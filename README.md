# 🚌 公交实时到站预报系统 (Bus Realtime Predictor)

结合GPS定位、路况拥堵模型、电子站牌与线路管理，准确预测公交到站剩余时间的全栈系统。

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
                                              (实时+静态)  (轨迹+线路)  (清洗数据)
                                                    │
                                    ┌───────────────┼───────────────┐
                                    ↓                               ↓
                            ┌──────────────┐              ┌──────────────────┐
                            │  Web API      │  WebSocket  │  线路管理 API      │
                            │  :8080        │ ──────────→ │  :8083            │
                            └──────────────┘              └──────────────────┘
                                    │                               │
                                    ↓                               ↓
                            ┌──────────────┐              ┌──────────────────┐
                            │  实时监控大屏  │              │  线路管理后台      │
                            │  :3000       │              │  :3001           │
                            └──────────────┘              └──────────────────┘
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
| `route-management` | 8083 | 线路/站点/排班管理 + 电子站牌API + Redis缓存 + Excel排班导入 |
| `simulator` | - | 车载终端模拟器，模拟多辆车GPS上报 |
| `web-api` | 8080 | 实时监控API + WebSocket实时推送 |
| `frontend` | 3000 | Vue3+ElementPlus+Leaflet统一管理前端（实时监控+线路管理+电子站牌） |

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

7. Web API → WebSocket → Frontend(:3000)
   每2秒推送在线车辆实时位置

8. Route-Management → MySQL + Redis
   线路/站点/排班CRUD → Redis缓存(line:{lineId}:stations)
   电子站牌API → 查询站点信息及途经线路
   Excel排班批量导入(Apache POI)

9. Route-Admin(:3001) → Route-Management(:8083)
   Vue3 + Element Plus + Leaflet地图管理后台
   地图拖拽标注站点坐标 → 保存至MySQL(SPATIAL INDEX)
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

# 访问实时监控大屏
open http://localhost:3000

# 访问线路管理后台
open http://localhost:3001
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

# 终端3: 启动线路管理服务
java -jar route-management/target/route-management-1.0.0-SNAPSHOT.jar

# 终端4: 启动Web API
java -jar web-api/target/web-api-1.0.0-SNAPSHOT.jar

# 终端5: 启动终端模拟器
java -jar simulator/target/simulator-1.0.0-SNAPSHOT.jar
```

**5. 访问前端**

```bash
# 实时监控大屏
open frontend/index.html

# 线路管理后台
open route-admin/index.html
```

## API 接口

### 实时监控 (Web API :8080)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/vehicle/online` | 获取所有在线车辆位置 |
| GET | `/api/vehicle/position/{vehicleId}` | 获取指定车辆最新位置 |
| GET | `/api/vehicle/status/{vehicleId}` | 获取车辆在线状态 |
| GET | `/api/vehicle/online/count` | 获取在线车辆数量 |
| GET | `/api/vehicle/prediction/{vehicleId}?routeId=R001` | 到站时间预测 |
| GET | `/api/traffic/segment/{segmentId}/speed` | 路段速度与拥堵系数 |
| WS | `ws://localhost:8080/ws/vehicle` | 实时推送车辆位置（每2秒） |

### 线路管理 (Route Management :8083)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/route/line/list` | 获取所有线路 |
| GET | `/api/route/line/{lineId}` | 获取线路详情 |
| POST | `/api/route/line` | 新增线路 |
| PUT | `/api/route/line` | 修改线路 |
| DELETE | `/api/route/line/{lineId}` | 删除线路 |
| GET | `/api/route/line/{lineId}/stations` | 获取线路站点列表(含详情) |
| POST | `/api/route/line/{lineId}/stations` | 保存线路站点排序 |
| POST | `/api/route/line/cache/refresh` | 刷新所有线路Redis缓存 |
| GET | `/api/route/station/list` | 获取所有站点 |
| GET | `/api/route/station/{stationId}` | 获取站点详情 |
| POST | `/api/route/station` | 新增站点 |
| PUT | `/api/route/station` | 修改站点 |
| DELETE | `/api/route/station/{stationId}` | 删除站点 |
| GET | `/api/route/station/nearby?longitude=&latitude=&radius=500&limit=20` | 附近站点查询(空间索引) |
| GET | `/api/route/schedule/list?lineId=L001&date=2026-06-09` | 查询排班 |
| POST | `/api/route/schedule` | 新增排班 |
| DELETE | `/api/route/schedule?lineId=L001&date=2026-06-09` | 删除排班 |
| POST | `/api/route/schedule/import` | Excel批量导入排班 |
| GET | `/api/stopboard/{stationId}/lines` | 电子站牌-途经线路 |
| GET | `/api/stopboard/{stationId}/info` | 电子站牌-站点信息 |

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

### LineEntity（线路）

```json
{
  "lineId": "L001",
  "lineName": "1路",
  "lineCode": "001",
  "direction": 0,
  "startStation": "火车站",
  "endStation": "科技园",
  "totalDistance": 12.5,
  "stationCount": 5,
  "firstBusTime": 600,
  "lastBusTime": 2200,
  "intervalMinutes": 8,
  "status": 1
}
```

### StationEntity（站点）

```json
{
  "stationId": "S001",
  "stationName": "火车站",
  "stationCode": "STA001",
  "longitude": 116.407526,
  "latitude": 39.904030,
  "district": "东城区",
  "street": "北京站前街",
  "stationType": 1
}
```

### VehicleStatus 枚举

| 值 | 状态 | 说明 |
|----|------|------|
| 0 | OFFLINE | 离线 |
| 1 | ONLINE | 在线 |
| 2 | STOPPED | 停运 |
| 3 | GPS_LOST | GPS信号丢失 |

## 数据库表结构

| 表名 | 说明 |
|------|------|
| `t_line` | 线路信息 |
| `t_station` | 站点信息(含SPATIAL INDEX) |
| `t_line_station` | 线路-站点关联(站点排序+站间距离) |
| `t_schedule` | 车辆排班 |
| `t_vehicle_info` | 车辆信息 |
| `t_trajectory_record` | GPS历史轨迹 |
| `t_road_segment` | 路段信息 |
| `t_route_station` | 路线站点(旧) |

## Redis缓存策略

| Key Pattern | TTL | 说明 |
|-------------|-----|------|
| `bus:vehicle:pos:{vehicleId}` | 5min | 车辆最新位置 |
| `bus:vehicle:status:{vehicleId}` | 5min | 车辆状态 |
| `bus:vehicle:online` | - | 在线车辆集合 |
| `bus:road:speed:{geoHash}` | 10min | 路段实时速度 |
| `bus:road:congestion:{geoHash}` | 10min | 路段拥堵系数 |
| `line:{lineId}:stations` | 24h | 线路站点静态数据 |

## 拥堵模型

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
| 数据库 | MySQL 8.0 + MyBatis-Plus 3.5 + SPATIAL INDEX |
| 线路管理 | Spring Boot 2.7 + Apache POI 5.2 |
| Web API | Spring Boot 2.7 + WebSocket |
| 实时大屏 | HTML5 + CSS3 + Canvas + WebSocket |
| 管理后台 | Vue 3 + Element Plus + Leaflet |
