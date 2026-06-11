const app = getApp();

class ApiService {
  constructor() {
    this.baseUrl = app.globalData.apiBaseUrl;
  }

  request(url, method = 'GET', data = {}, header = {}) {
    return new Promise((resolve, reject) => {
      wx.request({
        url: this.baseUrl + url,
        method: method,
        data: data,
        header: Object.assign({
          'Content-Type': 'application/json'
        }, header),
        success: (res) => {
          if (res.statusCode === 200) {
            if (res.data && res.data.code === 0) {
              resolve(res.data.data);
            } else if (res.data && res.data.code === 429) {
              wx.showToast({
                title: '请求过于频繁，请稍后再试',
                icon: 'none'
              });
              reject(res.data);
            } else {
              reject(res.data);
            }
          } else {
            reject({
              code: res.statusCode,
              message: '网络请求失败'
            });
          }
        },
        fail: (err) => {
          console.error('API请求失败:', url, err);
          reject({
            code: -1,
            message: '网络连接失败'
          });
        }
      });
    });
  }

  getEta(line, station, direction = 'up', forceRefresh = false) {
    return this.request('/eta', 'GET', {
      line: line,
      station: station,
      direction: direction,
      forceRefresh: forceRefresh
    });
  }

  getEtaHealth() {
    return this.request('/eta/health', 'GET');
  }

  getNearbyStations(longitude, latitude, radius = 1000, limit = 10) {
    return this.request('/traffic/stations/nearby', 'GET', {
      longitude: longitude,
      latitude: latitude,
      radius: radius,
      limit: limit
    });
  }
}

module.exports = new ApiService();
