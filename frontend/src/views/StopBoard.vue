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

    <el-card v-if="stationInfo" class="map-card" shadow="hover">
      <template #header>
        <span class="card-title">站点位置</span>
      </template>
      <div id="station-map" class="map-container"></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { stopboardApi } from '../api/index.js'
import { formatTime } from '../utils/format.js'
import L from 'leaflet'

const stationId = ref('')
const loading = ref(false)
const stationInfo = ref(null)
const stationLines = ref([])

let mapInstance = null
let markerInstance = null

async function handleQuery() {
  const id = stationId.value?.trim()
  if (!id) return

  loading.value = true
  stationInfo.value = null
  stationLines.value = []

  try {
    const [info, lines] = await Promise.all([
      stopboardApi.getStationInfo(id),
      stopboardApi.getStationLines(id)
    ])
    stationInfo.value = info
    stationLines.value = lines || []

    await nextTick()
    initMap()
  } catch {
    stationInfo.value = null
    stationLines.value = []
  } finally {
    loading.value = false
  }
}

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

.map-card {
  margin-bottom: 20px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.map-container {
  height: 300px;
  width: 100%;
  border-radius: 4px;
}
</style>
