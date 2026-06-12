<template>
  <div class="operation-dashboard">
    <div class="dashboard-header">
      <div class="header-left">
        <h1 class="dashboard-title">
          <el-icon :size="28" color="#409eff"><Monitor /></el-icon>
          公交实时预测运维监控大屏
        </h1>
        <div class="header-time">
          <el-icon :size="16"><Clock /></el-icon>
          {{ currentTime }}
        </div>
      </div>
      <div class="header-right">
        <el-tag :type="systemStatus.type" size="large" effect="dark">
          <el-icon :size="14"><CircleCheck /></el-icon>
          系统状态: {{ systemStatus.text }}
        </el-tag>
      </div>
    </div>

    <el-row :gutter="12" class="stats-row">
      <el-col :span="4">
        <div class="stat-card gradient-blue">
          <div class="stat-icon-wrap">
            <el-icon :size="32"><Van /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ businessMetrics.onlineVehicleCount || 0 }}</div>
            <div class="stat-label">在线车辆</div>
          </div>
          <div class="stat-trend up">
            <span class="trend-value">{{ businessMetrics.onlineRate?.toFixed(1) || 0 }}%</span>
          </div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card gradient-green">
          <div class="stat-icon-wrap">
            <el-icon :size="32"><Connection /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ businessMetrics.smoothSegmentCount || 0 }}</div>
            <div class="stat-label">畅通路段</div>
          </div>
          <div class="stat-trend up">
            <span class="trend-value">畅通率 {{ smoothRate }}%</span>
          </div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card gradient-orange">
          <div class="stat-icon-wrap">
            <el-icon :size="32"><Warning /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ businessMetrics.slowSegmentCount || 0 }}</div>
            <div class="stat-label">缓行路段</div>
          </div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card gradient-red">
          <div class="stat-icon-wrap">
            <el-icon :size="32"><WarningFilled /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ businessMetrics.congestedSegmentCount || 0 }}</div>
            <div class="stat-label">拥堵路段</div>
          </div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card gradient-purple">
          <div class="stat-icon-wrap">
            <el-icon :size="32"><Aim /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ accuracyRate }}<span class="stat-unit">%</span></div>
            <div class="stat-label">预测准确率</div>
          </div>
          <div class="stat-trend" :class="accuracyChange >= 0 ? 'up' : 'down'">
            <el-icon v-if="accuracyChange >= 0"><Top /></el-icon>
            <el-icon v-else><Bottom /></el-icon>
            <span class="trend-value">{{ Math.abs(accuracyChange) }}%</span>
          </div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card gradient-cyan">
          <div class="stat-icon-wrap">
            <el-icon :size="32"><Odometer /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ apiMetrics.qps || 0 }}</div>
            <div class="stat-label">API QPS</div>
          </div>
          <div class="stat-trend">
            <span class="trend-value">平均 {{ apiMetrics.avgResponseTimeMs || 0 }}ms</span>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="main-row">
      <el-col :span="18">
        <div class="map-panel">
          <div class="panel-header">
            <div class="panel-title">
              <el-icon :size="18"><MapLocation /></el-icon>
              实时车辆监控 & 路况热力图
            </div>
            <div class="panel-actions">
              <el-tag :type="vehicleWsConnected ? 'success' : 'danger'" size="small" effect="dark">
                车辆 {{ vehicleWsConnected ? '实时' : '断开' }}
              </el-tag>
              <el-tag :type="trafficWsConnected ? 'success' : 'danger'" size="small" effect="dark" style="margin-left: 8px">
                路况 {{ trafficWsConnected ? '实时' : '断开' }}
              </el-tag>
            </div>
          </div>
          <div id="ops-map" class="map-container"></div>
          <div class="map-legend">
            <div class="legend-item">
              <span class="legend-dot smooth"></span>
              <span>畅通</span>
            </div>
            <div class="legend-item">
              <span class="legend-dot slow"></span>
              <span>缓行</span>
            </div>
            <div class="legend-item">
              <span class="legend-dot congested"></span>
              <span>拥堵</span>
            </div>
            <div class="legend-item">
              <span class="legend-dot vehicle"></span>
              <span>车辆</span>
            </div>
            <div class="legend-item">
              <span class="legend-dot alert"></span>
              <span>告警线路</span>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :span="6">
        <div class="alert-panel">
          <div class="panel-header">
            <div class="panel-title">
              <el-icon :size="18" color="#f56c6c"><Bell /></el-icon>
              实时告警
              <el-badge :value="activeAlertCount" class="alert-badge" :hidden="activeAlertCount === 0" />
            </div>
            <el-tag :type="alertWsConnected ? 'success' : 'danger'" size="small" effect="dark">
              {{ alertWsConnected ? '推送中' : '已断开' }}
            </el-tag>
          </div>
          <div class="alert-stats">
            <div class="alert-stat critical">
              <span class="alert-stat-value">{{ alertStats.criticalCount || 0 }}</span>
              <span class="alert-stat-label">严重</span>
            </div>
            <div class="alert-stat warning">
              <span class="alert-stat-value">{{ alertStats.warningCount || 0 }}</span>
              <span class="alert-stat-label">警告</span>
            </div>
            <div class="alert-stat info">
              <span class="alert-stat-value">{{ alertStats.infoCount || 0 }}</span>
              <span class="alert-stat-label">提示</span>
            </div>
          </div>
          <div class="alert-list" ref="alertListRef">
            <div
              v-for="alert in activeAlerts"
              :key="alert.id"
              class="alert-item"
              :class="'alert-' + alert.alertLevel.toLowerCase()"
              @click="handleAlertClick(alert)"
            >
              <div class="alert-header">
                <el-tag :type="getAlertType(alert.alertLevel)" size="small" effect="dark">
                  {{ alert.alertLevel }}
                </el-tag>
                <span class="alert-time">{{ formatTime(alert.createTime) }}</span>
              </div>
              <div class="alert-title">{{ alert.ruleName }}</div>
              <div class="alert-target">{{ alert.targetName }}</div>
              <div class="alert-message">{{ alert.message }}</div>
            </div>
            <div v-if="activeAlerts.length === 0" class="no-alerts">
              <el-icon :size="48" color="#909399"><CircleCheck /></el-icon>
              <div>暂无告警</div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="chart-row">
      <el-col :span="8">
        <div class="chart-panel">
          <div class="panel-header">
            <div class="panel-title">
              <el-icon :size="18"><DataLine /></el-icon>
              预测准确率（最近1小时偏差分布）
            </div>
          </div>
          <div ref="deviationChartRef" class="chart-container"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-panel">
          <div class="panel-header">
            <div class="panel-title">
              <el-icon :size="18"><Cpu /></el-icon>
              系统资源使用
            </div>
          </div>
          <div ref="systemChartRef" class="chart-container"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-panel">
          <div class="panel-header">
            <div class="panel-title">
              <el-icon :size="18"><TrendCharts /></el-icon>
              API响应耗时分布
            </div>
          </div>
          <div ref="apiChartRef" class="chart-container"></div>
        </div>
      </el-col>
    </el-row>

    <el-dialog
      v-model="trajectoryDialogVisible"
      :title="`车辆轨迹回放 - ${selectedVehicleId}`"
      width="1000px"
      class="trajectory-dialog"
      destroy-on-close
    >
      <div class="trajectory-replay">
        <div class="replay-controls">
          <el-button-group>
            <el-button @click="startReplay" :disabled="isReplaying">
              <el-icon><VideoPlay /></el-icon> 播放
            </el-button>
            <el-button @click="pauseReplay" :disabled="!isReplaying">
              <el-icon><VideoPause /></el-icon> 暂停
            </el-button>
            <el-button @click="stopReplay">
              <el-icon><Refresh /></el-icon> 重置
            </el-button>
          </el-button-group>
          <el-slider
            v-model="replayProgress"
            :min="0"
            :max="replayData.path?.length || 100"
            @change="handleProgressChange"
            style="flex: 1; margin: 0 20px"
          />
          <el-select v-model="replaySpeed" size="small" style="width: 100px">
            <el-option label="0.5x" :value="0.5" />
            <el-option label="1x" :value="1" />
            <el-option label="2x" :value="2" />
            <el-option label="4x" :value="4" />
          </el-select>
        </div>
        <div class="replay-info">
          <el-tag>当前速度: {{ currentReplaySpeed }} km/h</el-tag>
          <el-tag>当前时间: {{ currentReplayTime }}</el-tag>
          <el-tag>进度: {{ replayProgress }}/{{ replayData.path?.length || 0 }}</el-tag>
        </div>
        <div id="replay-map" class="replay-map"></div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import L from 'leaflet'
