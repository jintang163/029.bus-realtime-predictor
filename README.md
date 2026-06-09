# 🚌 公交实时到站预报系统 (Bus Realtime Predictor)

结合GPS定位、路况拥堵模型，准确预测公交到站剩余时间的全栈系统。

## 系统架构

```
┌──────────────┐    TCP     ┌──────────────┐   Kafka    ┌─────────────────────┐
│  车载终端     │ ────────→  │  Netty网关    │ ────────→  │  流处理器            │
│  (Simulator) │  :9090     │  (Gateway)    │ bus-gps-raw│  (Standalone/Flink) │
└──────────────┘            └──────────────┘            └──────┬──────────────┘
                                                                │
                                                    ┌───────────┼───────────┐
                                                    ↓           ↓           ↓
                                                 Redis       MySQL      Kafka
                                              (实时位置)   (历史轨迹)  (清洗数据)
                                                    │
                                                    ↓
                                            ┌──────────────┐    WebSocket    ┌──────────────┐
                                            │  Web API      │ ─────────────→  │  前端管理平台  │
                                            │  :8080        │    :8080/ws     │  :3000       │
                                            └──────────────┘                 └──────────────┘
```

## 模块说明

| 模块 | 端口 | 说明 |
|------|------|------|
| `common` | - | 公共模型、常量、工具类 |
| `dal` | - | 数据访问层（MySQL Mapper + Redis DAO） |
| `traffic-model` | - | 路况拥堵模型、到站预测算法 |
| `gateway` | 9090/8081 | Netty TCP网关，接收车载终端GPS上报 |
| `standalone-processor` | 8082 | Spring Boot流处理器（Kafka消费→清洗→Redis/MySQL） |
| `flink-processor` | - | Flink流处理器（生产环境替代standalone-processor） |
| `simulator` | - | 车载终端模拟器，模拟多辆车GPS上报 |
| `web-api` | 8080 | Web管理API + WebSocket实时推送 |
| `frontend` | 3000 | 前端管理仪表盘（暗色主题） |

## 数据流全链路

```
1. Simulator → TCP连接 → Gateway:9090
   车载终端每秒采集: vehicleId, longitude, latitude, speed, direction, timestamp, satelliteCount, hdop

2. Gateway → Kafka Topic "bus-gps-raw"
   二进制协议解码 → JSON序列化 → Kafka Producer发送

3. Standalone-Processor ← Kafka Consumer
   消费GPS数据 → GpsValidator校验 → 清洗异常点

4. Processor → Redis
   Key: bus:vehicle:pos:{vehicleId} → VehiclePosition JSON (TTL 5min)
   Key: bus:vehicle:status:{vehicleId} → status code (TTL 5min)
   Key: bus:vehicle:online → Set<vehicleId>
   Key: bus:road:speed:{geoHash} → 当前速度 (TTL 10min)
   Key: bus:road:congestion:{geoHash} → 拥堵系数 (TTL 10min)

5. Processor → MySQL t_trajectory_record
   异步批量写入历史轨迹

6. Web API ← Redis读取
   REST接口查询车辆位置/状态/预测

7. Web API → WebSocket → Frontend
   每2秒推送在线车辆实时位置
```

## 快速启动

### 前置条件

- JDK 11+
- Maven 3.6+
- Docker & Docker Compose（可选，用于基础设施）

### 方式一：Docker Compose 一键启动

```bash
# 启动所有服务
docker-compose up -d

# 访问前端
open http://localhost:3000
```

### 方式二：本地开发启动

**1. 启动基础设施（Kafka + Redis + MySQL）**

```bash
docker-compose up -d zookeeper kafka redis mysql
```

**2. 初始化数据库**

```bash
# MySQL自动执行 sql/init.sql，或手动执行：
mysql -u root -p < sql/init.sql
```

**3. 编译项目**

```bash
mvn clean package -DskipTests
```

**4. 启动各服务（按顺序）**

