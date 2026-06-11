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
    displayVehicles: [],
    wsConnected: false,
    wsStatus: '未连接',
    currentTime: '',
    marqueeIndex: 0,
    currentIndex: 0,
    showSettings: false,
    quickLines: ['1', '5', '12', '28', '95'],
    quickStations: ['人民广场', '火车站', '市政府', '大学城', '商业街'],
    screenAwake: true
  },

  onLoad(options) {
    console.log('电子站牌页面加载');

    if (options.line) this.setData({ lineCode: options.line });
    if (options.station) this.setData({ stationName: decodeURIComponent(options.station || '人民广场') });
    if (options.direction) {
      this.setData({
        direction: options.direction,
        directionText: options.direction === 'down' ? '下行' : '上行'
      });
    }

    this.startClock();
    this.startMarquee();
    this.connectAndSubscribe();
  },

  onShow() {
    if (!this.data.wsConnected) {
      this.connectAndSubscribe();
    }
    this.setData({ screenAwake: true });
  },

  onHide() {
    this.setData({ screenAwake: false });
  },

  onUnload() {
    this.stopAllTimers();
    ws.off('eta', this.onWsEta.bind(this));
    ws.off('open', this.onWsOpen.bind(this));
    ws.off('close', this.onWsClose.bind(this));
    ws.unsubscribe();
  },

  stopAllTimers() {
    if (this.clockTimer) clearInterval(this.clockTimer);
    if (this.marqueeTimer) clearInterval(this.marqueeTimer);
    if (this.rotateTimer) clearInterval(this.rotateTimer);
    if (this.countdownTimer) clearInterval(this.countdownTimer);
  },

  startClock() {
    const updateTime = () => {
      const now = new Date();
      const h = now.getHours().toString().padStart(2, '0');
      const m = now.getMinutes().toString().padStart(2, '0');
      const s = now.getSeconds().toString().padStart(2, '0');
      this.setData({ currentTime: `${h}:${m}:${s}` });
    };
    updateTime();
    this.clockTimer = setInterval(updateTime, 1000);
  },

  startMarquee() {
    this.marqueeTimer = setInterval(() => {
      this.setData({ marqueeIndex: (this.data.marqueeIndex + 1) % 3 });
    }, 3000);
  },

  connectAndSubscribe() {
    this.setData({ wsStatus: '连接中...' });

    ws.on('eta', this.onWsEta.bind(this));
    ws.on('open', this.onWsOpen.bind(this));
    ws.on('close', this.onWsClose.bind(this));

    ws.subscribe(this.data.lineCode, this.data.stationName, this.data.direction)
      .then(() => {
        this.setData({ wsStatus: '已连接' });
      })
      .catch(err => {
        console.warn('WebSocket连接失败，降级使用HTTP轮询');
        this.setData({ wsStatus: '轮询模式' });
        this.startHttpPolling();
      });
  },

  onWsOpen() {
    this.setData({ wsConnected: true, wsStatus: '已连接' });
  },

  onWsClose() {
    this.setData({ wsConnected: false, wsStatus: '重连中...' });
  },

  onWsEta(data) {
    this.processEtaData(data);
  },

  startHttpPolling() {
    this.loadEta();
    this.rotateTimer = setInterval(() => {
      if (this.data.screenAwake && !this.data.wsConnected) {
        this.loadEta();
      }
    }, 15000);
  },

  loadEta() {
    api.getEta(this.data.lineCode, this.data.stationName, this.data.direction, false)
      .then(data => {
        this.processEtaData(data);
      })
      .catch(err => {
        console.error('HTTP加载ETA失败:', err);
      });
  },

  processEtaData(data) {
    if (!data || !data.vehicles) return;

    const vehicles = data.vehicles.map(v => ({
      ...v,
      countdownSeconds: v.estimatedSeconds || 0,
      countdownText: fmt.formatCountdown(v.estimatedSeconds),
      distanceText: fmt.getDistanceText(v.distanceStationsAway),
      distanceMetersText: fmt.formatDistance(v.distanceMeters),
      crowdClass: fmt.getCrowdLevelClass(v.crowdLevel),
      crowdIcon: fmt.getCrowdIcon(v.crowdLevel)
    }));

    this.setData({
      etaData: data,
      vehicles: vehicles,
      displayVehicles: vehicles.slice(0, 3)
    });

    if (!this.countdownTimer) {
      this.startCountdown();
    }

    wx.vibrateShort({ type: 'light' });
  },

  startCountdown() {
    this.countdownTimer = setInterval(() => {
      if (!this.data.screenAwake) return;

      const vehicles = this.data.vehicles.map(v => {
        if (v.countdownSeconds > 0) {
          const newSec = v.countdownSeconds - 1;
          return {
            ...v,
            countdownSeconds: newSec,
            countdownText: fmt.formatCountdown(newSec)
          };
        }
        return v;
      });

      this.setData({
        vehicles,
        displayVehicles: vehicles.slice(0, 3)
      });
    }, 1000);
  },

  toggleDirection() {
    const newDir = this.data.direction === 'up' ? 'down' : 'up';
    this.setData({
      direction: newDir,
      directionText: newDir === 'down' ? '下行' : '上行'
    });

    ws.unsubscribe();
    ws.subscribe(this.data.lineCode, this.data.stationName, newDir)
      .catch(() => this.loadEta());
  },

  selectQuickLine(e) {
    const line = e.currentTarget.dataset.line;
    this.setData({ lineCode: line, showSettings: false });

    ws.unsubscribe();
    ws.subscribe(line, this.data.stationName, this.data.direction)
      .catch(() => this.loadEta());

    wx.showToast({ title: `切换到 ${line}路`, icon: 'none' });
  },

  selectQuickStation(e) {
    const station = e.currentTarget.dataset.station;
    this.setData({ stationName: station, showSettings: false });

    ws.unsubscribe();
    ws.subscribe(this.data.lineCode, station, this.data.direction)
      .catch(() => this.loadEta());

    wx.showToast({ title: `切换到 ${station}`, icon: 'none' });
  },

  toggleSettings() {
    this.setData({ showSettings: !this.data.showSettings });
  },

  shareAppMessage() {
    return {
      title: `电子站牌 - ${this.data.lineCode}路 ${this.data.stationName}`,
      path: `/pages/stopboard/stopboard?line=${this.data.lineCode}&station=${encodeURIComponent(this.data.stationName)}&direction=${this.data.direction}`
    };
  }
});
