<template>
  <div class="dashboard">
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon online-bg">
            <el-icon :size="28"><Monitor /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ onlineCount }}</div>
            <div class="stat-label">在线车辆</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon offline-bg">
            <el-icon :size="28"><SwitchButton /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ offlineCount }}</div>
            <div class="stat-label">离线车辆</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon total-bg">
            <el-icon :size="28"><Van /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ totalCount }}</div>
            <div class="stat-label">车辆总数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon rate-bg">
            <el-icon :size="28"><Timer /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ refreshRate }}</div>
            <div class="stat-label">数据刷新频率</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="map-card">
      <template #header>
        <div class="map-header">
          <span class="map-title">车辆实时位置</span>
          <el-tag :type="wsConnected ? 'success' : 'danger'" size="small">
            {{ wsConnected ? 'WebSocket 已连接' : 'WebSocket 已断开' }}
          </el-tag>
        </div>
      </template>
      <div id="map" class="map-container"></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import L from 'leaflet'
import { Monitor, SwitchButton, Van, Timer } from '@element-plus/icons-vue'
import { vehicleApi } from '../api/index.js'
import { statusTag, formatDistance } from '../utils/format.js'

const onlineCount = ref(0)
const offlineCount = ref(0)
const totalCount = ref(0)
const refreshRate = ref('--')
const wsConnected = ref(false)

let map = null
const markers = new Map()
let ws = null
let wsReconnectTimer = null
let pollTimer = null
let lastMessageTime = null

function initMap() {
  map = L.map('map').setView([39.904, 116.407], 13)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors'
  }).addTo(map)
}

function buildPopupContent(v) {
  const tag = statusTag(v.status)
  return `<div style="line-height:1.8">
    <strong style="font-size:14px">${v.vehicleId}</strong><br/>
    速度: ${(v.speed * 3.6).toFixed(1)} km/h<br/>
    状态: ${tag.text}<br/>
    方向: ${v.direction ?? '--'}°<br/>
    经度: ${v.longitude ?? '--'}<br/>
    纬度: ${v.latitude ?? '--'}
  </div>`
}

function updateMarkers(vehicles) {
  const currentIds = new Set()

  vehicles.forEach(v => {
    currentIds.add(v.vehicleId)
    if (v.longitude == null || v.latitude == null) return

    const latlng = [v.latitude, v.longitude]

    if (markers.has(v.vehicleId)) {
      const marker = markers.get(v.vehicleId)
      marker.setLatLng(latlng)
      marker.setPopupContent(buildPopupContent(v))
    } else {
      const marker = L.circleMarker(latlng, {
        radius: 6,
        fillColor: '#409eff',
        color: '#fff',
        weight: 1,
        fillOpacity: 0.8
      }).addTo(map)
      marker.bindPopup(buildPopupContent(v))
      markers.set(v.vehicleId, marker)
    }
  })

  for (const [id, marker] of markers) {
    if (!currentIds.has(id)) {
      marker.remove()
      markers.delete(id)
    }
  }
}

function updateStats(vehicles) {
  const online = vehicles.filter(v => v.status === 1).length
  const offline = vehicles.filter(v => v.status === 0).length
  onlineCount.value = online
  offlineCount.value = offline
  totalCount.value = vehicles.length
}

function connectWebSocket() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/ws/vehicle`

  ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    wsConnected.value = true
  }

  ws.onmessage = (event) => {
    try {
      const vehicles = JSON.parse(event.data)
      if (Array.isArray(vehicles)) {
        updateMarkers(vehicles)
        updateStats(vehicles)

        const now = Date.now()
        if (lastMessageTime) {
          const interval = now - lastMessageTime
          refreshRate.value = (interval / 1000).toFixed(1) + 's'
        }
        lastMessageTime = now
      }
    } catch (e) {
      console.error('Failed to parse WS message:', e)
    }
  }

  ws.onclose = () => {
    wsConnected.value = false
    scheduleReconnect()
  }

  ws.onerror = () => {
    wsConnected.value = false
    ws.close()
  }
}

function scheduleReconnect() {
  if (wsReconnectTimer) clearTimeout(wsReconnectTimer)
  wsReconnectTimer = setTimeout(() => {
    connectWebSocket()
  }, 3000)
}

async function pollOnlineCount() {
  try {
    const res = await vehicleApi.getOnlineCount()
    if (typeof res === 'number') {
      onlineCount.value = res
    } else if (res && typeof res === 'object') {
      onlineCount.value = res.online ?? res.onlineCount ?? 0
      offlineCount.value = res.offline ?? res.offlineCount ?? 0
      totalCount.value = res.total ?? (onlineCount.value + offlineCount.value)
    }
  } catch {
    // fallback poll silently fails
  }
}

onMounted(() => {
  initMap()
  connectWebSocket()
  pollOnlineCount()
  pollTimer = setInterval(pollOnlineCount, 5000)
})

onUnmounted(() => {
  if (ws) ws.close()
  if (wsReconnectTimer) clearTimeout(wsReconnectTimer)
  if (pollTimer) clearInterval(pollTimer)
  if (map) map.remove()
  markers.clear()
})
</script>

<style scoped>
.dashboard {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.stats-row {
  flex-shrink: 0;
}

.stat-card {
  height: 100%;
}

.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.online-bg {
  background: linear-gradient(135deg, #67c23a, #95d475);
}

.offline-bg {
  background: linear-gradient(135deg, #f56c6c, #fab6b6);
}

.total-bg {
  background: linear-gradient(135deg, #409eff, #79bbff);
}

.rate-bg {
  background: linear-gradient(135deg, #e6a23c, #eebe77);
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.map-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.map-card :deep(.el-card__body) {
  flex: 1;
  padding: 0;
  min-height: 0;
}

.map-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.map-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.map-container {
  width: 100%;
  height: 100%;
  min-height: 500px;
}
</style>