import {
  Monitor, Clock, CircleCheck, Van, Connection, Warning, WarningFilled,
  Aim, Odometer, Top, Bottom, MapLocation, Bell, DataLine, Cpu,
  TrendCharts, VideoPlay, VideoPause, Refresh
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  vehicleApi, trafficApi, alertApi, monitorApi, trajectoryApi
} from '../api/index.js'

const currentTime = ref('')
const vehicleWsConnected = ref(false)
const trafficWsConnected = ref(false)
const alertWsConnected = ref(false)

const businessMetrics = ref({})
const apiMetrics = ref({})
const systemMetrics = ref({})
const alertStats = ref({})
const activeAlerts = ref([])
const accuracyRate = ref('--')
const accuracyChange = ref(0)

const map = ref(null)
const vehicleMarkers = new Map()
const segmentLines = new Map()
const alertLines = new Set()

let vehicleWs = null
let trafficWs = null
let alertWs = null
let wsReconnectTimers = {}

const deviationChartRef = ref(null)
const systemChartRef = ref(null)
const apiChartRef = ref(null)
let deviationChart = null
let systemChart = null
let apiChart = null

let timeTimer = null
let monitorTimer = null
let deviationTimer = null

const smoothRate = computed(() => {
  const total = (businessMetrics.value.segmentCount || 1)
  const smooth = businessMetrics.value.smoothSegmentCount || 0
  return ((smooth / total) * 100).toFixed(1)
})

