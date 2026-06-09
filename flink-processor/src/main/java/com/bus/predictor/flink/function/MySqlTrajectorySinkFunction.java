package com.bus.predictor.flink.function;

import com.bus.predictor.common.model.GpsData;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MySqlTrajectorySinkFunction extends RichSinkFunction<GpsData> {

    private static final Logger log = LoggerFactory.getLogger(MySqlTrajectorySinkFunction.class);

    private static final String INSERT_SQL =
            "INSERT INTO t_trajectory_record (vehicle_id, longitude, latitude, speed, direction, gps_time, create_time) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private final String jdbcUrl;
    private final String username;
    private final String password;

    private transient Connection connection;
    private transient ExecutorService executorService;
    private transient BlockingQueue<GpsData> queue;

    public MySqlTrajectorySinkFunction(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection(jdbcUrl, username, password);
        connection.setAutoCommit(false);

        queue = new ArrayBlockingQueue<>(10000);
        executorService = Executors.newFixedThreadPool(2);
        executorService.submit(this::batchInsertLoop);

        log.info("MySQL connection established: {}", jdbcUrl);
    }

    @Override
    public void invoke(GpsData gps, Context context) {
        try {
            queue.offer(gps);
        } catch (Exception e) {
            log.error("Queue offer failed for vehicle={}", gps.getVehicleId(), e);
        }
    }

    private void batchInsertLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                PreparedStatement ps = connection.prepareStatement(INSERT_SQL);
                int count = 0;
                int batchSize = 500;

                while (count < batchSize) {
                    GpsData gps = queue.poll();
                    if (gps == null) {
                        Thread.sleep(100);
                        continue;
                    }

                    LocalDateTime gpsTime = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(gps.getTimestamp()), ZoneId.systemDefault());

                    ps.setString(1, gps.getVehicleId());
                    ps.setDouble(2, gps.getLongitude());
                    ps.setDouble(3, gps.getLatitude());
                    ps.setDouble(4, gps.getSpeed() != null ? gps.getSpeed() : 0);
                    ps.setDouble(5, gps.getDirection() != null ? gps.getDirection() : 0);
                    ps.setTimestamp(6, Timestamp.valueOf(gpsTime));
                    ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
                    ps.addBatch();
                    count++;
                }

                ps.executeBatch();
                connection.commit();
                ps.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Batch insert failed", e);
                try {
                    connection.rollback();
                } catch (Exception ex) {
                    log.error("Rollback failed", ex);
                }
            }
        }
    }

    @Override
    public void close() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                log.error("Close MySQL connection failed", e);
            }
        }
    }
}
