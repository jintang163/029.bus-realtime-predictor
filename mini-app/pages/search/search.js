const api = require('../../utils/api.js');

Page({
  data: {
    keyword: '',
    searchType: 'line',
    lines: [
      { line: '1', name: '1路', desc: '火车站 → 市政府', stations: 24 },
      { line: '5', name: '5路', desc: '人民广场 → 大学城', stations: 32 },
      { line: '12', name: '12路', desc: '机场 → 商业街', stations: 18 },
      { line: '28', name: '28路', desc: '科技园 → 中心医院', stations: 26 },
      { line: '95', name: '95路', desc: '体育馆 → 会展中心', stations: 21 },
      { line: '101', name: '101路', desc: '东站 → 西站', stations: 45 }
    ],
    stations: [
      '人民广场', '火车站', '市政府', '大学城', '商业街',
      '中心医院', '科技园', '体育馆', '会展中心', '机场'
    ],
    filteredLines: [],
    filteredStations: [],
    history: [],
    showHistory: true,
    showResults: false,
    direction: 'up',
    selectedLine: null,
    selectedStation: null,
    lineStations: []
  },

  onLoad() {
    this.loadHistory();
    this.setData({
      filteredLines: this.data.lines,
      filteredStations: this.data.stations
    });
  },

  loadHistory() {
    try {
      const history = wx.getStorageSync('search_history') || [];
      this.setData({ history: history.slice(0, 10) });
    } catch (e) {}
  },

  saveHistory(item) {
    try {
      let history = wx.getStorageSync('search_history') || [];
      history = history.filter(h => !(h.type === item.type && h.value === item.value));
      history.unshift(item);
      history = history.slice(0, 10);
      wx.setStorageSync('search_history', history);
      this.setData({ history });
    } catch (e) {}
  },

  onInput(e) {
    const keyword = e.detail.value.trim();
    this.setData({
      keyword,
      showResults: keyword.length > 0,
      showHistory: keyword.length === 0
    });

    if (keyword.length === 0) {
      this.setData({
        filteredLines: this.data.lines,
        filteredStations: this.data.stations
      });
      return;
    }

    const filteredLines = this.data.lines.filter(l =>
      l.line.includes(keyword) ||
      (l.name && l.name.includes(keyword)) ||
      (l.desc && l.desc.includes(keyword))
    );

    const filteredStations = this.data.stations.filter(s => s.includes(keyword));

    this.setData({ filteredLines, filteredStations });
  },

  switchType(e) {
    this.setData({ searchType: e.currentTarget.dataset.type });
  },

  selectLine(e) {
    const line = e.currentTarget.dataset.line;
    this.setData({
      selectedLine: line,
      keyword: line.line,
      lineStations: this.generateLineStations(line),
      selectedStation: null
    });
    this.saveHistory({ type: 'line', value: line.line, label: line.name });
  },

  generateLineStations(line) {
    const stations = [];
    const count = line.stations || 20;
    for (let i = 1; i <= count; i++) {
      stations.push({
        order: i,
        name: `站点${i}`
      });
    }
    stations[0].name = line.desc.split('→')[0].trim();
    stations[stations.length - 1].name = line.desc.split('→')[1]?.trim() || '终点站';
    const midIdx = Math.floor(count / 2);
    stations[midIdx].name = '人民广场';
    return stations;
  },

  selectStation(e) {
    const station = e.currentTarget.dataset.station;
    if (this.data.selectedLine) {
      this.setData({ selectedStation: station, keyword: station });
      this.doSearch();
    } else {
      this.setData({ keyword: station, selectedStation: station });
    }
    this.saveHistory({ type: 'station', value: station });
  },

  toggleDirection() {
    this.setData({
      direction: this.data.direction === 'up' ? 'down' : 'up'
    });
  },

  doSearch() {
    const line = this.data.selectedLine ? this.data.selectedLine.line : this.data.keyword;
    const station = this.data.selectedStation || this.data.keyword;

    if (!line || !station) {
      wx.showToast({ title: '请选择线路和站点', icon: 'none' });
      return;
    }

    wx.navigateTo({
      url: `/pages/eta/eta?line=${line}&station=${encodeURIComponent(station)}&direction=${this.data.direction}`
    });
  },

  clickHistory(e) {
    const item = e.currentTarget.dataset.item;
    if (item.type === 'line') {
      this.setData({
        keyword: item.value,
        searchType: 'line'
      });
      this.onInput({ detail: { value: item.value } });
    } else {
      this.setData({
        keyword: item.value,
        searchType: 'station'
      });
      this.onInput({ detail: { value: item.value } });
    }
  },

  clearHistory() {
    wx.removeStorageSync('search_history');
    this.setData({ history: [] });
  },

  clearInput() {
    this.setData({
      keyword: '',
      selectedLine: null,
      selectedStation: null,
      showResults: false,
      showHistory: true,
      filteredLines: this.data.lines,
      filteredStations: this.data.stations
    });
  }
});