const activeAlertCount = computed(() => activeAlerts.value.length)

const systemStatus = computed(() => {
  const cpu = systemMetrics.value.cpu?.processCpuLoad || 0
  const mem = systemMetrics.value.memory?.heapUsedPercent || 0
  if (cpu > 80 || mem > 80) return { type: 'danger', text: '高负载' }
  if (cpu > 60 || mem > 60) return { type: 'warning', text: '中负载' }
  return { type: 'success', text: '正常' }
})

const trajectoryDialogVisible = ref(false)
const selectedVehicleId = ref('')
const replayData = ref({})
const isReplaying = ref(false)
const replayProgress = ref(0)
const replaySpeed = ref(1)
const currentReplaySpeed = ref(0)
const currentReplayTime = ref('')
let replayMap = null
let replayMarker = null
let replayPathLine = null
let replayTimer = null

function updateCurrentTime() {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
}

function initMap() {
  map.value = L.map('ops-map').setView([39.912, 116.407], 13)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors'
  }).addTo(map.value)
}

function getCongestionColor(factor) {
  if (factor == null) return '#909399'
  if (factor < 1.2) return '#67c23a'
  if (factor < 1.8) return '#e6a23c'
  if (factor < 3.0) return '#f56c6c'
  return '#c45656'
}

function getCongestionWeight(factor) {
  if (factor == null) return 4
  if (factor < 1.2) return 5
  if (factor < 1.8) return 7
  return 9
}

function isAlertSegment(segmentId) {
  return alertLines.has(segmentId)
}

function updateMapSegments(segments) {
  for (const [, line] of segmentLines) {
    line.remove()
  }
  segmentLines.clear()

  for (const seg of segments) {
    if (seg.startLng == null || seg.startLat == null) continue

    const latlngs = [[seg.startLat, seg.startLng], [seg.endLat, seg.endLng]]
    const isAlert = isAlertSegment(seg.segmentId)
    const color = isAlert ? '#ff00ff' : getCongestionColor(seg.congestionFactor)
    const weight = isAlert ? 12 : getCongestionWeight(seg.congestionFactor)
    const dashArray = isAlert ? '10, 10' : null

    const polyline = L.polyline(latlngs, {
      color, weight, opacity: isAlert ? 0.9 : 0.8, dashArray
    }).addTo(map.value)

    polyline.bindPopup(`
      <div style="line-height:1.8">
        <strong>${seg.startStationName || seg.segmentId} → ${seg.endStationName || ''}</strong><br/>
        线路: ${seg.lineId || '--'}<br/>
        拥堵系数: ${seg.congestionFactor ? seg.congestionFactor.toFixed(2) : '--'}<br/>
        当前速度: ${seg.currentSpeed ? (seg.currentSpeed * 3.6).toFixed(1) : '--'} km/h<br/>
        ${isAlert ? '<span style="color:#f56c6c;font-weight:bold">⚠️ 告警线路</span>' : ''}
      </div>
    `)
    segmentLines.set(seg.segmentId, polyline)
  }
}

