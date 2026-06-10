<template>
  <div class="stopboard-page">
    <div class="search-bar">
      <el-input
        v-model="stationId"
        placeholder="请输入站点ID"
        style="width: 240px"
        clearable
        @keyup.enter="handleQuery"
      />
      <el-button type="primary" @click="handleQuery" :loading="loading">查询</el-button>
      <el-button
        v-if="wsConnected"
        type="danger"
        size="small"
        @click="disconnectWs"
      >断开实时</el-button>
      <el-tag v-if="wsConnected" type="success" size="small" style="margin-left: 8px">
        实时推送中
      </el-tag>
    </div>

    <div class="content-row" v-if="stationInfo">
      <el-card class="info-card" shadow="hover">
        <template #header>
          <span class="card-title">站点信息</span>
        </template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="站点名称">{{ stationInfo.stationName }}</el-descriptions-item>
          <el-descriptions-item label="站点编号">{{ stationInfo.stationCode }}</el-descriptions-item>
          <el-descriptions-item label="经度">{{ stationInfo.longitude }}</el-descriptions-item>
          <el-descriptions-item label="纬度">{{ stationInfo.latitude }}</el-descriptions-item>
          <el-descriptions-item label="区域">{{ stationInfo.district }}</el-descriptions-item>
          <el-descriptions-item label="街道">{{ stationInfo.street }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="lines-card" shadow="hover">
        <template #header>
          <span class="card-title">途经线路</span>
        </template>
        <el-table :data="stationLines" stripe border style="width: 100%">
          <el-table-column prop="lineName" label="线路名称" min-width="120" />
          <el-table-column prop="direction" label="方向" width="80" align="center">
            <template #default="{ row }">
              {{ row.direction === 0 ? '上行' : '下行' }}
            </template>
          </el-table-column>
          <el-table-column prop="stationOrder" label="站序" width="70" align="center" />
          <el-table-column prop="firstBusTime" label="首班车" width="90" align="center">
            <template #default="{ row }">
              {{ formatTime(row.firstBusTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="lastBusTime" label="末班车" width="90" align="center">
            <template #default="{ row }">
              {{ formatTime(row.lastBusTime) }}
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <el-card v-if="stationInfo" class="arrival-card" shadow="hover">
      <template #header>
        <div class="arrival-header">
          <span class="card-title">到站倒计时</span>
          <el-switch v-model="ttsEnabled" active-text="语音播报" inactive-text="静音" />
        </div>
      </template>
      <div v-if="arrivalPredictions.length === 0" class="no-data">
        暂无到站预测数据
      </div>
      <div v-else class="arrival-grid">
        <div
          v-for="item in arrivalPredictions"
          :key="item.vehicleId + item.stationId"
          class="arrival-item"
          :class="getUrgencyClass(item.estimatedSeconds)"
        >
          <div class="arrival-route">{{ item.vehicleId }}</div>
          <div class="arrival-countdown">
            <span class="countdown-minutes">{{ formatCountdown(item.estimatedSeconds) }}</span>
          </div>
          <div class="arrival-details">
            <span class="detail-distance">{{ formatDistance(item.distanceToStation) }}</span>
            <span class="detail-speed">{{ (item.currentSpeed * 3.6).toFixed(0) }} km/h</span>
          </div>
          <div class="arrival-station">{{ item.stationName }}</div>
        </div>
      </div>
    </el-card>

    <el-card v-if="stationInfo" class="map-card" shadow="hover">
      <template #header>
        <span class="card-title">站点位置</span>
      </template>
      <div id="station-map" class="map-container"></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, nextTick, onUnmounted, watch } from 'vue'
import { stopboardApi, vehicleApi } from '../api/index.js'
import { formatTime } from '../utils/format.js'
import L from 'leaflet'

const stationId = ref('')
const loading = ref(false)
const stationInfo = ref(null)
const stationLines = ref([])
const arrivalPredictions = ref([])
const wsConnected = ref(false)
const ttsEnabled = ref(false)

let mapInstance = null
let markerInstance = null
let ws = null
let wsReconnectTimer = null
let countdownTimer = null
let lastSpokenMinute = null

async function handleQuery() {
  const id = stationId.value?.trim()
  if (!id) return

  loading.value = true
  stationInfo.value = null
  stationLines.value = []
  arrivalPredictions.value = []

  try {
    const [info, lines] = await Promise.all([
      stopboardApi.getStationInfo(id),
      stopboardApi.getStationLines(id)
    ])
    stationInfo.value = info
    stationLines.value = lines || []

    await nextTick()
    initMap()

    connectArrivalWs(id)
    fetchInitialPredictions(id)
    startCountdownTick()
  } catch {
    stationInfo.value = null
    stationLines.value = []
  } finally {
    loading.value = false
  }
}

async function fetchInitialPredictions(id) {
  try {
    const data = await vehicleApi.getStationPrediction(id)
    if (Array.isArray(data)) {
      arrivalPredictions.value = data.map(p => ({
        ...p,
        _updatedAt: Date.now()
      }))
    }
  } catch {
    // ignore
  }
}

function connectArrivalWs(id) {
  if (ws) {
    ws.close()
  }

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/ws/arrival`

  ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    wsConnected.value = true
    ws.send(JSON.stringify({
      action: 'subscribe',
      stationId: id
    }))
  }

  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      if (msg.type === 'arrival' && Array.isArray(msg.predictions)) {
        arrivalPredictions.value = msg.predictions.map(p => ({
          ...p,
          _updatedAt: Date.now()
        }))
        checkTts(msg.predictions)
      }
    } catch (e) {
      console.error('Failed to parse arrival WS message:', e)
    }
  }

  ws.onclose = () => {
    wsConnected.value = false
    scheduleReconnect(id)
  }

  ws.onerror = () => {
    wsConnected.value = false
    ws.close()
  }
}

function scheduleReconnect(id) {
  if (wsReconnectTimer) clearTimeout(wsReconnectTimer)
  wsReconnectTimer = setTimeout(() => {
    if (stationInfo.value) {
      connectArrivalWs(id)
    }
  }, 5000)
}

function disconnectWs() {
  if (ws) {
    ws.close()
    ws = null
  }
  wsConnected.value = false
  if (wsReconnectTimer) {
    clearTimeout(wsReconnectTimer)
    wsReconnectTimer = null
  }
}

function startCountdownTick() {
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    const now = Date.now()
    arrivalPredictions.value = arrivalPredictions.value.map(p => {
      const elapsed = (now - (p._updatedAt || now)) / 1000
      return {
        ...p,
        estimatedSeconds: Math.max(0, p.estimatedSeconds - Math.round(elapsed))
      }
    })
  }, 1000)
}

function formatCountdown(seconds) {
  if (seconds == null || seconds <= 0) return '即将到站'
  const minutes = Math.floor(seconds / 60)
  const secs = seconds % 60
  if (minutes === 0) return `${secs}秒`
  return `${minutes}分${secs < 10 ? '0' : ''}${secs}秒`
}

function formatDistance(meters) {
  if (meters == null) return '--'
  if (meters < 1000) return `${meters.toFixed(0)}m`
  return `${(meters / 1000).toFixed(1)}km`
}

function getUrgencyClass(seconds) {
  if (seconds == null) return ''
  if (seconds <= 60) return 'urgency-arriving'
  if (seconds <= 180) return 'urgency-soon'
  return 'urgency-normal'
}

function checkTts(predictions) {
  if (!ttsEnabled.value) return
  if (!window.speechSynthesis) return

  const nearest = predictions
    .filter(p => p.estimatedSeconds > 0)
    .sort((a, b) => a.estimatedSeconds - b.estimatedSeconds)[0]

  if (!nearest) return

  const minutes = Math.ceil(nearest.estimatedSeconds / 60)
  if (minutes !== lastSpokenMinute && minutes <= 5) {
    lastSpokenMinute = minutes
    const text = minutes <= 1
      ? `${nearest.vehicleId} 即将到站 ${nearest.stationName}`
      : `${nearest.vehicleId} 预计 ${minutes} 分钟后到达 ${nearest.stationName}`
    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = 'zh-CN'
    utterance.rate = 1.0
    window.speechSynthesis.speak(utterance)
  }
}

watch(ttsEnabled, (val) => {
  if (val && window.speechSynthesis) {
    const utterance = new SpeechSynthesisUtterance('语音播报已开启')
    utterance.lang = 'zh-CN'
    window.speechSynthesis.speak(utterance)
  }
})

function initMap() {
  if (!stationInfo.value) return

  const { longitude, latitude } = stationInfo.value
  const center = [latitude, longitude]

  if (!mapInstance) {
    mapInstance = L.map('station-map', {
      center,
      zoom: 16,
      zoomControl: true
    })
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors'
    }).addTo(mapInstance)
  } else {
    mapInstance.setView(center, 16)
  }

  if (markerInstance) {
    markerInstance.setLatLng(center)
  } else {
    markerInstance = L.marker(center).addTo(mapInstance)
  }

  setTimeout(() => {
    mapInstance.invalidateSize()
  }, 200)
}

onUnmounted(() => {
  disconnectWs()
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  if (mapInstance) {
    mapInstance.remove()
    mapInstance = null
    markerInstance = null
  }
})
</script>

<style scoped>
.stopboard-page {
  max-width: 1200px;
  margin: 0 auto;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.content-row {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
}

.info-card {
  flex: 0 0 340px;
}

.lines-card {
  flex: 1;
  min-width: 0;
}

.arrival-card {
  margin-bottom: 20px;
}

.arrival-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.no-data {
  text-align: center;
  color: #909399;
  padding: 40px 0;
  font-size: 14px;
}

.arrival-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.arrival-item {
  padding: 16px;
  border-radius: 8px;
  border: 2px solid #e4e7ed;
  background: #f5f7fa;
  transition: all 0.3s;
}

.arrival-item.urgency-arriving {
  border-color: #f56c6c;
  background: linear-gradient(135deg, #fef0f0, #fde2e2);
  animation: pulse-border 1.5s ease-in-out infinite;
}

.arrival-item.urgency-soon {
  border-color: #e6a23c;
  background: linear-gradient(135deg, #fdf6ec, #faecd8);
}

.arrival-item.urgency-normal {
  border-color: #67c23a;
  background: linear-gradient(135deg, #f0f9eb, #e1f3d8);
}

@keyframes pulse-border {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.3); }
  50% { box-shadow: 0 0 0 6px rgba(245, 108, 108, 0.1); }
}

.arrival-route {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
  font-weight: 500;
}

.arrival-countdown {
  margin-bottom: 8px;
}

.countdown-minutes {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.urgency-arriving .countdown-minutes {
  color: #f56c6c;
}

.urgency-soon .countdown-minutes {
  color: #e6a23c;
}

.urgency-normal .countdown-minutes {
  color: #67c23a;
}

.arrival-details {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.arrival-station {
  font-size: 12px;
  color: #909399;
}

.map-card {
  margin-bottom: 20px;
}

.map-container {
  height: 300px;
  width: 100%;
  border-radius: 4px;
}
</style>
