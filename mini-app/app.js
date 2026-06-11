App({
  globalData: {
    apiBaseUrl: 'http://localhost:8080/api',
    wsBaseUrl: 'ws://localhost:8080/ws/eta',
    userInfo: null,
    currentLocation: null
  },

  onLaunch() {
    console.log('公交到站预报小程序启动');
    this.checkSystemInfo();
  },

  onShow() {
    console.log('小程序显示');
  },

  onHide() {
    console.log('小程序隐藏');
  },

  onError(msg) {
    console.error('小程序错误:', msg);
  },

  checkSystemInfo() {
    try {
      const sysInfo = wx.getSystemInfoSync();
      console.log('系统信息:', sysInfo.model, sysInfo.system);
    } catch (e) {
      console.warn('获取系统信息失败:', e);
    }
  },

  getLocation() {
    return new Promise((resolve, reject) => {
      if (this.globalData.currentLocation) {
        resolve(this.globalData.currentLocation);
        return;
      }
      wx.getLocation({
        type: 'gcj02',
        success: (res) => {
          this.globalData.currentLocation = res;
          resolve(res);
        },
        fail: (err) => {
          console.warn('获取位置失败:', err);
          reject(err);
        }
      });
    });
  }
});