function buildVehiclePopup(v) {
  return `<div style="line-height:1.8; min-width:180px">
    <strong style="font-size:15px">${v.vehicleId}</strong><br/>
    速度: ${v.speed != null ? (v.speed * 3.6).toFixed(1) : '--'} km/h<br/>
    方向: ${v.direction != null ? v.direction + '°' : '--'}<br/>
    经度: ${v.longitude != null ? v.longitude.toFixed(6) : '--'}<br/>
    纬度: ${v.latitude != null ? v.latitude.toFixed(6) : '--'}<br/>
    GPS时间: ${v.gpsTime ? new Date(v.gpsTime).toLocaleTimeString() : '--'}<br/>
    <button onclick="window.dispatchEvent(new CustomEvent('showTrajectory', {detail:'${v.vehicleId}'}))" 
            style="margin-top:8px; padding:4px 12px; background:#409eff; color:#fff; border:none; border-radius:4px; cursor:pointer">
      轨迹回放
    </button>
  </div>`
}

function updateVehicleMarkers(vehicles) {
  const currentIds = new Set()

  vehicles.forEach(v => {
    currentIds.add(v.vehicleId)
    if (v.longitude == null || v.latitude == null) return

    const latlng = [v.latitude, v.longitude]

    if (vehicleMarkers.has(v.vehicleId)) {
      const marker = vehicleMarkers.get(v.vehicleId)
      marker.setLatLng(latlng)
      marker.setPopupContent(buildVehiclePopup(v))
      marker.setRotationAngle(v.direction || 0)
    } else {
      const busIcon = L.divIcon({
        className: 'vehicle-marker',
        html: `<div style="
          width:24px; height:24px; background:#409eff; border-radius:50%;
          border:2px solid #fff; box-shadow:0 2px 6px rgba(0,0,0,0.3);
          display:flex; align-items:center; justify-content:center;
          transform-origin: center center;
        ">
          <span style="color:#fff; font-size:12px; font-weight:bold">🚌</span>
        </div>`,
        iconSize: [24, 24],
        iconAnchor: [12, 12]
      })

      const marker = L.marker(latlng, { icon: busIcon, rotationAngle: v.direction || 0 })
        .addTo(map.value)
        .bindPopup(buildVehiclePopup(v))

      marker.on('click', () => {
        showTrajectoryReplay(v.vehicleId)
      })

      vehicleMarkers.set(v.vehicleId, marker)
    }
  })

  for (const [id, marker] of vehicleMarkers) {
    if (!currentIds.has(id)) {
      marker.remove()
      vehicleMarkers.delete(id)
    }
  }
}

function connectVehicleWebSocket() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/ws/vehicle`
  vehicleWs = new WebSocket(wsUrl)

  vehicleWs.onopen = () => { vehicleWsConnected.value = true }
  vehicleWs.onmessage = (event) => {
    try {
      const vehicles = JSON.parse(event.data)
      if (Array.isArray(vehicles)) {
        updateVehicleMarkers(vehicles)
      }
    } catch (e) {
      console.error('Failed to parse vehicle WS message:', e)
    }
  }
  vehicleWs.onclose = () => {
    vehicleWsConnected.value = false
    scheduleReconnect('vehicle', connectVehicleWebSocket)
  }
  vehicleWs.onerror = () => {
    vehicleWsConnected.value = false
    vehicleWs.close()
  }
}

function connectTrafficWebSocket() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/ws/traffic`
  trafficWs = new WebSocket(wsUrl)

  trafficWs.onopen = () => { trafficWsConnected.value = true }
  trafficWs.onmessage = (event) => {
    try {
      const segments = JSON.parse(event.data)
      if (Array.isArray(segments)) {
        updateMapSegments(segments)
      }
    } catch (e) {
      console.error('Failed to parse traffic WS message:', e)
    }
  }
  trafficWs.onclose = () => {
    trafficWsConnected.value = false
    scheduleReconnect('traffic', connectTrafficWebSocket)
  }
  trafficWs.onerror = () => {
    trafficWsConnected.value = false
    trafficWs.close()
  }
}

