function formatTime(timestamp) {
  if (!timestamp) return '';
  const date = new Date(timestamp);
  const hours = date.getHours().toString().padStart(2, '0');
  const minutes = date.getMinutes().toString().padStart(2, '0');
  return `${hours}:${minutes}`;
}

function formatCountdown(seconds) {
  if (seconds == null || seconds < 0) return '--';
  if (seconds === 0) return '到站';
  if (seconds < 60) return `${seconds}秒`;
  const minutes = Math.floor(seconds / 60);
  const secs = seconds % 60;
  if (minutes < 60) {
    return secs > 0 ? `${minutes}分${secs}秒` : `${minutes}分钟`;
  }
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return `${hours}小时${mins}分`;
}

function formatMinutes(seconds) {
  if (seconds == null || seconds < 0) return '--';
  if (seconds === 0) return 0;
  return Math.ceil(seconds / 60);
}

function formatDistance(meters) {
  if (meters == null || meters < 0) return '--';
  if (meters < 1000) {
    return `${Math.round(meters)}米`;
  }
  return `${(meters / 1000).toFixed(1)}公里`;
}

function formatSpeed(ms) {
  if (ms == null || ms < 0) return '--';
  return `${(ms * 3.6).toFixed(0)}km/h`;
}

function getCrowdLevelText(level) {
  switch (level) {
    case 1: return '空';
    case 2: return '适中';
    case 3: return '拥挤';
    default: return '适中';
  }
}

function getCrowdLevelClass(level) {
  switch (level) {
    case 1: return 'tag-green';
    case 2: return 'tag-yellow';
    case 3: return 'tag-red';
    default: return 'tag-yellow';
  }
}

function getCrowdIcon(level) {
  switch (level) {
    case 1: return '○○○';
    case 2: return '●○○';
    case 3: return '●●●';
    default: return '●○○';
  }
}

function getDistanceText(stationsAway) {
  if (stationsAway == null) return '';
  if (stationsAway === 0) return '即将到站';
  if (stationsAway === 1) return '还有1站';
  return `还有${stationsAway}站`;
}

function debounce(fn, delay) {
  let timer = null;
  return function() {
    const context = this;
    const args = arguments;
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      fn.apply(context, args);
    }, delay);
  };
}

function throttle(fn, interval) {
  let last = 0;
  return function() {
    const context = this;
    const args = arguments;
    const now = Date.now();
    if (now - last >= interval) {
      last = now;
      fn.apply(context, args);
    }
  };
}

module.exports = {
  formatTime,
  formatCountdown,
  formatMinutes,
  formatDistance,
  formatSpeed,
  getCrowdLevelText,
  getCrowdLevelClass,
  getCrowdIcon,
  getDistanceText,
  debounce,
  throttle
};
