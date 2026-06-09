<template>
  <div class="vehicle-monitor">
    <el-card class="monitor-card">
      <template #header>
        <div class="monitor-header">
          <span class="monitor-title">在线车辆列表</span>
          <div class="header-actions">
            <el-input
              v-model="searchQuery"
              placeholder="搜索车辆ID"
              clearable
              style="width: 240px"
              :prefix-icon="Search"
            />
            <el-tag type="info" size="small">自动刷新: 3s</el-tag>
          </div>
        </div>
      </template>

      <el-table
        :data="filteredVehicles"
        stripe
        highlight-current-row
        @row-click="handleRowClick"
        style="width: 100%"
        max-height="calc(100vh - 220px)"
      >
        <el-table-column prop="vehicleId" label="车辆ID" min-width="140" />
        <el-table-column prop="longitude" label="经度" min-width="110" align="right">
          <template #default="{ row }">
            {{ row.longitude != null ? row.longitude.toFixed(6) : '--' }}
          </template>
        </el-table-column>
        <el-table-column prop="latitude" label="纬度" min-width="110" align="right">
          <template #default="{ row }">
            {{ row.latitude != null ? row.latitude.toFixed(6) : '--' }}
          </template>
        </el-table-column>
        <el-table-column label="速度(km/h)" min-width="110" align="right">
          <template #default="{ row }">
            {{ row.speed != null ? (row.speed * 3.6).toFixed(1) : '--' }}
          </template>
        </el-table-column>
        <el-table-column prop="direction" label="方向" min-width="80" align="center">
          <template #default="{ row }">
            {{ row.direction != null ? row.direction + '°' : '--' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="GPS时间" min-width="180">
          <template #default="{ row }">
            {{ formatTimestamp(row.gpsTime) }}
          </template>
        </el-table-column>
        <el-table-column label="接收时间" min-width="180">
          <template #default="{ row }">
            {{ formatTimestamp(row.receiveTime) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer
      v-model="drawerVisible"
      title="车辆详情"
      size="420px"
      direction="rtl"
    >
      <template v-if="selectedVehicle">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="车辆ID">{{ selectedVehicle.vehicleId }}</el-descriptions-item>
          <el-descriptions-item label="经度">{{ selectedVehicle.longitude ?? '--' }}</el-descriptions-item>
          <el-descriptions-item label="纬度">{{ selectedVehicle.latitude ?? '--' }}</el-descriptions-item>
          <el-descriptions-item label="速度">
            {{ selectedVehicle.speed != null ? (selectedVehicle.speed * 3.6).toFixed(1) + ' km/h' : '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="方向">
            {{ selectedVehicle.direction != null ? selectedVehicle.direction + '°' : '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(selectedVehicle.status)" size="small">
              {{ getStatusText(selectedVehicle.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="GeoHash">{{ selectedVehicle.geoHash ?? '--' }}</el-descriptions-item>
          <el-descriptions-item label="GPS时间">{{ formatTimestamp(selectedVehicle.gpsTime) }}</el-descriptions-item>
          <el-descriptions-item label="接收时间">{{ formatTimestamp(selectedVehicle.receiveTime) }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { vehicleApi } from '../api/index.js'
import { statusTag, formatDistance } from '../utils/format.js'

const vehicles = ref([])
const searchQuery = ref('')
const drawerVisible = ref(false)
const selectedVehicle = ref(null)
let refreshTimer = null

const filteredVehicles = computed(() => {
  if (!searchQuery.value) return vehicles.value
  const q = searchQuery.value.toLowerCase()
  return vehicles.value.filter(v => v.vehicleId?.toLowerCase().includes(q))
})

function getStatusType(status) {
  return statusTag(status).type
}

function getStatusText(status) {
  return statusTag(status).text
}

function formatTimestamp(ms) {
  if (ms == null) return '--'
  const d = new Date(ms)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function fetchVehicles() {
  try {
    const data = await vehicleApi.getOnlineList()
    vehicles.value = Array.isArray(data) ? data : []
  } catch {
    // silent
  }
}

function handleRowClick(row) {
  selectedVehicle.value = row
  drawerVisible.value = true
}

onMounted(() => {
  fetchVehicles()
  refreshTimer = setInterval(fetchVehicles, 3000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style scoped>
.vehicle-monitor {
  height: 100%;
}

.monitor-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.monitor-card :deep(.el-card__body) {
  flex: 1;
  padding: 0;
  min-height: 0;
}

.monitor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.monitor-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

:deep(.el-table) {
  --el-table-header-bg-color: #f5f7fa;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
