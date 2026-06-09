<template>
  <div class="prediction-page">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="queryForm" class="search-form">
        <el-form-item label="车辆">
          <el-select
            v-model="queryForm.vehicleId"
            placeholder="请选择车辆"
            filterable
            clearable
            style="width: 220px"
          >
            <el-option
              v-for="v in vehicleList"
              :key="v.vehicleId"
              :label="v.vehicleId + (v.plateNumber ? ' - ' + v.plateNumber : '')"
              :value="v.vehicleId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="线路">
          <el-select
            v-model="queryForm.routeId"
            placeholder="请选择线路"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="r in routeOptions"
              :key="r.value"
              :label="r.label"
              :value="r.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleQuery">
            查询
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="result-card">
      <template #header>
        <span>到站预测结果</span>
      </template>
      <el-table :data="predictionList" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="stationName" label="站点名称" min-width="140" />
        <el-table-column prop="distanceToStation" label="距站距离" width="120" align="right">
          <template #default="{ row }">
            {{ formatDistance(row.distanceToStation) }}
          </template>
        </el-table-column>
        <el-table-column prop="estimatedSeconds" label="预计到达" width="120" align="right">
          <template #default="{ row }">
            {{ formatSeconds(row.estimatedSeconds) }}
          </template>
        </el-table-column>
        <el-table-column prop="congestionFactor" label="拥堵程度" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="congestionLevel(row.congestionFactor).type" size="small">
              {{ congestionLevel(row.congestionFactor).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentSpeed" label="当前速度(km/h)" width="140" align="right">
          <template #default="{ row }">
            {{ row.currentSpeed != null ? row.currentSpeed.toFixed(1) : '--' }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { vehicleApi } from '../api/index.js'
import { formatSeconds, formatDistance, congestionLevel } from '../utils/format.js'
import { ElMessage } from 'element-plus'

const queryForm = ref({
  vehicleId: '',
  routeId: ''
})

const routeOptions = [
  { label: 'L001', value: 'L001' },
  { label: 'L002', value: 'L002' },
  { label: 'L003', value: 'L003' }
]

const vehicleList = ref([])
const predictionList = ref([])
const loading = ref(false)

onMounted(async () => {
  try {
    vehicleList.value = await vehicleApi.getOnlineList()
  } catch {
    ElMessage.error('获取在线车辆列表失败')
  }
})

async function handleQuery() {
  if (!queryForm.value.vehicleId) {
    ElMessage.warning('请选择车辆')
    return
  }
  if (!queryForm.value.routeId) {
    ElMessage.warning('请选择线路')
    return
  }
  loading.value = true
  try {
    predictionList.value = await vehicleApi.getPrediction(
      queryForm.value.vehicleId,
      queryForm.value.routeId
    )
    if (!predictionList.value || predictionList.value.length === 0) {
      ElMessage.info('暂无预测数据')
    }
  } catch {
    ElMessage.error('查询预测数据失败')
    predictionList.value = []
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.prediction-page {
  height: 100%;
}

.search-card {
  margin-bottom: 16px;
}

.search-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.result-card {
  flex: 1;
}
</style>