```bash
# 终端1: 启动网关
java -jar gateway/target/gateway-1.0.0-SNAPSHOT.jar

# 终端2: 启动流处理器
java -jar standalone-processor/target/standalone-processor-1.0.0-SNAPSHOT.jar

# 终端3: 启动Web API
java -jar web-api/target/web-api-1.0.0-SNAPSHOT.jar

# 终端4: 启动终端模拟器
java -jar simulator/target/simulator-1.0.0-SNAPSHOT.jar
```

**5. 访问前端**

```bash
# 直接用浏览器打开
open frontend/index.html
```

## API 接口

### 车辆监控

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/vehicle/online` | 获取所有在线车辆位置 |
| GET | `/api/vehicle/position/{vehicleId}` | 获取指定车辆最新位置 |
| GET | `/api/vehicle/status/{vehicleId}` | 获取车辆在线状态 |
| GET | `/api/vehicle/online/count` | 获取在线车辆数量 |
| GET | `/api/vehicle/prediction/{vehicleId}?routeId=R001` | 到站时间预测 |

### 交通信息

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/traffic/segment/{segmentId}/speed` | 路段速度与拥堵系数 |

### WebSocket

| 路径 | 说明 |
|------|------|
| `ws://localhost:8080/ws/vehicle` | 实时推送车辆位置（每2秒） |

### 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": ...
}
```

## 核心数据模型

### VehiclePosition（车辆实时位置）

```json
{
  "vehicleId": "V001",
  "longitude": 116.407526,
  "latitude": 39.904030,
  "speed": 8.5,
  "direction": 45.0,
  "geoHash": "wx4g0s",
  "gpsTime": 1718000000000,
  "receiveTime": 1718000000050,
  "status": 1
}
```

### ArrivalPrediction（到站预测）

```json
{
  "vehicleId": "V001",
  "routeId": "R001",
  "stationId": "S001",
  "stationName": "火车站",
  "distanceToStation": 1500.0,
  "estimatedSeconds": 180,
  "congestionFactor": 1.5,
  "currentSpeed": 8.33,
  "predictTime": 1718000180000,
  "gpsTime": 1718000000000
}
```

### VehicleStatus 枚举

| 值 | 状态 | 说明 |
|----|------|------|
| 0 | OFFLINE | 离线 |
| 1 | ONLINE | 在线 |
| 2 | STOPPED | 停运 |
| 3 | GPS_LOST | GPS信号丢失 |

## 拥堵模型

拥堵系数计算公式：

```
congestion = realTimeFactor × 0.5 + timeOfDayFactor × 0.3 + weatherFactor × 0.2
```

- **realTimeFactor**: 基于Redis中路段实时速度，自由流速度/当前速度
- **timeOfDayFactor**: 时段因子（早高峰7-9点=2.5, 晚高峰17-19点=2.5, 平峰=1.0）
- **weatherFactor**: 天气因子（预留，当前默认1.0）

到站预测：

```
estimatedSeconds = Σ(distance_to_station / (currentSpeed / congestionFactor)) + stopPenalty × stationCount
```

## TCP协议格式

车载终端与网关之间的二进制协议：

```
+----------+----------+----------+------------------+
| Length   | MsgType  | Body...                    |
| 4 bytes  | 1 byte   | (Length-1) bytes           |
+----------+----------+----------+------------------+

MsgType:
  0x01 = GPS数据
  0x02 = 心跳
  0x03 = 认证
  0x04 = 批量GPS

GPS Body:
  vehicleIdLen(1B) + vehicleId(32B max) + longitude(8B double) + latitude(8B double)
  + speed(8B double) + direction(8B double) + timestamp(8B long) + satelliteCount(1B) + hdop(1B)
```

## 技术栈

| 层级 | 技术 |
|------|------|
| 车载终端 | 北斗/GPS双模 + 4G/5G + 嵌入式Linux/Android |
| TCP网关 | Netty 4.1 + Spring Boot 2.7 |
| 消息队列 | Apache Kafka 3.4 |
| 流处理 | Flink 1.17 / Spring Boot (standalone) |
| 缓存 | Redis 7 (Jedis 4.3) |
| 数据库 | MySQL 8.0 + MyBatis-Plus 3.5 |
| Web API | Spring Boot 2.7 + WebSocket |
| 前端 | HTML5 + CSS3 + Canvas + WebSocket |
