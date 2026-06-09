package com.bus.predictor.simulator.schedule;

import com.bus.predictor.simulator.client.TcpClient;
import com.bus.predictor.simulator.model.VehicleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class GpsReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(GpsReportScheduler.class);

    private final List<TcpClient> clients = new CopyOnWriteArrayList<>();
    private final Random random = new Random();

    public void addClient(TcpClient client) {
        clients.add(client);
    }

    public void removeClient(TcpClient client) {
        clients.remove(client);
    }

    @Scheduled(fixedRateString = "${simulator.report-interval-ms:1000}")
    public void reportGpsData() {
        for (TcpClient client : clients) {
            if (!client.isActive()) {
                continue;
            }

            VehicleConfig config = client.getConfig();
            double lng = config.getStartLongitude() + (random.nextDouble() - 0.5) * 0.001;
            double lat = config.getStartLatitude() + (random.nextDouble() - 0.5) * 0.001;
            double speed = config.getAvgSpeed() + (random.nextDouble() - 0.5) * 2;
            if (speed < 0) speed = 0;
            double direction = random.nextDouble() * 360;
            long timestamp = System.currentTimeMillis();
            int satellites = 8 + random.nextInt(5);
            int hdop = random.nextInt(3) + 1;

            client.sendGps(lng, lat, speed, direction, timestamp, satellites, hdop);

            if (log.isDebugEnabled()) {
                log.debug("[{}] Reported GPS: lng={}, lat={}, speed={}",
                        config.getVehicleId(),
                        String.format("%.6f", lng),
                        String.format("%.6f", lat),
                        String.format("%.2f", speed));
            }
        }
    }

    @Scheduled(fixedRate = 30000)
    public void sendHeartbeats() {
        for (TcpClient client : clients) {
            if (client.isActive()) {
                client.sendHeartbeat();
            }
        }
    }
}
