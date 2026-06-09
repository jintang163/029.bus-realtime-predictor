<template>
  <div class="traffic-heatmap-page">
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon smooth-bg">
            <el-icon :size="28"><Sunny /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ smoothCount }}</div>
            <div class="stat-label">畅通路段</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon slow-bg">
            <el-icon :size="28"><Cloudy /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ slowCount }}</div>
            <div class="stat-label">缓行路段</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon congested-bg">
            <el-icon :size="28"><Lightning /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ congestedCount }}</div>
            <div class="stat-label">拥堵路段</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon total-bg">
            <el-icon :size="28"><DataLine /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ avgSpeed }}</div>
            <div class="stat-label">平均速度(km/h)</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="main-row">
      <el-col :span="16">
        <el-card class="map-card">
          <template #header>
            <div class="map-header">
              <span class="map-title">路况热力图</span>
              <div>
                <el-tag :type="wsConnected ? 'success' : 'danger'" size="small">
                  {{ wsConnected ? '实时推送中' : '已断开' }}
                </el-tag>
                <el-button type="primary" size="small" style="margin-left: 10px" @click="refreshHeatmap">
                  手动刷新
                </el-button>
              </div>
            </div>
          </template>
          <div id="traffic-map" class="map-container"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="segment-list-card">
          <template #header>
            <div class="segment-header">
              <span>路段拥堵排行</span>
              <el-select v-model="selectedLine" placeholder="筛选线路" clearable size="small" style="width: 140px" @change="filterSegments">
                <el-option v-for="l in lineOptions" :key="l" :label="l" :value="l" />
              </el-select>
            </div>
          </template>
          <div class="segment-list">
            <div
              v-for="seg in displayedSegments"
              :key="seg.segmentId"
              class="segment-item"
              @click="showSegmentDetail(seg)"
            >
              <div class="segment-name">
                {{ seg.startStationName || seg.segmentId }} → {{ seg.endStationName || '' }}
              </div>
              <div class="segment-meta">
                <el-tag :type="congestionLevel(seg.congestionFactor).type" size="small">
                  {{ congestionLevel(seg.congestionFactor).text }}
                </el-tag>
                <span class="speed-text">
                  {{ seg.speedKmh ? seg.speedKmh.toFixed(1) : '--' }} km/h
                </span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="detailVisible" :title="detailTitle" width="800px" destroy-on-close>
      <div v-if="detailData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="路段ID">{{ detailData.segmentId }}</el-descriptions-item>
          <el-descriptions-item label="所属线路">{{ detailData.lineId }}</el-descriptions-item>
          <el-descriptions-item label="起点站">{{ detailData.startStationName }}</el-descriptions-item>
          <el-descriptions-item label="终点站">{{ detailData.endStationName }}</el-descriptions-item>
          <el-descriptions-item label="当前速度">
            {{ detailData.speedKmh ? detailData.speedKmh.toFixed(1) : '--' }} km/h
          </el-descriptions-item>
          <el-descriptions-item label="拥堵系数">
            <el-tag :type="congestionLevel(detailData.congestionFactor).type">
              {{ detailData.congestionFactor ? detailData.congestionFactor.toFixed(2) : '--' }}
              {{ congestionLevel(detailData.congestionFactor).text }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="路段长度">
            {{ detailData.length ? (detailData.length / 1000).toFixed(2) + ' km' : '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="自由流速度">
            {{ detailData.freeFlowSpeed ? (detailData.freeFlowSpeed * 3.6).toFixed(1) : '--' }} km/h
          </el-descriptions-item>
        </el-descriptions>

        <div style="margin-top: 20px">
          <h4 style="margin-bottom: 10px">历史速度趋势（近2小时）</h4>
          <div ref="historyChartRef" style="width: 100%; height: 300px"></div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import L from 'leaflet'
import * as echarts from 'echarts'
import { Sunny, Cloudy, Lightning, DataLine } from '@element-plus/icons-vue'
import { trafficApi, lineApi } from '../api/index.js'
import { congestionLevel } from '../utils/format.js'

const smoothCount = ref(0)
const slowCount = ref(0)
const congestedCount = ref(0)
const avgSpeed = ref('--')
const wsConnected = ref(false)
const selectedLine = ref('')
const lineOptions = ref([])
const segmentList = ref([])
const detailVisible = ref(false)
const detailData = ref(null)
const detailTitle = ref('')
const historyChartRef = ref(null)

let map = null
let heatmapLayer = null
let ws = null
let wsReconnectTimer = null
let historyChart = null
const segmentLines = new Map()
const segmentOverlays = new Map()

const displayedSegments = computed(() => {
  let list = [...segmentList.value]
  if (selectedLine.value) {
    list = list.filter(s => s.lineId === selectedLine.value)
  }
  list.sort((a, b) => (b.congestionFactor || 0) - (a.congestionFactor || 0))
  return list
})

function initMap() {
  map = L.map('traffic-map').setView([39.912, 116.407], 13)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors'
  }).addTo(map)
}

function congestionColor(factor) {
  if (factor == null) return '#909399'
  if (factor < 1.2) return '#67c23a'
  if (factor < 1.8) return '#e6a23c'
  if (factor < 3.0) return '#f56c6c'
  return '#c45656'
}

function congestionWeight(factor) {
  if (factor == null) return 3
  if (factor < 1.2) return 4
  if (factor < 1.8) return 5
  return 6
}

function updateMapSegments(segments) {
  for (const [, overlay] of segmentOverlays) {
    overlay.remove()
  }
  segmentOverlays.clear()

  for (const seg of segments) {
    if (seg.startLng == null || seg.startLat == null) continue
    const latlngs = [
      [seg.startLat, seg.startLng],
      [seg.endLat, seg.endLng]
    ]
    const polyline = L.polyline(latlngs, {
      color: congestionColor(seg.congestionFactor),
      weight: congestionWeight(seg.congestionFactor),
      opacity: 0.85
    }).addTo(map)

    polyline.bindPopup(`
      <div style="line-height:1.8">
        <strong>${seg.startStationName || seg.segmentId} → ${seg.endStationName || ''}</strong><br/>
        拥堵系数: ${seg.congestionFactor ? seg.congestionFactor.toFixed(2) : '--'}<br/>
        当前速度: ${seg.currentSpeed ? (seg.currentSpeed * 3.6).toFixed(1) : '--'} km/h<br/>
        状态: ${congestionLevel(seg.congestionFactor).text}
      </div>
    `)
    segmentOverlays.set(seg.segmentId, polyline)
  }
}

function updateStats(segments) {
  let smooth = 0, slow = 0, congested = 0, totalSpeed = 0, speedCount = 0
  for (const seg of segments) {
    const f = seg.congestionFactor
    if (f == null) continue
    if (f < 1.2) smooth++
    else if (f < 1.8) slow++
    else congested++
    if (seg.currentSpeed) {
      totalSpeed += seg.currentSpeed * 3.6
      speedCount++
    }
  }
  smoothCount.value = smooth
  slowCount.value = slow
  congestedCount.value = congested
  avgSpeed.value = speedCount > 0 ? (totalSpeed / speedCount).toFixed(1) : '--'
}

function updateSegmentList(segments) {
  segmentList.value = segments
  const lines = new Set()
  for (const seg of segments) {
    if (seg.lineId) lines.add(seg.lineId)
  }
  lineOptions.value = Array.from(lines)
}

function filterSegments() {}

function connectWebSocket() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/ws/traffic`
  ws = new WebSocket(wsUrl)

  ws.onopen = () => { wsConnected.value = true }
  ws.onmessage = (event) => {
    try {
      const segments = JSON.parse(event.data)
      if (Array.isArray(segments)) {
        updateMapSegments(segments)
        updateStats(segments)
        updateSegmentList(segments)
      }
    } catch (e) {
      console.error('Failed to parse traffic WS message:', e)
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
  wsReconnectTimer = setTimeout(() => connectWebSocket(), 5000)
}

async function refreshHeatmap() {
  try {
    const segments = await trafficApi.getHeatmap()
    if (Array.isArray(segments)) {
      updateMapSegments(segments)
      updateStats(segments)
      updateSegmentList(segments)
    }
  } catch {
    // silently fail
  }
}

async function showSegmentDetail(seg) {
  detailVisible.value = true
  detailTitle.value = `${seg.startStationName || seg.segmentId} → ${seg.endStationName || ''}`
  try {
    const data = await trafficApi.getSegmentComparison(seg.segmentId)
    detailData.value = { ...seg, ...data }
    await nextTick()
    renderHistoryChart(data.historyData || [])
  } catch {
    detailData.value = seg
  }
}

function renderHistoryChart(historyData) {
  if (!historyChartRef.value) return
  if (historyChart) historyChart.dispose()
  historyChart = echarts.init(historyChartRef.value)

  const times = []
  const speeds = []
  const congestions = []

  for (const item of historyData) {
    const t = item.record_time || item.recordTime
    times.push(t ? t.substring(11, 16) : '')
    speeds.push(item.speed != null ? +(item.speed * 3.6).toFixed(1) : null)
    congestions.push(item.congestion_factor != null ? +item.congestion_factor.toFixed(2) : null)
  }

  historyChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['速度(km/h)', '拥堵系数'] },
    grid: { left: 60, right: 60, bottom: 30, top: 40 },
    xAxis: { type: 'category', data: times },
    yAxis: [
      { type: 'value', name: '速度(km/h)', position: 'left' },
      { type: 'value', name: '拥堵系数', position: 'right', min: 1, max: 5 }
    ],
    series: [
      {
        name: '速度(km/h)',
        type: 'line',
        data: speeds,
        smooth: true,
        itemStyle: { color: '#409eff' },
        areaStyle: { color: 'rgba(64,158,255,0.15)' }
      },
      {
        name: '拥堵系数',
        type: 'line',
        yAxisIndex: 1,
        data: congestions,
        smooth: true,
        itemStyle: { color: '#f56c6c' },
        lineStyle: { type: 'dashed' }
      }
    ]
  })
}

onMounted(async () => {
  initMap()
  connectWebSocket()
  await refreshHeatmap()
})

onUnmounted(() => {
  if (ws) ws.close()
  if (wsReconnectTimer) clearTimeout(wsReconnectTimer)
  if (map) map.remove()
  if (historyChart) historyChart.dispose()
  segmentOverlays.clear()
})
</script>

<style scoped>
.traffic-heatmap-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
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
  padding: 16px;
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.smooth-bg { background: linear-gradient(135deg, #67c23a, #95d475); }
.slow-bg { background: linear-gradient(135deg, #e6a23c, #eebe77); }
.congested-bg { background: linear-gradient(135deg, #f56c6c, #fab6b6); }
.total-bg { background: linear-gradient(135deg, #409eff, #79bbff); }

.stat-info { flex: 1; min-width: 0; }
.stat-value { font-size: 24px; font-weight: 700; color: #303133; line-height: 1.2; }
.stat-label { font-size: 12px; color: #909399; margin-top: 4px; }

.main-row {
  flex: 1;
  min-height: 0;
}

.map-card {
  height: 100%;
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

.map-title { font-size: 16px; font-weight: 600; color: #303133; }

.map-container {
  width: 100%;
  height: 100%;
  min-height: 480px;
}

.segment-list-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.segment-list-card :deep(.el-card__body) {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}

.segment-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.segment-list {
  padding: 8px 0;
}

.segment-item {
  padding: 10px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  transition: background-color 0.2s;
}

.segment-item:hover {
  background-color: #f5f7fa;
}

.segment-name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
}

.segment-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.speed-text {
  font-size: 12px;
  color: #909399;
}
</style>
