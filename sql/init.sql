CREATE DATABASE IF NOT EXISTS bus_predictor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE bus_predictor;

CREATE TABLE IF NOT EXISTS t_trajectory_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id VARCHAR(32) NOT NULL,
    longitude DOUBLE NOT NULL,
    latitude DOUBLE NOT NULL,
    speed DOUBLE DEFAULT 0,
    direction DOUBLE DEFAULT 0,
    gps_time DATETIME NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_vehicle_id (vehicle_id),
    INDEX idx_gps_time (gps_time),
    INDEX idx_vehicle_gps (vehicle_id, gps_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_vehicle_info (
    vehicle_id VARCHAR(32) PRIMARY KEY,
    plate_number VARCHAR(20),
    route_id VARCHAR(32),
    driver_name VARCHAR(50),
    status INT DEFAULT 0,
    longitude DOUBLE,
    latitude DOUBLE,
    speed DOUBLE DEFAULT 0,
    last_gps_time DATETIME,
    last_online_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_route_id (route_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_road_segment (
    segment_id VARCHAR(32) PRIMARY KEY,
    start_node VARCHAR(64),
    end_node VARCHAR(64),
    start_lng DOUBLE NOT NULL,
    start_lat DOUBLE NOT NULL,
    end_lng DOUBLE NOT NULL,
    end_lat DOUBLE NOT NULL,
    length DOUBLE DEFAULT 0,
    free_flow_speed DOUBLE DEFAULT 13.89,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_start_node (start_node),
    INDEX idx_end_node (end_node)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_route_station (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id VARCHAR(32) NOT NULL,
    station_id VARCHAR(32) NOT NULL,
    station_name VARCHAR(100),
    station_order INT NOT NULL,
    longitude DOUBLE NOT NULL,
    latitude DOUBLE NOT NULL,
    distance_to_next DOUBLE DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_route_id (route_id),
    INDEX idx_station_id (station_id),
    UNIQUE KEY uk_route_station_order (route_id, station_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_line (
    line_id VARCHAR(32) PRIMARY KEY,
    line_name VARCHAR(100) NOT NULL,
    line_code VARCHAR(20),
    direction INT DEFAULT 0,
    start_station VARCHAR(100),
    end_station VARCHAR(100),
    total_distance DOUBLE DEFAULT 0,
    station_count INT DEFAULT 0,
    first_bus_time INT DEFAULT 600,
    last_bus_time INT DEFAULT 2200,
    interval_minutes INT DEFAULT 10,
    status INT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_line_code (line_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_station (
    station_id VARCHAR(32) PRIMARY KEY,
    station_name VARCHAR(100) NOT NULL,
    station_code VARCHAR(20),
    longitude DOUBLE NOT NULL,
    latitude DOUBLE NOT NULL,
    district VARCHAR(50),
    street VARCHAR(100),
    station_type INT DEFAULT 0,
    remark VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    SPATIAL INDEX idx_location ((ST_GeomFromText(CONCAT('POINT(', longitude, ' ', latitude, ')'), 4326)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_line_station (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    line_id VARCHAR(32) NOT NULL,
    station_id VARCHAR(32) NOT NULL,
    station_order INT NOT NULL,
    distance_from_start DOUBLE DEFAULT 0,
    distance_to_next DOUBLE DEFAULT 0,
    estimated_seconds INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_line_id (line_id),
    INDEX idx_station_id (station_id),
    UNIQUE KEY uk_line_station_order (line_id, station_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    line_id VARCHAR(32) NOT NULL,
    vehicle_id VARCHAR(32) NOT NULL,
    driver_name VARCHAR(50),
    schedule_date DATE NOT NULL,
    departure_time INT NOT NULL,
    trip_index INT DEFAULT 1,
    direction INT DEFAULT 0,
    status INT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_line_date (line_id, schedule_date),
    INDEX idx_vehicle_date (vehicle_id, schedule_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_segment_speed_history (
    id BIGINT PRIMARY KEY,
    segment_id VARCHAR(64) NOT NULL,
    line_id VARCHAR(32),
    speed DOUBLE,
    congestion_factor DOUBLE,
    speed_source INT DEFAULT 0,
    record_time DATETIME NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_segment_time (segment_id, record_time),
    INDEX idx_line_id (line_id),
    INDEX idx_record_time (record_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO t_line (line_id, line_name, line_code, direction, start_station, end_station, total_distance, station_count, first_bus_time, last_bus_time, interval_minutes, status) VALUES
('L001', '1路', '001', 0, '火车站', '科技园', 12.5, 5, 600, 2200, 8, 1),
('L002', '2路', '002', 0, '西客站', '东湖公园', 18.2, 8, 600, 2200, 10, 1),
('L003', '快速1号', 'K001', 0, '市政府', '高铁站', 22.0, 6, 630, 2100, 5, 1);

INSERT IGNORE INTO t_station (station_id, station_name, station_code, longitude, latitude, district, street, station_type) VALUES
('S001', '火车站', 'STA001', 116.407526, 39.904030, '东城区', '北京站前街', 1),
('S002', '中山路', 'STA002', 116.410526, 39.908030, '东城区', '中山路', 0),
('S003', '人民广场', 'STA003', 116.415526, 39.912030, '东城区', '长安街', 1),
('S004', '市政府', 'STA004', 116.420526, 39.916030, '西城区', '府右街', 0),
('S005', '科技园', 'STA005', 116.425526, 39.920030, '海淀区', '中关村大街', 1),
('S006', '西客站', 'STA006', 116.381000, 39.925000, '丰台区', '莲花池东路', 1),
('S007', '复兴门', 'STA007', 116.388000, 39.918000, '西城区', '复兴门外大街', 0),
('S008', '东湖公园', 'STA008', 116.405000, 39.940000, '朝阳区', '东湖路', 1),
('S009', '高铁站', 'STA009', 116.378000, 39.865000, '丰台区', '站前街', 1),
('S010', '三元桥', 'STA010', 116.455000, 39.960000, '朝阳区', '三环', 0);

INSERT IGNORE INTO t_line_station (line_id, station_id, station_order, distance_from_start, distance_to_next, estimated_seconds) VALUES
('L001', 'S001', 1, 0, 850, 120),
('L001', 'S002', 2, 850, 920, 130),
('L001', 'S003', 3, 1770, 1100, 160),
('L001', 'S004', 4, 2870, 780, 110),
('L001', 'S005', 5, 3650, 0, 0),
('L002', 'S006', 1, 0, 1200, 170),
('L002', 'S007', 2, 1200, 980, 140),
('L002', 'S003', 3, 2180, 1100, 155),
('L002', 'S008', 4, 3280, 0, 0),
('L003', 'S004', 1, 0, 2200, 180),
('L003', 'S003', 2, 2200, 3500, 260),
('L003', 'S009', 3, 5700, 0, 0);

CREATE TABLE IF NOT EXISTS t_prediction_deviation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id VARCHAR(32) NOT NULL,
    route_id VARCHAR(32) NOT NULL,
    segment_id VARCHAR(64) NOT NULL,
    station_id VARCHAR(32),
    predicted_seconds INT NOT NULL,
    actual_seconds INT NOT NULL,
    deviation_seconds INT NOT NULL,
    deviation_rate DOUBLE NOT NULL,
    predicted_speed DOUBLE,
    actual_speed DOUBLE,
    predict_time DATETIME NOT NULL,
    arrival_time DATETIME NOT NULL,
    hour_of_day INT NOT NULL,
    day_of_week INT NOT NULL,
    is_accurate TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_vehicle_time (vehicle_id, arrival_time),
    INDEX idx_segment_time (segment_id, arrival_time),
    INDEX idx_route_time (route_id, arrival_time),
    INDEX idx_hour_day (hour_of_day, day_of_week)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_segment_baseline_speed (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    segment_id VARCHAR(64) NOT NULL,
    line_id VARCHAR(32),
    day_of_week INT NOT NULL,
    hour_of_day INT NOT NULL,
    baseline_speed DOUBLE NOT NULL,
    baseline_congestion DOUBLE DEFAULT 1.0,
    sample_count INT DEFAULT 0,
    std_dev DOUBLE DEFAULT 0,
    speed_source INT DEFAULT 2,
    train_time DATETIME NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_segment_day_hour (segment_id, day_of_week, hour_of_day),
    INDEX idx_segment_id (segment_id),
    INDEX idx_line_id (line_id),
    INDEX idx_day_hour (day_of_week, hour_of_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_alert_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    rule_type VARCHAR(32) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_value VARCHAR(100),
    threshold DOUBLE NOT NULL,
    operator VARCHAR(10) NOT NULL,
    duration INT DEFAULT 60,
    notification_type VARCHAR(32) NOT NULL,
    notification_target VARCHAR(500),
    enabled TINYINT DEFAULT 1,
    description VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_rule_type (rule_type),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_alert_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    rule_name VARCHAR(100),
    alert_type VARCHAR(32) NOT NULL,
    alert_level VARCHAR(16) NOT NULL,
    target_id VARCHAR(64),
    target_name VARCHAR(200),
    alert_value DOUBLE,
    threshold DOUBLE,
    operator VARCHAR(10),
    message TEXT,
    status VARCHAR(16) DEFAULT 'ACTIVE',
    acknowledged_by VARCHAR(64),
    acknowledged_time DATETIME,
    resolved_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_alert_type (alert_type),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time),
    INDEX idx_rule_id (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO t_alert_rule (rule_name, rule_type, target_type, threshold, operator, duration, notification_type, notification_target, enabled, description) VALUES
('预测偏差过大', 'PREDICTION_DEVIATION', 'LINE', 300.0, '>', 60, 'DINGTALK', '', 1, '单线路预测偏差超过5分钟持续60秒告警'),
('车辆离线', 'VEHICLE_OFFLINE', 'VEHICLE', 300.0, '>', 120, 'DINGTALK', '', 1, '车辆GPS数据超过5分钟未更新告警'),
('路段严重拥堵', 'CONGESTION', 'SEGMENT', 3.0, '>', 180, 'DINGTALK', '', 1, '路段拥堵系数超过3.0持续3分钟告警'),
('API响应过慢', 'API_RESPONSE', 'API', 3000.0, '>', 60, 'DINGTALK', '', 1, 'API响应时间超过3秒持续60秒告警'),
('设备在线率过低', 'ONLINE_RATE', 'SYSTEM', 80.0, '<', 300, 'DINGTALK', '', 1, '设备在线率低于80%持续5分钟告警');
