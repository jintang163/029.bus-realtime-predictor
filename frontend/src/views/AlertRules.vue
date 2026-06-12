<template>
  <div class="alert-rules-page">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="告警规则" name="rules">
        <div class="page-header">
          <div class="header-title">
            <el-icon :size="20" color="#409eff"><Setting /></el-icon>
            告警规则管理
          </div>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            新增规则
          </el-button>
        </div>

        <el-card class="rules-card">
          <el-table :data="rulesList" stripe style="width: 100%">
            <el-table-column prop="ruleName" label="规则名称" min-width="140" />
            <el-table-column prop="ruleType" label="规则类型" min-width="140">
              <template #default="{ row }">
                <el-tag :type="getRuleTypeColor(row.ruleType)" size="small">
                  {{ getRuleTypeText(row.ruleType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="targetType" label="目标类型" min-width="100">
              <template #default="{ row }">
                {{ getTargetTypeText(row.targetType) }}
              </template>
            </el-table-column>
            <el-table-column label="触发条件" min-width="200">
              <template #default="{ row }">
                <span class="condition-text">
                  {{ getTargetTypeText(row.targetType) }}
                  {{ getOperatorText(row.operator) }}
                  <el-tag type="danger" size="small">{{ row.threshold }}</el-tag>
                  持续 {{ row.duration }}秒
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="notificationType" label="通知方式" min-width="100">
              <template #default="{ row }">
                {{ getNotificationTypeText(row.notificationType) }}
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.enabled === 1"
                  @change="toggleRule(row)"
                  active-text="启用"
                  inactive-text="禁用"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="editRule(row)">
                  编辑
                </el-button>
                <el-button type="danger" link size="small" @click="deleteRule(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="告警记录" name="records">
        <div class="page-header">
          <div class="header-title">
            <el-icon :size="20" color="#f56c6c"><Document /></el-icon>
            告警记录
          </div>
          <div class="filter-actions">
            <el-select v-model="filterType" placeholder="告警类型" clearable size="default" style="width: 140px">
              <el-option label="预测偏差" value="PREDICTION_DEVIATION" />
              <el-option label="车辆离线" value="VEHICLE_OFFLINE" />
              <el-option label="路段拥堵" value="CONGESTION" />
              <el-option label="API响应" value="API_RESPONSE" />
              <el-option label="在线率" value="ONLINE_RATE" />
            </el-select>
            <el-select v-model="filterLevel" placeholder="告警级别" clearable size="default" style="width: 120px; margin-left: 12px">
              <el-option label="严重" value="CRITICAL" />
              <el-option label="警告" value="WARNING" />
              <el-option label="提示" value="INFO" />
            </el-select>
            <el-select v-model="filterStatus" placeholder="状态" clearable size="default" style="width: 120px; margin-left: 12px">
              <el-option label="活跃" value="ACTIVE" />
              <el-option label="已确认" value="ACKNOWLEDGED" />
              <el-option label="已解决" value="RESOLVED" />
            </el-select>
            <el-button type="primary" style="margin-left: 12px" @click="loadRecords">
              查询
            </el-button>
          </div>
        </div>

        <el-card class="records-card">
          <el-table :data="recordsList" stripe style="width: 100%" max-height="calc(100vh - 320px)">
            <el-table-column label="级别" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="getAlertLevelColor(row.alertLevel)" size="small" effect="dark">
                  {{ row.alertLevel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ruleName" label="规则名称" min-width="140" />
            <el-table-column prop="alertType" label="类型" min-width="120">
              <template #default="{ row }">
                {{ getRuleTypeText(row.alertType) }}
              </template>
            </el-table-column>
            <el-table-column prop="targetName" label="目标" min-width="160" show-overflow-tooltip />
            <el-table-column label="告警值" width="120">
              <template #default="{ row }">
                <span class="alert-value">{{ row.alertValue?.toFixed(2) || '--' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="阈值" width="120">
              <template #default="{ row }">
                {{ row.threshold }} ({{ row.operator }})
              </template>
            </el-table-column>
            <el-table-column prop="message" label="消息" min-width="200" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusColor(row.status)" size="small">
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="时间" width="160">
              <template #default="{ row }">
                {{ formatTime(row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'ACTIVE'"
                  type="primary"
                  link
                  size="small"
                  @click="acknowledgeAlert(row)"
                >
                  确认
                </el-button>
                <el-button
                  v-if="row.status !== 'RESOLVED'"
                  type="success"
                  link
                  size="small"
                  @click="resolveAlert(row)"
                >
                  解决
                </el-button>
                <el-button type="info" link size="small" @click="showAlertDetail(row)">
                  详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="totalRecords"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
            />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑告警规则' : '新增告警规则'"
      width="600px"
      destroy-on-close
    >
      <el-form :model="ruleForm" :rules="formRules" ref="ruleFormRef" label-width="120px">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="ruleForm.ruleName" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="规则类型" prop="ruleType">
          <el-select v-model="ruleForm.ruleType" placeholder="请选择规则类型" style="width: 100%">
            <el-option label="预测偏差告警" value="PREDICTION_DEVIATION" />
            <el-option label="车辆离线告警" value="VEHICLE_OFFLINE" />
            <el-option label="路段拥堵告警" value="CONGESTION" />
            <el-option label="API响应告警" value="API_RESPONSE" />
            <el-option label="设备在线率告警" value="ONLINE_RATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标类型" prop="targetType">
          <el-select v-model="ruleForm.targetType" placeholder="请选择目标类型" style="width: 100%">
            <el-option label="线路" value="LINE" />
            <el-option label="车辆" value="VEHICLE" />
            <el-option label="路段" value="SEGMENT" />
            <el-option label="API" value="API" />
            <el-option label="系统" value="SYSTEM" />
          </el-select>
        </el-form-item>
        <el-form-item label="阈值" prop="threshold">
          <el-input-number v-model="ruleForm.threshold" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="比较符" prop="operator">
          <el-select v-model="ruleForm.operator" placeholder="请选择比较符" style="width: 100%">
            <el-option label="大于 ( > )" value=">" />
            <el-option label="大于等于 ( >= )" value=">=" />
            <el-option label="小于 ( < )" value="<" />
            <el-option label="小于等于 ( <= )" value="<=" />
            <el-option label="等于 ( == )" value="==" />
            <el-option label="不等于 ( != )" value="!=" />
          </el-select>
        </el-form-item>
        <el-form-item label="持续时间(秒)" prop="duration">
          <el-input-number v-model="ruleForm.duration" :min="1" :max="3600" style="width: 100%" />
        </el-form-item>
        <el-form-item label="通知方式" prop="notificationType">
          <el-select v-model="ruleForm.notificationType" placeholder="请选择通知方式" style="width: 100%">
            <el-option label="钉钉" value="DINGTALK" />
            <el-option label="短信" value="SMS" />
            <el-option label="全部" value="ALL" />
            <el-option label="不通知" value="NONE" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知目标">
          <el-input v-model="ruleForm.notificationTarget" placeholder="手机号或钉钉群ID，多个用逗号分隔" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="ruleForm.description" type="textarea" :rows="3" placeholder="请输入规则描述" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="ruleForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRule">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="告警详情" width="600px">
      <el-descriptions :column="2" border v-if="currentAlert">
        <el-descriptions-item label="告警级别">
          <el-tag :type="getAlertLevelColor(currentAlert.alertLevel)" effect="dark">
            {{ currentAlert.alertLevel }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="告警类型">
          {{ getRuleTypeText(currentAlert.alertType) }}
        </el-descriptions-item>
        <el-descriptions-item label="规则名称">{{ currentAlert.ruleName }}</el-descriptions-item>
        <el-descriptions-item label="目标">{{ currentAlert.targetName }}</el-descriptions-item>
        <el-descriptions-item label="告警值">{{ currentAlert.alertValue?.toFixed(2) || '--' }}</el-descriptions-item>
        <el-descriptions-item label="阈值">
          {{ currentAlert.operator }} {{ currentAlert.threshold }}
        </el-descriptions-item>
        <el-descriptions-item label="状态" :span="2">
          <el-tag :type="getStatusColor(currentAlert.status)">
            {{ getStatusText(currentAlert.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="告警消息" :span="2">
          {{ currentAlert.message }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(currentAlert.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatTime(currentAlert.updateTime) }}</el-descriptions-item>
        <el-descriptions-item label="确认人" v-if="currentAlert.acknowledgedBy">
          {{ currentAlert.acknowledgedBy }}
        </el-descriptions-item>
        <el-descriptions-item label="确认时间" v-if="currentAlert.acknowledgedTime">
          {{ formatTime(currentAlert.acknowledgedTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="解决时间" v-if="currentAlert.resolvedTime" :span="2">
          {{ formatTime(currentAlert.resolvedTime) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Setting, Plus, Document } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { alertApi } from '../api/index.js'

const activeTab = ref('rules')
const rulesList = ref([])
const recordsList = ref([])
const currentPage = ref(1)
const pageSize = ref(20)
const totalRecords = ref(0)

const filterType = ref('')
const filterLevel = ref('')
const filterStatus = ref('')

const dialogVisible = ref(false)
const isEdit = ref(false)
const ruleFormRef = ref(null)
const ruleForm = ref({
  id: null,
  ruleName: '',
  ruleType: '',
  targetType: '',
  targetValue: '',
  threshold: 0,
  operator: '>',
  duration: 60,
  notificationType: 'DINGTALK',
  notificationTarget: '',
  description: '',
  enabled: 1
})

const formRules = {
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  ruleType: [{ required: true, message: '请选择规则类型', trigger: 'change' }],
  targetType: [{ required: true, message: '请选择目标类型', trigger: 'change' }],
  threshold: [{ required: true, message: '请输入阈值', trigger: 'blur' }],
  operator: [{ required: true, message: '请选择比较符', trigger: 'change' }],
  duration: [{ required: true, message: '请输入持续时间', trigger: 'blur' }],
  notificationType: [{ required: true, message: '请选择通知方式', trigger: 'change' }]
}

const detailVisible = ref(false)
const currentAlert = ref(null)

function getRuleTypeText(type) {
  const map = {
    'PREDICTION_DEVIATION': '预测偏差',
    'VEHICLE_OFFLINE': '车辆离线',
    'CONGESTION': '路段拥堵',
    'API_RESPONSE': 'API响应',
    'ONLINE_RATE': '在线率'
  }
  return map[type] || type
}

function getRuleTypeColor(type) {
  const map = {
    'PREDICTION_DEVIATION': 'warning',
    'VEHICLE_OFFLINE': 'danger',
    'CONGESTION': 'danger',
    'API_RESPONSE': 'info',
    'ONLINE_RATE': 'warning'
  }
  return map[type] || 'info'
}

function getTargetTypeText(type) {
  const map = {
    'LINE': '线路',
    'VEHICLE': '车辆',
    'SEGMENT': '路段',
    'API': 'API',
    'SYSTEM': '系统'
  }
  return map[type] || type
}

function getOperatorText(op) {
  const map = {
    '>': '大于',
    '>=': '大于等于',
    '<': '小于',
    '<=': '小于等于',
    '==': '等于',
    '!=': '不等于'
  }
  return map[op] || op
}

function getNotificationTypeText(type) {
  const map = {
    'DINGTALK': '钉钉',
    'SMS': '短信',
    'ALL': '全部',
    'NONE': '不通知'
  }
  return map[type] || type
}

function getAlertLevelColor(level) {
  const map = {
    'CRITICAL': 'danger',
    'WARNING': 'warning',
    'INFO': 'info'
  }
  return map[level] || 'info'
}

function getStatusColor(status) {
  const map = {
    'ACTIVE': 'danger',
    'ACKNOWLEDGED': 'warning',
    'RESOLVED': 'success'
  }
  return map[status] || 'info'
}

function getStatusText(status) {
  const map = {
    'ACTIVE': '活跃',
    'ACKNOWLEDGED': '已确认',
    'RESOLVED': '已解决'
  }
  return map[status] || status
}

function formatTime(timeStr) {
  if (!timeStr) return '--'
  return new Date(timeStr).toLocaleString('zh-CN')
}

async function loadRules() {
  try {
    const data = await alertApi.getRuleList()
    rulesList.value = Array.isArray(data) ? data : []
  } catch (e) {
    ElMessage.error('加载规则列表失败')
  }
}

async function loadRecords() {
  try {
    const data = await alertApi.getRecordList({
      page: currentPage.value,
      size: pageSize.value,
      alertType: filterType.value,
      alertLevel: filterLevel.value,
      status: filterStatus.value
    })
    if (data) {
      recordsList.value = data.records || []
      totalRecords.value = data.total || 0
    }
  } catch (e) {
    ElMessage.error('加载告警记录失败')
  }
}

function handleSizeChange(val) {
  pageSize.value = val
  loadRecords()
}

function handleCurrentChange(val) {
  currentPage.value = val
  loadRecords()
}

function openCreateDialog() {
  isEdit.value = false
  ruleForm.value = {
    id: null,
    ruleName: '',
    ruleType: '',
    targetType: '',
    targetValue: '',
    threshold: 0,
    operator: '>',
    duration: 60,
    notificationType: 'DINGTALK',
    notificationTarget: '',
    description: '',
    enabled: 1
  }
  dialogVisible.value = true
}

function editRule(row) {
  isEdit.value = true
  ruleForm.value = { ...row }
  dialogVisible.value = true
}

async function saveRule() {
  if (!ruleFormRef.value) return
  try {
    await ruleFormRef.value.validate()
    if (isEdit.value) {
      await alertApi.updateRule(ruleForm.value)
      ElMessage.success('更新成功')
    } else {
      await alertApi.createRule(ruleForm.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadRules()
  } catch (e) {
    if (e !== false) {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
    }
  }
}

async function toggleRule(row) {
  try {
    await alertApi.toggleRule(row.id)
    ElMessage.success('操作成功')
    loadRules()
  } catch (e) {
    row.enabled = row.enabled === 1 ? 0 : 1
    ElMessage.error('操作失败')
  }
}

async function deleteRule(row) {
  try {
    await ElMessageBox.confirm('确定要删除该规则吗？', '提示', {
      type: 'warning'
    })
    await alertApi.deleteRule(row.id)
    ElMessage.success('删除成功')
    loadRules()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

async function acknowledgeAlert(row) {
  try {
    await alertApi.acknowledge(row.id, 'operator')
    ElMessage.success('确认成功')
    loadRecords()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function resolveAlert(row) {
  try {
    await alertApi.resolve(row.id)
    ElMessage.success('已标记为解决')
    loadRecords()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

function showAlertDetail(row) {
  currentAlert.value = row
  detailVisible.value = true
}

onMounted(() => {
  loadRules()
  loadRecords()
})
</script>

<style scoped>
.alert-rules-page {
  height: 100%;
  padding: 20px;
  background: #f5f7fa;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.filter-actions {
  display: flex;
  align-items: center;
}

.rules-card, .records-card {
  margin-bottom: 20px;
}

.condition-text {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.alert-value {
  font-weight: 600;
  color: #f56c6c;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

:deep(.el-tabs__header) {
  margin-bottom: 20px;
}
</style>
