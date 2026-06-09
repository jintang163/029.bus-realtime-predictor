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