function connectAlertWebSocket() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/ws/alert`
  alertWs = new WebSocket(wsUrl)

  alertWs.onopen = () => { alertWsConnected.value = true }
  alertWs.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      if (Array.isArray(data)) {
        activeAlerts.value = data
      } else if (data.id) {
        if (!activeAlerts.value.find(a => a.id === data.id)) {
          activeAlerts.value.unshift(data)
          if (activeAlerts.value.length > 20) {
            activeAlerts.value.pop()
          }
          ElMessage.warning(`新告警: ${data.ruleName} - ${data.targetName}`)
        }
        if (data.alertType === 'CONGESTION' || data.alertType === 'PREDICTION_DEVIATION') {
          alertLines.add(data.targetId)
        }
      }
    } catch (e) {
      console.error('Failed to parse alert WS message:', e)
    }
  }
  alertWs.onclose = () => {
    alertWsConnected.value = false
    scheduleReconnect('alert', connectAlertWebSocket)
  }
  alertWs.onerror = () => {
    alertWsConnected.value = false
    alertWs.close()
  }
}

function scheduleReconnect(type, connectFn) {
  if (wsReconnectTimers[type]) clearTimeout(wsReconnectTimers[type])
  wsReconnectTimers[type] = setTimeout(() => connectFn(), 5000)
}

function getAlertType(level) {
  switch (level) {
    case 'CRITICAL': return 'danger'
    case 'WARNING': return 'warning'
    default: return 'info'
  }
}

function formatTime(timeStr) {
  if (!timeStr) return '--'
  const d = new Date(timeStr)
  const now = new Date()
  const diff = (now - d) / 1000
  if (diff < 60) return Math.floor(diff) + '秒前'
  if (diff < 3600) return Math.floor(diff / 60) + '分钟前'
  if (diff < 86400) return Math.floor(diff / 3600) + '小时前'
  return d.toLocaleDateString()
}

function handleAlertClick(alert) {
  if (alert.alertType === 'CONGESTION' || alert.alertType === 'PREDICTION_DEVIATION') {
    for (const [segId, line] of segmentLines) {
      if (segId === alert.targetId) {
        map.value.fitBounds(line.getBounds(), { padding: [50, 50] })
        line.openPopup()
        break
      }
    }
  }
}

async function loadMonitorData() {
  try {
    const [overview, devOverview, alertStatsData, activeAlertsData] = await Promise.all([
      monitorApi.getOverview().catch(() => null),
      trafficApi.getDeviationOverview().catch(() => null),
      alertApi.getStats().catch(() => null),
      alertApi.getActive(10).catch(() => null)
    ])

    if (overview) {
      systemMetrics.value = overview.system || {}
      businessMetrics.value = overview.business || {}
      apiMetrics.value = overview.api || {}
    }

    if (devOverview?.dailyTrend) {
      accuracyRate.value = devOverview.dailyTrend.overallAccuracy ?? '--'
      accuracyChange.value = devOverview.dailyTrend.accuracyChange ?? 0
    }

    alertStats.value = alertStatsData || {}
    if (Array.isArray(activeAlertsData) && activeAlerts.value.length === 0) {
      activeAlerts.value = activeAlertsData
    }

    updateSystemChart()
  } catch (e) {
    console.warn('Load monitor data failed:', e)
  }
}

async function loadDeviationData() {
  try {
    const data = await monitorApi.getDeviationDistribution()
    if (data && deviationChart) {
      deviationChart.setOption({
        xAxis: { data: data.labels || [] },
        series: [{
          data: data.avgDeviations || [],
          itemStyle: {
            color: (params) => {
              const val = params.value || 0
              if (val < 30) return '#67c23a'
              if (val < 60) return '#e6a23c'
              return '#f56c6c'
            },
            borderRadius: [4, 4, 0, 0]
          }
        }]
      })
    }
  } catch (e) {
    console.warn('Load deviation data failed:', e)
  }
}

async function loadApiResponseData() {
  try {
    const data = await monitorApi.getApiResponseDistribution()
    if (data && apiChart) {
      const labels = data.labels || []
      const values = data.values || []
      apiChart.setOption({
        xAxis: { data: labels },
        series: [
          {
            name: '请求数',
            type: 'bar',
            data: values,
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: '#409eff' },
                { offset: 1, color: 'rgba(64,158,255,0.3)' }
              ]),
              borderRadius: [4, 4, 0, 0]
            },
            barWidth: '50%'
          }
        ]
      })
    }
  } catch (e) {
    console.warn('Load api response data failed:', e)
  }
}

function initCharts() {
  if (deviationChartRef.value) {
    deviationChart = echarts.init(deviationChartRef.value)
    deviationChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 45, right: 20, top: 30, bottom: 30 },
      xAxis: {
        type: 'category',
        data: [],
        axisLabel: { fontSize: 10 }
      },
      yAxis: {
        type: 'value',
        name: '偏差率(%)',
        axisLabel: { fontSize: 10 }
      },
      series: [{
        type: 'bar',
        data: [],
        itemStyle: { borderRadius: [4, 4, 0, 0] }
      }]
    })
  }

  if (systemChartRef.value) {
    systemChart = echarts.init(systemChartRef.value)
    systemChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c}%' },
      legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
      series: [
        {
          type: 'pie',
          radius: ['40%', '70%'],
          center: ['25%', '45%'],
          data: [
            { value: 0, name: 'CPU使用率', itemStyle: { color: '#409eff' } },
            { value: 100, name: 'CPU空闲', itemStyle: { color: '#ebeef5' } }
          ],
          label: { show: false }
        },
        {
          type: 'pie',
          radius: ['40%', '70%'],
          center: ['75%', '45%'],
          data: [
            { value: 0, name: '内存使用率', itemStyle: { color: '#67c23a' } },
            { value: 100, name: '内存空闲', itemStyle: { color: '#ebeef5' } }
          ],
          label: { show: false }
        }
      ]
    })
  }

  if (apiChartRef.value) {
    apiChart = echarts.init(apiChartRef.value)
    apiChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 45, right: 20, top: 30, bottom: 30 },
      xAxis: {
        type: 'category',
        data: [],
        axisLabel: { fontSize: 10, rotate: 30 }
      },
      yAxis: {
        type: 'value',
        name: '请求数',
        axisLabel: { fontSize: 10 }
      },
      series: [
        {
          name: '请求数',
          type: 'bar',
          data: [],
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#409eff' },
              { offset: 1, color: 'rgba(64,158,255,0.3)' }
            ]),
            borderRadius: [4, 4, 0, 0]
          }
        }
      ]
    })
  }

  window.addEventListener('resize', () => {
    deviationChart && deviationChart.resize()
    systemChart && systemChart.resize()
    apiChart && apiChart.resize()
  })
}

function updateSystemChart() {
  if (systemChart) {
    const cpu = systemMetrics.value.cpu?.processCpuLoad || 0
    const mem = systemMetrics.value.memory?.heapUsedPercent || 0
    systemChart.setOption({
      series: [
        { data: [{ value: cpu, name: 'CPU使用率' }, { value: 100 - cpu, name: 'CPU空闲' }] },
        { data: [{ value: mem, name: '内存使用率' }, { value: 100 - mem, name: '内存空闲' }] }
      ]
    })
  }
}

function showTrajectoryReplay(vehicleId) {
  selectedVehicleId.value = vehicleId
  trajectoryDialogVisible.value = true
  nextTick(() => {
    initReplayMap()
    loadReplayData(vehicleId)
  })
}

function initReplayMap() {
  if (replayMap) {
    replayMap.remove()
  }
  replayMap = L.map('replay-map').setView([39.912, 116.407], 14)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors'
  }).addTo(replayMap)
}

async function loadReplayData(vehicleId) {
  try {
    const data = await trajectoryApi.getReplayData(vehicleId)
    replayData.value = data || {}

    if (replayData.value.path && replayData.value.path.length > 0) {
      const latlngs = replayData.value.path.map(p => [p.lat, p.lng])

      if (replayPathLine) replayPathLine.remove()
      replayPathLine = L.polyline(latlngs, {
        color: '#409eff', weight: 4, opacity: 0.6, dashArray: '10, 10'
      }).addTo(replayMap)

      replayMap.fitBounds(replayPathLine.getBounds(), { padding: [50, 50] })

      const startPoint = replayData.value.path[0]
      const busIcon = L.divIcon({
        className: 'replay-vehicle-marker',
        html: `<div style="
          width:32px; height:32px; background:#f56c6c; border-radius:50%;
          border:3px solid #fff; box-shadow:0 3px 10px rgba(0,0,0,0.4);
          display:flex; align-items:center; justify-content:center;
        ">
          <span style="color:#fff; font-size:16px; font-weight:bold">🚌</span>
        </div>`,
        iconSize: [32, 32],
        iconAnchor: [16, 16]
      })

      if (replayMarker) replayMarker.remove()
      replayMarker = L.marker([startPoint.lat, startPoint.lng], { icon: busIcon })
        .addTo(replayMap)
        .bindPopup(`车辆: ${vehicleId}`)

      updateReplayInfo(0)
    }
  } catch (e) {
    ElMessage.error('加载轨迹数据失败')
  }
}

function startReplay() {
  if (!replayData.value.path || replayData.value.path.length === 0) {
    ElMessage.warning('没有轨迹数据')
    return
  }
  isReplaying.value = true
  runReplay()
}

function runReplay() {
  if (!isReplaying.value) return
  if (replayProgress.value >= replayData.value.path.length - 1) {
    stopReplay()
    return
  }

  replayProgress.value++
  updateReplayInfo(replayProgress.value)

  const interval = Math.max(50, 500 / replaySpeed.value)
  replayTimer = setTimeout(runReplay, interval)
}

function pauseReplay() {
  isReplaying.value = false
  if (replayTimer) clearTimeout(replayTimer)
}

function stopReplay() {
  isReplaying.value = false
  if (replayTimer) clearTimeout(replayTimer)
  replayProgress.value = 0
  updateReplayInfo(0)
}

function handleProgressChange(val) {
  updateReplayInfo(val)
}

function updateReplayInfo(index) {
  if (!replayData.value.path || replayData.value.path.length === 0) return

  const point = replayData.value.path[index]
  if (!point) return

  if (replayMarker) {
    replayMarker.setLatLng([point.lat, point.lng])
    replayMarker.setPopupContent(`
      车辆: ${selectedVehicleId.value}<br/>
      速度: ${point.speed || 0} km/h<br/>
      时间: ${new Date(point.time).toLocaleTimeString()}
    `)
  }

  currentReplaySpeed.value = point.speed || 0
  currentReplayTime.value = point.time ? new Date(point.time).toLocaleString() : '--'
}

window.addEventListener('showTrajectory', (e) => {
  showTrajectoryReplay(e.detail)
})

onMounted(async () => {
  updateCurrentTime()
  timeTimer = setInterval(updateCurrentTime, 1000)

  initMap()
  connectVehicleWebSocket()
  connectTrafficWebSocket()
  connectAlertWebSocket()

  await nextTick()
  initCharts()

  await Promise.all([
    loadMonitorData(),
    loadDeviationData(),
    loadApiResponseData()
  ])

  monitorTimer = setInterval(loadMonitorData, 10000)
  deviationTimer = setInterval(() => {
    loadDeviationData()
    loadApiResponseData()
  }, 30000)
})

onUnmounted(() => {
  if (timeTimer) clearInterval(timeTimer)
  if (monitorTimer) clearInterval(monitorTimer)
  if (deviationTimer) clearInterval(deviationTimer)

  if (vehicleWs) vehicleWs.close()
  if (trafficWs) trafficWs.close()
  if (alertWs) alertWs.close()

  for (const key in wsReconnectTimers) {
    if (wsReconnectTimers[key]) clearTimeout(wsReconnectTimers[key])
  }

  if (map.value) map.value.remove()
  if (deviationChart) deviationChart.dispose()
  if (systemChart) systemChart.dispose()
  if (apiChart) apiChart.dispose()

  if (replayTimer) clearTimeout(replayTimer)
  if (replayMap) replayMap.remove()

  vehicleMarkers.clear()
  segmentLines.clear()
})
</script>

<style scoped>
.operation-dashboard {
  min-height: 100vh;
  padding: 16px;
  background: linear-gradient(135deg, #0a192f 0%, #112240 50%, #1a365d 100%);
  color: #fff;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 16px 24px;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 24px;
}

.dashboard-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 24px;
  font-weight: 700;
  margin: 0;
  background: linear-gradient(135deg, #64ffda, #4fd1c5);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-time {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  color: #a8b2d1;
  font-family: 'Courier New', monospace;
}

.stats-row {
  margin-bottom: 12px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  position: relative;
  overflow: hidden;
  transition: transform 0.3s, box-shadow 0.3s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
}

.gradient-blue { background: linear-gradient(135deg, rgba(64, 158, 255, 0.3), rgba(64, 158, 255, 0.1)); }
.gradient-green { background: linear-gradient(135deg, rgba(103, 194, 58, 0.3), rgba(103, 194, 58, 0.1)); }
.gradient-orange { background: linear-gradient(135deg, rgba(230, 162, 60, 0.3), rgba(230, 162, 60, 0.1)); }
.gradient-red { background: linear-gradient(135deg, rgba(245, 108, 108, 0.3), rgba(245, 108, 108, 0.1)); }
.gradient-purple { background: linear-gradient(135deg, rgba(114, 46, 209, 0.3), rgba(114, 46, 209, 0.1)); }
.gradient-cyan { background: linear-gradient(135deg, rgba(19, 194, 194, 0.3), rgba(19, 194, 194, 0.1)); }

.stat-icon-wrap {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  flex-shrink: 0;
}

.stat-content {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  line-height: 1.2;
}

.stat-unit {
  font-size: 16px;
  font-weight: 500;
  margin-left: 4px;
  color: #a8b2d1;
}

.stat-label {
  font-size: 13px;
  color: #a8b2d1;
  margin-top: 4px;
}

.stat-trend {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.1);
}

.stat-trend.up { color: #67c23a; }
.stat-trend.down { color: #f56c6c; }

.main-row {
  margin-bottom: 12px;
}

.map-panel, .alert-panel, .chart-panel {
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  overflow: hidden;
  height: 100%;
  min-height: 480px;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
}

.panel-actions {
  display: flex;
  gap: 8px;
}

.map-container {
  flex: 1;
  min-height: 400px;
  position: relative;
}

.map-legend {
  position: absolute;
  bottom: 16px;
  right: 16px;
  background: rgba(0, 0, 0, 0.7);
  padding: 12px 16px;
  border-radius: 8px;
  display: flex;
  gap: 20px;
  z-index: 1000;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #fff;
}

.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.legend-dot.smooth { background: #67c23a; }
.legend-dot.slow { background: #e6a23c; }
.legend-dot.congested { background: #f56c6c; }
.legend-dot.vehicle { background: #409eff; }
.legend-dot.alert { background: #ff00ff; }

.alert-panel {
  min-height: 480px;
}

.alert-badge {
  margin-left: 8px;
}

.alert-stats {
  display: flex;
  gap: 1px;
  background: rgba(255, 255, 255, 0.1);
}

.alert-stat {
  flex: 1;
  padding: 16px;
  text-align: center;
  background: rgba(255, 255, 255, 0.03);
}

.alert-stat-value {
  display: block;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}

.alert-stat.critical .alert-stat-value { color: #f56c6c; }
.alert-stat.warning .alert-stat-value { color: #e6a23c; }
.alert-stat.info .alert-stat-value { color: #909399; }

.alert-stat-label {
  font-size: 12px;
  color: #a8b2d1;
  margin-top: 4px;
}

.alert-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.alert-item {
  padding: 12px;
  margin-bottom: 8px;
  border-radius: 8px;
  border-left: 3px solid;
  background: rgba(255, 255, 255, 0.05);
  cursor: pointer;
  transition: all 0.2s;
}

.alert-item:hover {
  background: rgba(255, 255, 255, 0.1);
  transform: translateX(4px);
}

.alert-item.alert-critical { border-left-color: #f56c6c; }
.alert-item.alert-warning { border-left-color: #e6a23c; }
.alert-item.alert-info { border-left-color: #909399; }

.alert-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.alert-time {
  font-size: 11px;
  color: #a8b2d1;
}

.alert-title {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  margin-bottom: 4px;
}

.alert-target {
  font-size: 12px;
  color: #64ffda;
  margin-bottom: 4px;
}

.alert-message {
  font-size: 12px;
  color: #a8b2d1;
  line-height: 1.5;
}

.no-alerts {
  height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #a8b2d1;
}

.chart-row {
  margin-bottom: 0;
}

.chart-panel {
  min-height: 280px;
}

.chart-container {
  flex: 1;
  padding: 8px 16px 16px;
  min-height: 220px;
}

:deep(.el-tag) {
  --el-tag-text-color: #fff;
}

:deep(.leaflet-container) {
  background: #0a192f;
}

.trajectory-dialog :deep(.el-dialog__body) {
  padding: 20px;
}

.trajectory-replay {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.replay-controls {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.replay-info {
  display: flex;
  gap: 12px;
}

.replay-map {
  width: 100%;
  height: 500px;
  border-radius: 8px;
}

:deep(.vehicle-marker) {
  background: transparent !important;
  border: none !important;
}

:deep(.replay-vehicle-marker) {
  background: transparent !important;
  border: none !important;
}

::-webkit-scrollbar {
  width: 6px;
}

::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.05);
}

::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.3);
}
</style>
