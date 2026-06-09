<template>
  <div class="station-management">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>站点管理</span>
          <div>
            <el-button type="primary" @click="handleAdd">新增站点</el-button>
            <el-button @click="nearbyDialogVisible = true">附近查询</el-button>
          </div>
        </div>
      </template>

      <el-table
        :data="stations"
        stripe
        highlight-current-row
        @row-click="handleRowClick"
        v-loading="loading"
      >
        <el-table-column prop="stationId" label="站点ID" width="100" />
        <el-table-column prop="stationName" label="站点名称" width="160" />
        <el-table-column prop="stationCode" label="站点编码" width="120" />
        <el-table-column prop="longitude" label="经度" width="120" />
        <el-table-column prop="latitude" label="纬度" width="120" />
        <el-table-column prop="district" label="区域" width="100" />
        <el-table-column prop="street" label="街道" width="120" />
        <el-table-column prop="stationType" label="站点类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.stationType === 1 ? 'danger' : 'info'">
              {{ row.stationType === 1 ? '枢纽' : '普通' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该站点？" @confirm="handleDelete(row.stationId)">
              <template #reference>
                <el-button link type="danger" @click.stop>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="map-card">
      <template #header>
        <span>站点地图</span>
      </template>
      <div id="station-map" class="map-container"></div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑站点' : '新增站点'"
      width="680px"
      destroy-on-close
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="站点名称" prop="stationName">
              <el-input v-model="form.stationName" placeholder="请输入站点名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="站点编码" prop="stationCode">
              <el-input v-model="form.stationCode" placeholder="请输入站点编码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="经度" prop="longitude">
              <el-input-number v-model="form.longitude" :precision="6" :step="0.001" :controls="false" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纬度" prop="latitude">
              <el-input-number v-model="form.latitude" :precision="6" :step="0.001" :controls="false" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="区域" prop="district">
              <el-input v-model="form.district" placeholder="请输入区域" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="街道" prop="street">
              <el-input v-model="form.street" placeholder="请输入街道" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="站点类型" prop="stationType">
              <el-select v-model="form.stationType" placeholder="请选择" style="width:100%">
                <el-option :value="0" label="普通" />
                <el-option :value="1" label="枢纽" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
        <el-form-item label="坐标拾取">
          <div class="tip-text">点击地图选取坐标</div>
          <div id="pick-map" class="pick-map"></div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="nearbyDialogVisible" title="附近站点查询" width="600px" destroy-on-close>
      <el-form :model="nearbyForm" label-width="90px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="经度">
              <el-input-number v-model="nearbyForm.longitude" :precision="6" :step="0.001" :controls="false" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纬度">
              <el-input-number v-model="nearbyForm.latitude" :precision="6" :step="0.001" :controls="false" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="半径(米)">
          <el-input-number v-model="nearbyForm.radius" :min="100" :step="100" style="width:200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleNearbyQuery" :loading="nearbyLoading">查询</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="nearbyResults" stripe v-if="nearbyResults.length > 0" class="nearby-table">
        <el-table-column prop="stationName" label="站点名称" />
        <el-table-column prop="stationCode" label="站点编码" />
        <el-table-column prop="longitude" label="经度" />
        <el-table-column prop="latitude" label="纬度" />
        <el-table-column prop="distance" label="距离(m)" width="100">
          <template #default="{ row }">
            {{ row.distance ? row.distance.toFixed(1) : '--' }}
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { stationApi } from '../api/index.js'
import L from 'leaflet'

const loading = ref(false)
const stations = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const defaultForm = {
  stationId: null,
  stationName: '',
  stationCode: '',
  longitude: 116.397428,
  latitude: 39.90923,
  district: '',
  street: '',
  stationType: 0,
  remark: ''
}
const form = reactive({ ...defaultForm })

const rules = {
  stationName: [{ required: true, message: '请输入站点名称', trigger: 'blur' }],
  stationCode: [{ required: true, message: '请输入站点编码', trigger: 'blur' }],
  longitude: [{ required: true, message: '请输入经度', trigger: 'blur' }],
  latitude: [{ required: true, message: '请输入纬度', trigger: 'blur' }]
}

const nearbyDialogVisible = ref(false)
const nearbyLoading = ref(false)
const nearbyResults = ref([])
const nearbyForm = reactive({
  longitude: 116.397428,
  latitude: 39.90923,
  radius: 500
})

let mainMap = null
let mainMarkerGroup = null
let pickMap = null
let pickMarker = null

const OSM_URL = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
const OSM_ATTR = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'

function initMainMap() {
  mainMap = L.map('station-map', {
    center: [39.90923, 116.397428],
    zoom: 12
  })
  L.tileLayer(OSM_URL, { attribution: OSM_ATTR }).addTo(mainMap)
  mainMarkerGroup = L.layerGroup().addTo(mainMap)
}

function renderMarkers() {
  if (!mainMarkerGroup) return
  mainMarkerGroup.clearLayers()
  stations.value.forEach(s => {
    if (s.latitude && s.longitude) {
      const marker = L.marker([s.latitude, s.longitude]).addTo(mainMarkerGroup)
      marker.bindPopup(`<b>${s.stationName}</b>`)
    }
  })
  if (stations.value.length > 0) {
    const bounds = stations.value
      .filter(s => s.latitude && s.longitude)
      .map(s => [s.latitude, s.longitude])
    if (bounds.length > 0) {
      mainMap.fitBounds(bounds, { padding: [30, 30] })
    }
  }
}

function initPickMap() {
  pickMap = L.map('pick-map', {
    center: [form.latitude, form.longitude],
    zoom: 14
  })
  L.tileLayer(OSM_URL, { attribution: OSM_ATTR }).addTo(pickMap)
  pickMarker = L.marker([form.latitude, form.longitude], { draggable: true }).addTo(pickMap)
  pickMarker.on('dragend', () => {
    const pos = pickMarker.getLatLng()
    form.longitude = Number(pos.lng.toFixed(6))
    form.latitude = Number(pos.lat.toFixed(6))
  })
  pickMap.on('click', (e) => {
    form.longitude = Number(e.latlng.lng.toFixed(6))
    form.latitude = Number(e.latlng.lat.toFixed(6))
    pickMarker.setLatLng(e.latlng)
  })
}

async function loadStations() {
  loading.value = true
  try {
    stations.value = await stationApi.list()
    renderMarkers()
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function handleRowClick(row) {
  if (row.latitude && row.longitude) {
    mainMap.setView([row.latitude, row.longitude], 16)
  }
}

function handleAdd() {
  isEdit.value = false
  Object.assign(form, { ...defaultForm })
  dialogVisible.value = true
  nextTick(() => {
    if (pickMap) {
      pickMap.remove()
      pickMap = null
    }
    nextTick(() => initPickMap())
  })
}

function handleEdit(row) {
  isEdit.value = true
  Object.assign(form, { ...row })
  dialogVisible.value = true
  nextTick(() => {
    if (pickMap) {
      pickMap.remove()
      pickMap = null
    }
    nextTick(() => initPickMap())
  })
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value) {
      await stationApi.update({ ...form })
      ElMessage.success('更新成功')
    } else {
      const { stationId, ...data } = form
      await stationApi.create(data)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadStations()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}

async function handleDelete(stationId) {
  try {
    await stationApi.delete(stationId)
    ElMessage.success('删除成功')
    await loadStations()
  } catch (e) {
    console.error(e)
  }
}

async function handleNearbyQuery() {
  nearbyLoading.value = true
  try {
    nearbyResults.value = await stationApi.findNearby(
      nearbyForm.longitude,
      nearbyForm.latitude,
      nearbyForm.radius
    )
  } catch (e) {
    console.error(e)
  } finally {
    nearbyLoading.value = false
  }
}

onMounted(() => {
  nextTick(() => {
    initMainMap()
    loadStations()
  })
})

onBeforeUnmount(() => {
  if (mainMap) {
    mainMap.remove()
    mainMap = null
  }
  if (pickMap) {
    pickMap.remove()
    pickMap = null
  }
})
</script>

<style scoped>
.station-management {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.map-card {
  margin-top: 0;
}

.map-container {
  height: 400px;
  width: 100%;
  border-radius: 4px;
}

.pick-map {
  height: 250px;
  width: 100%;
  border-radius: 4px;
  margin-top: 8px;
}

.tip-text {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.nearby-table {
  margin-top: 16px;
}
</style>
