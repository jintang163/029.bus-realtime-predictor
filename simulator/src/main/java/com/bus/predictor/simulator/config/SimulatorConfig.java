package com.bus.predictor.simulator.config;

import com.bus.predictor.simulator.client.TcpClient;
import com.bus.predictor.simulator.model.VehicleConfig;
import com.bus.predictor.simulator.schedule.GpsReportScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableScheduling
public class SimulatorConfig {

    private static final Logger log = LoggerFactory.getLogger(SimulatorConfig.class);

    @Value("${simulator.gateway-host:localhost}")
    private String gatewayHost;

    @Value("${simulator.gateway-port:9090}")
    private int gatewayPort;

    @Value("${simulator.vehicle-count:5}")
    private int vehicleCount;

    @Bean
    public CommandLineRunner startSimulators(GpsReportScheduler scheduler) {
        return args -> {
            log.info("Starting {} vehicle simulators, connecting to {}:{}",
                    vehicleCount, gatewayHost, gatewayPort);

            List<VehicleConfig> configs = createVehicleConfigs(vehicleCount);

            for (VehicleConfig config : configs) {
                try {
                    TcpClient client = new TcpClient(gatewayHost, gatewayPort, config);
                    client.connect();
                    scheduler.addClient(client);
                    log.info("Vehicle [{}] ({}) simulator started", config.getVehicleId(), config.getPlateNumber());
                } catch (Exception e) {
                    log.error("Failed to start simulator for vehicle {}", config.getVehicleId(), e);
                }
            }
        };
    }

    private List<VehicleConfig> createVehicleConfigs(int count) {
        List<VehicleConfig> configs = new ArrayList<>();

        String[] plates = {"京A12345", "京B23456", "京C34567", "京D45678", "京E56789",
                "京F67890", "京G78901", "京H89012", "京J90123", "京K01234"};

        double[][] routes = {
                {116.407526, 39.904030, 116.425526, 39.920030},
                {116.381000, 39.925000, 116.405000, 39.940000},
                {116.420000, 39.890000, 116.440000, 39.910000},
                {116.360000, 39.910000, 116.390000, 39.930000},
                {116.410000, 39.880000, 116.430000, 39.900000},
        };

        for (int i = 0; i < Math.min(count, routes.length); i++) {
            configs.add(VehicleConfig.builder()
                    .vehicleId("V" + String.format("%03d", i + 1))
                    .plateNumber(plates[i % plates.length])
                    .routeId("R001")
                    .startLongitude(routes[i][0])
                    .startLatitude(routes[i][1])
                    .endLongitude(routes[i][2])
                    .endLatitude(routes[i][3])
                    .avgSpeed(8.0 + i * 1.5)
                    .reportIntervalMs(1000)
                    .simulateRoute(true)
                    .build());
        }

        for (int i = routes.length; i < count; i++) {
            configs.add(VehicleConfig.builder()
                    .vehicleId("V" + String.format("%03d", i + 1))
                    .plateNumber(plates[i % plates.length])
                    .routeId("R002")
                    .startLongitude(116.38 + i * 0.005)
                    .startLatitude(39.91 + i * 0.003)
                    .endLongitude(116.42 + i * 0.005)
                    .endLatitude(39.93 + i * 0.003)
                    .avgSpeed(7.0 + (i % 5) * 1.5)
                    .reportIntervalMs(1000)
                    .simulateRoute(true)
                    .build());
        }

        return configs;
    }
}
