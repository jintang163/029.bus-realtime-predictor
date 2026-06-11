const api = require('../../utils/api.js');
const ws = require('../../utils/websocket.js');
const fmt = require('../../utils/format.js');

Page({
  data: {
    lineCode: '5',
    stationName: '人民广场',
    direction: 'up',
    directionText: '上行',
    etaData: null,
    vehicles: [],
    loading: true,
    error: null,
    lastUpdateTime: null,
    fromCache: false,
    useWebSocket: false,
    wsConnected: false,
    countdownTimers: []
  },

  onLoad(options) {
    console.log('ETA页面加载', options);

    if (options.line) {
      this.setData({ lineCode: options.line });
    }
    if (options.station) {
      this.setData({ stationName: decodeURIComponent(options.station) });
    }
    if (options.direction) {
      this.setData({
        direction: options.direction,
        directionText: options.direction === 'down' ? '下行' : '上行'
      });
    }

    this.loadEta();
  },

  onShow() {
    if (this.data.useWebSocket && this.data.etaData) {
      this.startWebSocket();
    }
  },

  onUnload() {
    this.clearCountdownTimers();
    this.stopWebSocket();
  },

  onPullDownRefresh() {
    this.refresh(true);
  },

  loadEta(forceRefresh = false) {
    this.setData({ loading: true, error: null });

    api.getEta(this.data.lineCode, this.data.stationName, this.data.direction, forceRefresh)
      .then(data => {
        this.processEtaData(data);
      })
      .catch(err => {
        console.error('加载ETA失败:', err);
        this.setData({
          loading: false,
          error: err.message || '加载失败，请稍后重试'
        });
      })
      .finally(() => {
        wx.stopPullDownRefresh();
      });
  },

  processEtaData(data) {
    if (!data) {
      this.setData({
        loading: false,
        vehicles: [],
        etaData: null
      });
      return;
    }

    const now = Date.now();
    const vehicles = (data.vehicles || []).map(v => ({
      ...v,
      countdownSeconds: v.estimatedSeconds || 0,
      countdownText: fmt.formatCountdown(v.estimatedSeconds),
      distanceText: fmt.getDistanceText(v.distanceStationsAway),
      distanceMetersText: fmt.formatDistance(v.distanceMeters),
      speedText: fmt.formatSpeed(v.currentSpeed),
      crowdClass: fmt.getCrowdLevelClass(v.crowdLevel),
      crowdIcon: fmt.getCrowdIcon(v.crowdLevel)
    }));

    this.setData({
      etaData: data,
      vehicles: vehicles,
      fromCache: !!data.fromCache,
      lastUpdateTime: now,
      loading: false
    });

    this.startCountdown();

    wx.setNavigationBarTitle({
      title: `${data.lineCode || this.data.lineCode}路 - ${data.stationName || this.data.stationName}`
    });
  },

  startCountdown() {
    this.clearCountdownTimers();

    const timer = setInterval(() => {
      const vehicles = this.data.vehicles.map(v => {
        if (v.countdownSeconds > 0) {
          const newSeconds = v.countdownSeconds - 1;
          return {
            ...v,
            countdownSeconds: newSeconds,
            countdownText: fmt.formatCountdown(newSeconds)
          };
        }
        return v;
      });

      this.setData({ vehicles });
    }, 1000);

    this.data.countdownTimers.push(timer);
  },

  clearCountdownTimers() {
    this.data.countdownTimers.forEach(t => clearInterval(t));
    this.setData({ countdownTimers: [] });
  },

  refresh(force = false) {
    if (this.data.useWebSocket && this.data.wsConnected) {
      ws.refresh();
      wx.showToast({ title: '已请求最新数据', icon: 'none' });
      return;
    }
    this.loadEta(force);
    wx.vibrateShort({ type: 'light' });
  },

  toggleDirection() {
    const newDir = this.data.direction === 'up' ? 'down' : 'up';
    this.setData({
      direction: newDir,
      directionText: newDir === 'down' ? '下行' : '上行'
    });
    this.loadEta(false);
  },

  toggleWebSocket() {
    const useWs = !this.data.useWebSocket;
    this.setData({ useWebSocket: useWs });

    if (useWs) {
      this.startWebSocket();
      wx.showToast({ title: '已开启实时推送', icon: 'success' });
    } else {
      this.stopWebSocket();
      wx.showToast({ title: '已关闭实时推送', icon: 'none' });
    }
  },

  startWebSocket() {
    ws.on('eta', this.onWsEta.bind(this));
    ws.on('open', () => {
      this.setData({ wsConnected: true });
    });
    ws.on('close', () => {
      this.setData({ wsConnected: false });
    });

    ws.subscribe(this.data.lineCode, this.data.stationName, this.data.direction)
      .catch(err => {
        console.warn('WebSocket订阅失败:', err);
        wx.showToast({ title: '实时连接失败，使用轮询模式', icon: 'none' });
      });
  },

  stopWebSocket() {
    ws.off('eta', this.onWsEta.bind(this));
    ws.unsubscribe();
    this.setData({ wsConnected: false });
  },

  onWsEta(data) {
    console.log('收到WebSocket ETA数据');
    this.processEtaData(data);
  },

  goSearch() {
    wx.navigateTo({ url: '/pages/search/search' });
  },

  copyVehicleId(e) {
    const id = e.currentTarget.dataset.id;
    wx.setClipboardData({
      data: id,
      success: () => {
        wx.showToast({ title: '车辆ID已复制', icon: 'success' });
      }
    });
  },

  shareAppMessage() {
    return {
      title: `${this.data.lineCode}路 ${this.data.stationName} 到站预报`,
      path: `/pages/eta/eta?line=${this.data.lineCode}&station=${encodeURIComponent(this.data.stationName)}&direction=${this.data.direction}`
    };
  }
});
