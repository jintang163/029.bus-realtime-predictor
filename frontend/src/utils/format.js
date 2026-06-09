export function formatTime(intTime) {
  if (intTime == null) return '--'
  const str = String(intTime).padStart(4, '0')
  return str.substring(0, 2) + ':' + str.substring(2)
}

export function formatDistance(meters) {
  if (meters == null) return '--'
  if (meters < 1000) return meters.toFixed(0) + 'm'
  return (meters / 1000).toFixed(1) + 'km'
}

export function formatSeconds(seconds) {
  if (seconds == null) return '--'
  if (seconds < 60) return seconds + '秒'
  const min = Math.floor(seconds / 60)
  const sec = seconds % 60
  return min + '分' + sec + '秒'
}

export function statusTag(status) {
  const map = { 0: '离线', 1: '在线', 2: '停运', 3: 'GPS信号丢失' }
  const typeMap = { 0: 'danger', 1: 'success', 2: 'warning', 3: 'info' }
  return { text: map[status] || '未知', type: typeMap[status] || 'info' }
}

export function congestionLevel(factor) {
  if (factor == null) return { text: '--', type: 'info' }
  if (factor < 1.2) return { text: '畅通', type: 'success' }
  if (factor < 1.8) return { text: '缓行', type: 'warning' }
  return { text: '拥堵', type: 'danger' }
}
