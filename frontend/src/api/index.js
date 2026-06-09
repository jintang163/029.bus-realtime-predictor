import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  timeout: 15000
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res.data
  },
  (error) => {
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export const vehicleApi = {
  getOnlineList: () => request.get('/api/vehicle/online'),
  getPosition: (vehicleId) => request.get(`/api/vehicle/position/${vehicleId}`),
  getStatus: (vehicleId) => request.get(`/api/vehicle/status/${vehicleId}`),
  getOnlineCount: () => request.get('/api/vehicle/online/count'),
  getPrediction: (vehicleId, routeId) => request.get(`/api/vehicle/prediction/${vehicleId}`, { params: { routeId } }),
  getTrafficSegment: (segmentId) => request.get(`/api/traffic/segment/${segmentId}/speed`)
}

export const lineApi = {
  list: () => request.get('/api/route/line/list'),
  getById: (lineId) => request.get(`/api/route/line/${lineId}`),
  create: (data) => request.post('/api/route/line', data),
  update: (data) => request.put('/api/route/line', data),
  delete: (lineId) => request.delete(`/api/route/line/${lineId}`),
  getStations: (lineId) => request.get(`/api/route/line/${lineId}/stations`),
  saveStations: (lineId, data) => request.post(`/api/route/line/${lineId}/stations`, data),
  refreshCache: () => request.post('/api/route/line/cache/refresh'),
  refreshLineCache: (lineId) => request.post(`/api/route/line/cache/refresh/${lineId}`)
}

export const stationApi = {
  list: () => request.get('/api/route/station/list'),
  getById: (stationId) => request.get(`/api/route/station/${stationId}`),
  create: (data) => request.post('/api/route/station', data),
  update: (data) => request.put('/api/route/station', data),
  delete: (stationId) => request.delete(`/api/route/station/${stationId}`),
  findNearby: (longitude, latitude, radius = 500, limit = 20) =>
    request.get('/api/route/station/nearby', { params: { longitude, latitude, radius, limit } })
}

export const scheduleApi = {
  list: (lineId, date) => request.get('/api/route/schedule/list', { params: { lineId, date } }),
  create: (data) => request.post('/api/route/schedule', data),
  delete: (lineId, date) => request.delete('/api/route/schedule', { params: { lineId, date } }),
  importExcel: (file, lineId, scheduleDate) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('lineId', lineId)
    formData.append('scheduleDate', scheduleDate)
    return request.post('/api/route/schedule/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}

export const stopboardApi = {
  getStationInfo: (stationId) => request.get(`/api/stopboard/${stationId}/info`),
  getStationLines: (stationId) => request.get(`/api/stopboard/${stationId}/lines`)
}
