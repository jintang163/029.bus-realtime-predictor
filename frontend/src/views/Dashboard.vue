<template>
  <div class="dashboard">
    <el-row :gutter="20" class="stats-row">
      <el-col :span="4">
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
      <el-col :span="4">
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
      <el-col :span="4">
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
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon accuracy-bg">
            <el-icon :size="28"><Aim /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ accuracyRate }}<span class="stat-unit">%</span></div>
            <div class="stat-label">
              预测准确率
              <el-tag v-if="accuracyChange > 0" type="success" size="small" effect="plain" class="change-tag">
                ↑ {{ accuracyChange }}%
              </el-tag>
              <el-tag v-else-if="accuracyChange < 0" type="danger" size="small" effect="plain" class="change-tag">
                ↓ {{ Math.abs(accuracyChange) }}%
              </el-tag>
              <el-tag v-else type="info" size="small" effect="plain" class="change-tag">--</el-tag>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" :class="amapHealthy ? 'api-ok-bg' : 'api-fail-bg'">
            <el-icon :size="28"><Connection /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">
              <el-tag :type="amapHealthy ? 'success' : 'danger'" size="small">
                {{ amapHealthy ? '正常' : '已降级' }}
              </el-tag>
            </div>
            <div class="stat-label">第三方路况API</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon baseline-bg">
            <el-icon :size="28"><Cpu /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value-row">
              <div class="stat-value-sm">{{ baselineSegments }}</div>
              <el-button
                type="primary"
                size="small"
                :loading="training"
                :disabled="training"
                @click="triggerTraining"
                class="train-btn">
                {{ training ? '训练中...' : '重训基线' }}
              </el-button>
            </div>
            <div class="stat-label">自学习基线覆盖路段</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="chart-row">
      <el-col :span="16">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <div class="chart-header">
              <span class="chart-title">预测偏差趋势（近7日准确率）</span>
              <el-tag type="info" size="small">总预测: {{ totalPredictions }} 次</el-tag>
            </div>
          </template>
          <div ref="trendChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <span class="chart-title">24小时准确率分布</span>
          </template>
          <div ref="hourlyChartRef" class="chart-container"></div>
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
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import L from 'leaflet'
import {
  Monitor, SwitchButton, Van, Aim, Timer, Connection, Cpu
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { vehicleApi, trafficApi } from '../api/index.js'
import { statusTag } from '../utils/format.js'

const onlineCount = ref(0)
const offlineCount = ref(0)
const totalCount = ref(0)
const accuracyRate = ref('--')
const accuracyChange = ref(0)
const totalPredictions = ref(0)
const amapHealthy = ref(true)
const baselineSegments = ref(0)
const training = ref(false)
const wsConnected = ref(false)

const trendChartRef = ref(null)
const hourlyChartRef = ref(null)
let trendChart = null
let hourlyChart = null

let map = null
const markers = new Map()
let ws = null
let wsReconnectTimer = null
let pollTimer = null
let deviationTimer = null
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

function initTrendChart() {
  if (!trendChartRef.value) return
  trendChart = echarts.init(trendChartRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['准确率', '平均偏差率'], top: 0 },
    grid: { left: 50, right: 50, top: 40, bottom: 30 },
    xAxis: {
      type: 'category',
      data: [],
      axisLabel: { fontSize: 11 }
    },
    yAxis: [
      {
        type: 'value',
        name: '准确率(%)',
        min: 0,
        max: 100,
        axisLabel: { formatter: '{value}%' }
      },
      {
        type: 'value',
        name: '偏差率',
        min: 0,
        axisLabel: { formatter: '{value}' }
      }
    ],
    series: [
      {
        name: '准确率',
        type: 'line',
        smooth: true,
        data: [],
        itemStyle: { color: '#67c23a' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(103,194,58,0.3)' },
            { offset: 1, color: 'rgba(103,194,58,0.05)' }
          ])
        }
      },
      {
        name: '平均偏差率',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: [],
        itemStyle: { color: '#e6a23c' }
      }
    ]
  })
  window.addEventListener('resize', () => trendChart && trendChart.resize())
}

