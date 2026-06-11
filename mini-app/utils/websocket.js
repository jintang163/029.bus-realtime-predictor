const app = getApp();

class WebSocketService {
  constructor() {
    this.wsUrl = app.globalData.wsBaseUrl;
    this.socketTask = null;
    this.isConnected = false;
    this.subscription = null;
    this.listeners = {};
    this.reconnectTimer = null;
    this.heartbeatTimer = null;
    this.reconnectCount = 0;
    this.maxReconnectCount = 5;
  }

  connect() {
    return new Promise((resolve, reject) => {
      if (this.isConnected && this.socketTask) {
        resolve(this.socketTask);
        return;
      }

      console.log('正在连接WebSocket:', this.wsUrl);

      try {
        this.socketTask = wx.connectSocket({
          url: this.wsUrl,
          header: {
            'content-type': 'application/json'
          },
          success: () => {
            console.log('WebSocket连接请求已发送');
          },
          fail: (err) => {
            console.error('WebSocket连接失败:', err);
            this.handleReconnect();
            reject(err);
          }
        });

        this.socketTask.onOpen(() => {
          console.log('WebSocket连接已建立');
          this.isConnected = true;
          this.reconnectCount = 0;
          this.startHeartbeat();
          this.emit('open');
          resolve(this.socketTask);

          if (this.subscription) {
            this.subscribe(this.subscription.line, this.subscription.station, this.subscription.direction);
          }
        });

        this.socketTask.onMessage((res) => {
          try {
            const data = JSON.parse(res.data);
            this.emit('message', data);
            if (data.type === 'eta') {
              this.emit('eta', data.data);
            } else if (data.type === 'error') {
              console.warn('WebSocket服务端错误:', data.message);
            }
          } catch (e) {
            if (res.data === 'pong') {
              return;
            }
            console.warn('解析WebSocket消息失败:', e);
          }
        });

        this.socketTask.onError((err) => {
          console.error('WebSocket错误:', err);
          this.isConnected = false;
          this.emit('error', err);
          this.handleReconnect();
        });

        this.socketTask.onClose((res) => {
          console.log('WebSocket连接关闭:', res);
          this.isConnected = false;
          this.stopHeartbeat();
          this.emit('close', res);
          this.handleReconnect();
        });
      } catch (e) {
        console.error('创建WebSocket异常:', e);
        reject(e);
      }
    });
  }

  subscribe(line, station, direction = 'up') {
    this.subscription = { line, station, direction };

    if (!this.isConnected) {
      return this.connect().then(() => {
        this.sendSubscribe(line, station, direction);
      });
    }

    this.sendSubscribe(line, station, direction);
    return Promise.resolve();
  }

  sendSubscribe(line, station, direction) {
    const msg = {
      action: 'subscribe',
      line: line,
      station: station,
      direction: direction
    };
    this.send(msg);
  }

  unsubscribe() {
    this.subscription = null;
    if (this.isConnected) {
      this.send({ action: 'unsubscribe' });
    }
  }

  refresh() {
    if (this.isConnected) {
      this.send({ action: 'refresh' });
    }
  }

  send(data) {
    if (!this.isConnected || !this.socketTask) {
      console.warn('WebSocket未连接，无法发送消息');
      return false;
    }

    try {
      this.socketTask.send({
        data: JSON.stringify(data),
        success: () => {},
        fail: (err) => {
          console.error('发送WebSocket消息失败:', err);
        }
      });
      return true;
    } catch (e) {
      console.error('发送WebSocket异常:', e);
      return false;
    }
  }

  startHeartbeat() {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      if (this.isConnected) {
        this.socketTask.send({
          data: 'ping',
          success: () => {},
          fail: () => {}
        });
      }
    }, 30000);
  }

  stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  handleReconnect() {
    if (this.reconnectCount >= this.maxReconnectCount) {
      console.warn('WebSocket重连次数达到上限');
      return;
    }

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }

    this.reconnectCount++;
    const delay = Math.min(1000 * Math.pow(2, this.reconnectCount), 30000);

    console.log(`将在 ${delay}ms 后进行第 ${this.reconnectCount} 次重连`);

    this.reconnectTimer = setTimeout(() => {
      this.connect().catch((err) => {
        console.warn('WebSocket重连失败:', err);
      });
    }, delay);
  }

  disconnect() {
    this.stopHeartbeat();
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    if (this.socketTask) {
      this.socketTask.close({});
      this.socketTask = null;
    }
    this.isConnected = false;
    this.subscription = null;
  }

  on(event, callback) {
    if (!this.listeners[event]) {
      this.listeners[event] = [];
    }
    this.listeners[event].push(callback);
  }

  off(event, callback) {
    if (!this.listeners[event]) return;
    this.listeners[event] = this.listeners[event].filter(cb => cb !== callback);
  }

  emit(event, data) {
    if (!this.listeners[event]) return;
    this.listeners[event].forEach(callback => {
      try {
        callback(data);
      } catch (e) {
        console.error(`事件监听器异常 [${event}]:`, e);
      }
    });
  }
}

module.exports = new WebSocketService();
