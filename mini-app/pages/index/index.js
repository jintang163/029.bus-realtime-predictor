const api = require('../../utils/api.js');
const app = getApp();

Page({
  data: {
    hotRoutes: [
      { line: '1', name: '1路', desc: '火车站 ↔ 市政府' },
      { line: '5', name: '5路', desc: '人民广场 ↔ 大学城' },
      { line: '12', name: '12路', desc: '机场 ↔ 商业街' },
      { line: '28', name: '28路', desc: '科技园 ↔ 中心医院' },
      { line: '95', name: '95路', desc: '体育馆 ↔ 会展中心' }
    ],
    hotStations: [
      '人民广场', '火车站', '市政府', '大学城', '商业街', '中心医院'
    ],
    nearbyStations: [],
    banners: [
      { title: '智能公交预报', desc: '实时到站 精准预测', color: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' },
      { title: '绿色出行 优选公交', desc: '节能减排 从我做起', color: 'linear-gradient(135deg, #11998e 0%, #38ef7d 100%)' }
    ],
    loadingLocation: false
  },

  onLoad() {
    this.loadNearbyStations();
  },

  onShow() {},

  onPullDownRefresh() {
    this.loadNearbyStations();
    wx.stopPullDownRefresh();
  },

  async loadNearbyStations() {
    this.setData({ loadingLocation: true });

    try {
      const loc = await app.getLocation();
      console.log('当前位置:', loc);

      try {
        const stations = await api.getNearbyStations(
          loc.longitude, loc.latitude, 1500, 6
        );
        if (stations && stations.length > 0) {
          this.setData({ nearbyStations: stations });
        }
      } catch (e) {
        console.log('加载附近站点失败，使用默认数据');
      }
    } catch (e) {
      console.log('获取位置失败');
    }

    this.setData({ loadingLocation: false });
  },

  goEta(e) {
    const { line, station, direction } = e.currentTarget.dataset;
    const url = `/pages/eta/eta?line=${line || '5'}&station=${encodeURIComponent(station || '人民广场')}&direction=${direction || 'up'}`;
    wx.navigateTo({ url });
  },

  goSearch() {
    wx.switchTab({ url: '/pages/search/search' });
  },

  goStopBoard() {
    wx.switchTab({ url: '/pages/stopboard/stopboard' });
  },

  onBannerTap(e) {
    const idx = e.currentTarget.dataset.idx;
    if (idx === 0) {
      this.goEta({ currentTarget: { dataset: { line: '5', station: '人民广场' } } });
    } else {
      this.goStopBoard();
    }
  }
});
