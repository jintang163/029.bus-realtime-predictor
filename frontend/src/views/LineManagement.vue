<template>
  <div class="line-management-page">
    <div class="left-panel">
      <el-card shadow="never">
        <template #header>
          <div class="panel-header">
            <span>线路列表</span>
            <div>
              <el-button type="primary" :icon="Plus" @click="handleAdd">新增线路</el-button>
              <el-button :icon="Refresh" @click="handleRefreshCache">刷新缓存</el-button>
            </div>
          </div>
        </template>
        <el-table
          :data="lineList"
          v-loading="listLoading"
          stripe
          border
          highlight-current-row
          @current-change="handleRowClick"
          style="width: 100%"
        >
          <el-table-column prop="lineId" label="线路ID" width="180" show-overflow-tooltip />
          <el-table-column prop="lineName" label="线路名称" min-width="120" />
          <el-table-column prop="lineCode" label="线路编码" width="100" />
          <el-table-column prop="direction" label="方向" width="80" align="center">
            <template #default="{ row }">
              {{ row.direction === 0 ? '上行' : '下行' }}
            </template>
          </el-table-column>
          <el-table-column prop="startStation" label="起点站" width="120" show-overflow-tooltip />
          <el-table-column prop="endStation" label="终点站" width="120" show-overflow-tooltip />
          <el-table-column prop="stationCount" label="站点数" width="80" align="center" />
          <el-table-column prop="firstBusTime" label="首班" width="80" align="center">
            <template #default="{ row }">
              {{ formatTime(row.firstBusTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="lastBusTime" label="末班" width="80" align="center">
            <template #default="{ row }">
              {{ formatTime(row.lastBusTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="intervalMinutes" label="间隔(分)" width="90" align="center" />
          <el-table-column prop="status" label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                {{ row.status === 1 ? '运营' : '停运' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="handleEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click.stop="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <div class="right-panel">
      <el-card shadow="never">
        <template #header>
          <span>{{ currentLine ? '【' + currentLine.lineName + '】途经站点' : '途经站点' }}</span>
        </template>
        <el-table
          :data="stationList"
          v-loading="stationLoading"
          stripe
          border
          style="width: 100%"
          max-height="600"
        >
          <el-table-column prop="stationOrder" label="序号" width="60" align="center" />
          <el-table-column prop="stationId" label="站点ID" width="160" show-overflow-tooltip />
          <el-table-column prop="stationName" label="站点名称" min-width="120" />
          <el-table-column prop="longitude" label="经度" width="110" align="right">
            <template #default="{ row }">
              {{ row.longitude != null ? row.longitude.toFixed(6) : '--' }}
            </template>
          </el-table-column>
          <el-table-column prop="latitude" label="纬度" width="110" align="right">
            <template #default="{ row }">
              {{ row.latitude != null ? row.latitude.toFixed(6) : '--' }}
            </template>
          </el-table-column>
          <el-table-column prop="distanceFromStart" label="距起点(m)" width="100" align="right" />
          <el-table-column prop="distanceToNext" label="距下站(m)" width="100" align="right" />
        </el-table>
      </el-card>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑线路' : '新增线路'"
      width="600px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item v-if="isEdit" label="线路ID" prop="lineId">
          <el-input v-model="formData.lineId" disabled />
        </el-form-item>
        <el-form-item label="线路名称" prop="lineName">
          <el-input v-model="formData.lineName" placeholder="请输入线路名称" />
        </el-form-item>
        <el-form-item label="线路编码" prop="lineCode">
          <el-input v-model="formData.lineCode" placeholder="请输入线路编码" />
        </el-form-item>
        <el-form-item label="方向" prop="direction">
          <el-select v-model="formData.direction" placeholder="请选择方向" style="width: 100%">
            <el-option label="上行" :value="0" />
            <el-option label="下行" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="起点站" prop="startStation">
          <el-input v-model="formData.startStation" placeholder="请输入起点站" />
        </el-form-item>
        <el-form-item label="终点站" prop="endStation">
          <el-input v-model="formData.endStation" placeholder="请输入终点站" />
        </el-form-item>
        <el-form-item label="总里程(km)" prop="totalDistance">
          <el-input-number v-model="formData.totalDistance" :min="0" :precision="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="站点数" prop="stationCount">
          <el-input-number v-model="formData.stationCount" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="首班时间" prop="firstBusTime">
          <el-input-number v-model="formData.firstBusTime" :min="0" :max="2359" :step="1" placeholder="如 600" style="width: 100%" />
        </el-form-item>
        <el-form-item label="末班时间" prop="lastBusTime">
          <el-input-number v-model="formData.lastBusTime" :min="0" :max="2359" :step="1" placeholder="如 2200" style="width: 100%" />
        </el-form-item>
        <el-form-item label="发车间隔(分)" prop="intervalMinutes">
          <el-input-number v-model="formData.intervalMinutes" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="formData.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="运营" :value="1" />
            <el-option label="停运" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { lineApi } from '../api/index.js'
import { formatTime } from '../utils/format.js'
import { ElMessage, ElMessageBox } from 'element-plus'

const lineList = ref([])
const stationList = ref([])
const currentLine = ref(null)
const listLoading = ref(false)
const stationLoading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)

const defaultForm = {
  lineId: '',
  lineName: '',
  lineCode: '',
  direction: 0,
  startStation: '',
  endStation: '',
  totalDistance: 0,
  stationCount: 0,
  firstBusTime: 600,
  lastBusTime: 2200,
  intervalMinutes: 10,
  status: 1
}

const formData = reactive({ ...defaultForm })

const formRules = {
  lineName: [{ required: true, message: '请输入线路名称', trigger: 'blur' }],
  lineCode: [{ required: true, message: '请输入线路编码', trigger: 'blur' }],
  direction: [{ required: true, message: '请选择方向', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

onMounted(() => {
  fetchLineList()
})

async function fetchLineList() {
  listLoading.value = true
  try {
    lineList.value = await lineApi.list()
  } catch {
    ElMessage.error('获取线路列表失败')
  } finally {
    listLoading.value = false
  }
}

async function handleRowClick(row) {
  currentLine.value = row
  if (!row) {
    stationList.value = []
    return
  }
  stationLoading.value = true
  try {
    stationList.value = await lineApi.getStations(row.lineId)
  } catch {
    ElMessage.error('获取站点数据失败')
    stationList.value = []
  } finally {
    stationLoading.value = false
  }
}

function handleAdd() {
  isEdit.value = false
  Object.assign(formData, { ...defaultForm })
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  Object.assign(formData, { ...row })
  dialogVisible.value = true
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await lineApi.update({ ...formData })
      ElMessage.success('更新成功')
    } else {
      const { lineId, ...createData } = formData
      await lineApi.create(createData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchLineList()
  } catch {
    ElMessage.error(isEdit.value ? '更新失败' : '新增失败')
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除线路【${row.lineName}】？`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    await lineApi.delete(row.lineId)
    ElMessage.success('删除成功')
    if (currentLine.value && currentLine.value.lineId === row.lineId) {
      currentLine.value = null
      stationList.value = []
    }
    fetchLineList()
  } catch {
    // cancelled or failed
  }
}

async function handleRefreshCache() {
  try {
    await lineApi.refreshCache()
    ElMessage.success('缓存刷新成功')
  } catch {
    ElMessage.error('缓存刷新失败')
  }
}
</script>

<style scoped>
.line-management-page {
  display: flex;
  gap: 16px;
  height: 100%;
}

.left-panel {
  flex: 6;
  min-width: 0;
}

.right-panel {
  flex: 4;
  min-width: 0;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