function initHourlyChart() {
  if (!hourlyChartRef.value) return
  hourlyChart = echarts.init(hourlyChartRef.value)
  hourlyChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 45, right: 20, top: 20, bottom: 30 },
    xAxis: {
      type: 'category',
      data: Array.from({ length: 24 }, (_, i) => i + '时'),
      axisLabel: { fontSize: 10, interval: 2 }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { formatter: '{value}%', fontSize: 10 }
    },
    series: [
      {
        type: 'bar',
        data: [],
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#409eff' },
            { offset: 1, color: '#79bbff' }
          ]),
          borderRadius: [4, 4, 0, 0]
        }
      }
    ]
  })
  window.addEventListener('resize', () => hourlyChart && hourlyChart.resize())
}

async function loadDeviationData() {
  try {
    const overview = await trafficApi.getDeviationOverview()
    if (overview) {
      const trend = overview.dailyTrend || {}
      accuracyRate.value = trend.overallAccuracy ?? '--'
      accuracyChange.value = trend.accuracyChange ?? 0
      totalPredictions.value = trend.totalPredictions ?? 0

      if (trendChart && trend.dates && trend.dates.length > 0) {
        trendChart.setOption({
          xAxis: { data: trend.dates.map(d => d.substring(5)) },
          series: [
            { data: trend.accuracyRates || [] },
            { data: (trend.avgDeviationRates || []).map(v => +(v * 100).toFixed(1)) }
          ]
        })
      }

      if (hourlyChart && overview.hourlyAccuracy) {
        hourlyChart.setOption({
          series: [{ data: overview.hourlyAccuracy.accuracyRates || [] }]
        })
      }

      const baseline = overview.baselineStatus || {}
      amapHealthy.value = baseline.amapApiHealthy !== false
      baselineSegments.value = baseline.coveredSegments ?? 0
    }
  } catch (e) {
    console.warn('Load deviation data failed:', e)
  }
}

async function triggerTraining() {
  try {
    training.value = true
    const res = await trafficApi.triggerBaselineTraining()
    if (res && res.success) {
      ElMessage.success(`基线训练完成: 处理 ${res.processedSegments} 个路段, 耗时 ${res.durationSeconds}s`)
    } else {
      ElMessage.warning(res?.message || '训练启动失败')
    }
  } catch (e) {
    ElMessage.error('训练失败: ' + (e.message || '未知错误'))
  } finally {
    training.value = false
    loadDeviationData()
  }
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
  }
}

onMounted(async () => {
  initMap()
  connectWebSocket()
  pollOnlineCount()
  await nextTick()
  initTrendChart()
  initHourlyChart()
  loadDeviationData()

  pollTimer = setInterval(pollOnlineCount, 5000)
  deviationTimer = setInterval(loadDeviationData, 60000)
})

onUnmounted(() => {
  if (ws) ws.close()
  if (wsReconnectTimer) clearTimeout(wsReconnectTimer)
  if (pollTimer) clearInterval(pollTimer)
  if (deviationTimer) clearInterval(deviationTimer)
  if (map) map.remove()
  if (trendChart) trendChart.dispose()
  if (hourlyChart) hourlyChart.dispose()
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

.stats-row,
.chart-row {
  flex-shrink: 0;
}

.chart-row {
  margin-top: 0;
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

.accuracy-bg {
  background: linear-gradient(135deg, #722ed1, #b37feb);
}

.api-ok-bg {
  background: linear-gradient(135deg, #52c41a, #95de64);
}

.api-fail-bg {
  background: linear-gradient(135deg, #fa8c16, #ffc069);
}

.baseline-bg {
  background: linear-gradient(135deg, #13c2c2, #5cdbd3);
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

.stat-value-sm {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stat-unit {
  font-size: 16px;
  font-weight: 500;
  margin-left: 2px;
  color: #606266;
}

.stat-value-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.train-btn {
  flex-shrink: 0;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.change-tag {
  margin-left: 6px;
  vertical-align: middle;
}

.chart-card {
  height: 100%;
}

.chart-card :deep(.el-card__body) {
  padding: 12px 16px;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chart-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.chart-container {
  width: 100%;
  height: 260px;
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
