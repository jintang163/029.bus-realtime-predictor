<template>
  <div class="schedule-management">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>排班管理</span>
          <div>
            <el-button type="primary" @click="handleAdd">新增排班</el-button>
            <el-popconfirm title="确认删除当日全部排班？" @confirm="handleDeleteAll">
              <template #reference>
                <el-button type="danger">删除当日排班</el-button>
              </template>
            </el-popconfirm>
            <el-button @click="excelDialogVisible = true">导入Excel</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" class="query-form">
        <el-form-item label="线路">
          <el-select v-model="queryForm.lineId" placeholder="请选择线路" clearable style="width:200px">
            <el-option
              v-for="line in lineList"
              :key="line.lineId"
              :label="line.lineName"
              :value="line.lineId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker
            v-model="queryForm.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width:180px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadSchedules">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="schedules" stripe v-loading="loading">
        <el-table-column prop="vehicleId" label="车辆ID" width="120" />
        <el-table-column prop="driverName" label="司机姓名" width="120" />
        <el-table-column prop="departureTime" label="发车时间" width="120">
          <template #default="{ row }">
            {{ formatTime(row.departureTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="tripIndex" label="班次序号" width="100" />
        <el-table-column prop="direction" label="方向" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 0 ? 'info' : 'warning'">
              {{ scheduleStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="addDialogVisible"
      title="新增排班"
      width="500px"
      destroy-on-close
    >
      <el-form :model="addForm" :rules="addRules" ref="addFormRef" label-width="90px">
        <el-form-item label="车辆ID" prop="vehicleId">
          <el-input v-model="addForm.vehicleId" placeholder="请输入车辆ID" />
        </el-form-item>
        <el-form-item label="司机姓名" prop="driverName">
          <el-input v-model="addForm.driverName" placeholder="请输入司机姓名" />
        </el-form-item>
        <el-form-item label="发车时间" prop="departureTime">
          <el-input-number
            v-model="addForm.departureTime"
            :min="0"
            :max="2359"
            :step="1"
            placeholder="如800表示08:00"
            style="width:100%"
          />
          <div class="tip-text">输入整数，如 800 表示 08:00，1430 表示 14:30</div>
        </el-form-item>
        <el-form-item label="班次序号" prop="tripIndex">
          <el-input-number v-model="addForm.tripIndex" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="方向" prop="direction">
          <el-select v-model="addForm.direction" placeholder="请选择方向" style="width:100%">
            <el-option :value="0" label="上行" />
            <el-option :value="1" label="下行" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAddSubmit" :loading="addSubmitting">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="excelDialogVisible" title="导入Excel" width="480px" destroy-on-close>
      <el-upload
        action=""
        :http-request="handleImportExcel"
        :limit="1"
        :on-exceed="() => ElMessage.warning('只能上传一个文件')"
        accept=".xlsx,.xls"
        drag
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .xlsx / .xls 文件</div>
        </template>
      </el-upload>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { scheduleApi, lineApi } from '../api/index.js'
import { formatTime } from '../utils/format.js'

const loading = ref(false)
const schedules = ref([])
const lineList = ref([])

const queryForm = reactive({
  lineId: null,
  date: new Date().toISOString().slice(0, 10)
})

const addDialogVisible = ref(false)
const addSubmitting = ref(false)
const addFormRef = ref(null)

const defaultAddForm = {
  vehicleId: '',
  driverName: '',
  departureTime: 800,
  tripIndex: 1,
  direction: 0
}
const addForm = reactive({ ...defaultAddForm })

const addRules = {
  vehicleId: [{ required: true, message: '请输入车辆ID', trigger: 'blur' }],
  driverName: [{ required: true, message: '请输入司机姓名', trigger: 'blur' }],
  departureTime: [{ required: true, message: '请输入发车时间', trigger: 'blur' }],
  tripIndex: [{ required: true, message: '请输入班次序号', trigger: 'blur' }],
  direction: [{ required: true, message: '请选择方向', trigger: 'change' }]
}

const excelDialogVisible = ref(false)

function scheduleStatusText(status) {
  const map = { 0: '待执行', 1: '已执行', 2: '已取消' }
  return map[status] ?? '未知'
}

async function loadLines() {
  try {
    lineList.value = await lineApi.list()
  } catch (e) {
    console.error(e)
  }
}

async function loadSchedules() {
  if (!queryForm.lineId) {
    ElMessage.warning('请选择线路')
    return
  }
  loading.value = true
  try {
    schedules.value = await scheduleApi.list(queryForm.lineId, queryForm.date)
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  if (!queryForm.lineId || !queryForm.date) {
    ElMessage.warning('请先选择线路和日期')
    return
  }
  Object.assign(addForm, { ...defaultAddForm })
  addDialogVisible.value = true
}

async function handleAddSubmit() {
  const valid = await addFormRef.value.validate().catch(() => false)
  if (!valid) return
  addSubmitting.value = true
  try {
    await scheduleApi.create({
      ...addForm,
      lineId: queryForm.lineId,
      scheduleDate: queryForm.date
    })
    ElMessage.success('新增排班成功')
    addDialogVisible.value = false
    await loadSchedules()
  } catch (e) {
    console.error(e)
  } finally {
    addSubmitting.value = false
  }
}

async function handleDeleteAll() {
  if (!queryForm.lineId || !queryForm.date) {
    ElMessage.warning('请先选择线路和日期')
    return
  }
  try {
    await scheduleApi.delete(queryForm.lineId, queryForm.date)
    ElMessage.success('删除成功')
    await loadSchedules()
  } catch (e) {
    console.error(e)
  }
}

async function handleImportExcel({ file }) {
  if (!queryForm.lineId || !queryForm.date) {
    ElMessage.warning('请先选择线路和日期')
    return
  }
  try {
    const res = await scheduleApi.importExcel(file, queryForm.lineId, queryForm.date)
    ElMessage.success(`导入成功，共 ${res ?? 0} 条记录`)
    excelDialogVisible.value = false
    await loadSchedules()
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => {
  loadLines()
})
</script>

<style scoped>
.schedule-management {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.query-form {
  margin-bottom: 16px;
}

.tip-text {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
