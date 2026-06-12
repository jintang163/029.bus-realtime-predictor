package com.bus.predictor.webapi.controller;

import com.bus.predictor.dal.mapper.PredictionDeviationMapper;
import com.bus.predictor.dal.redis.VehiclePositionRedisDao;
import com.bus.predictor.traffic.model.RoadSegmentManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final VehiclePositionRedisDao vehiclePositionRedisDao;
    private final RoadSegmentManager roadSegmentManager;
    private final MeterRegistry meterRegistry;
    private final PredictionDeviationMapper deviationMapper;

    @Autowired
    public MonitorController(VehiclePositionRedisDao vehiclePositionRedisDao,
                             RoadSegmentManager roadSegmentManager,
                             MeterRegistry meterRegistry,
                             PredictionDeviationMapper deviationMapper) {
        this.vehiclePositionRedisDao = vehiclePositionRedisDao;
        this.roadSegmentManager = roadSegmentManager;
        this.meterRegistry = meterRegistry;
        this.deviationMapper = deviationMapper;
    }

    @GetMapping("/system")
    public Result<Map<String, Object>> getSystemMetrics() {
        Map<String, Object> result = new HashMap<>();

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        Map<String, Object> cpu = new HashMap<>();
        cpu.put("availableProcessors", osBean.getAvailableProcessors());
        cpu.put("systemLoadAverage", osBean.getSystemLoadAverage());
        cpu.put("processCpuLoad", getProcessCpuLoad(osBean));
        result.put("cpu", cpu);

        Map<String, Object> memory = new HashMap<>();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed();
        memory.put("heapUsed", heapUsed);
        memory.put("heapMax", heapMax);
        memory.put("heapUsedPercent", heapMax > 0 ? (heapUsed * 100.0 / heapMax) : 0);
        memory.put("nonHeapUsed", nonHeapUsed);
        memory.put("heapUsedMB", Math.round(heapUsed / 1024.0 / 1024.0));
        memory.put("heapMaxMB", Math.round(heapMax / 1024.0 / 1024.0));
        result.put("memory", memory);

        Map<String, Object> threads = new HashMap<>();
        threads.put("threadCount", threadBean.getThreadCount());
        threads.put("peakThreadCount", threadBean.getPeakThreadCount());
        threads.put("daemonThreadCount", threadBean.getDaemonThreadCount());
        result.put("threads", threads);

        Runtime runtime = Runtime.getRuntime();
        result.put("jvmUptime", ManagementFactory.getRuntimeMXBean().getUptime());

        return Result.success(result);
    }

    @GetMapping("/business")
    public Result<Map<String, Object>> getBusinessMetrics() {
        Map<String, Object> result = new HashMap<>();

        Set<String> onlineVehicles = vehiclePositionRedisDao.getOnlineVehicleIds();
        int onlineCount = onlineVehicles != null ? onlineVehicles.size() : 0;

        List<Map<String, Object>> segments = roadSegmentManager.getAllSegmentsWithSpeed();
        int segmentCount = segments != null ? segments.size() : 0;

        int smoothCount = 0, slowCount = 0, congestedCount = 0;
        if (segments != null) {
            for (Map<String, Object> seg : segments) {
                Object cf = seg.get("congestionFactor");
                if (cf != null) {
                    double congestionFactor = ((Number) cf).doubleValue();
                    if (congestionFactor < 1.2) smoothCount++;
                    else if (congestionFactor < 1.8) slowCount++;
                    else congestedCount++;
                }
            }
        }

        result.put("onlineVehicleCount", onlineCount);
        result.put("totalVehicleCount", 50);
        result.put("onlineRate", onlineCount * 100.0 / 50);
        result.put("segmentCount", segmentCount);
        result.put("smoothSegmentCount", smoothCount);
        result.put("slowSegmentCount", slowCount);
        result.put("congestedSegmentCount", congestedCount);

        return Result.success(result);
    }

    @GetMapping("/api-stats")
    public Result<Map<String, Object>> getApiStats() {
        Map<String, Object> result = new HashMap<>();

        List<Timer> timers = meterRegistry.getTimers().stream().toList();
        double totalRequests = 0;
        double totalResponseTime = 0;

        for (Timer timer : timers) {
            if (timer.getId().getName().startsWith("http.server.requests")) {
                long count = timer.count();
                totalRequests += count;
                totalResponseTime += timer.mean(TimeUnit.MILLISECONDS) * count;
            }
        }

        double avgResponseTime = totalRequests > 0 ? totalResponseTime / totalRequests : 0;

        double qps = 0;
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        if (uptimeMs > 0) {
            qps = totalRequests / (uptimeMs / 1000.0);
        }

        result.put("totalRequests", (long) totalRequests);
        result.put("avgResponseTimeMs", Math.round(avgResponseTime * 100) / 100.0);
        result.put("qps", Math.round(qps * 100) / 100.0);

        return Result.success(result);
    }

    @GetMapping("/api-response-distribution")
    public Result<Map<String, Object>> getApiResponseDistribution() {
        Map<String, Object> result = new HashMap<>();

        String[] buckets = {"0-50ms", "50-100ms", "100-200ms", "200-500ms", "500ms-1s", "1-3s", ">3s"};
        double[] bucketBounds = {0, 50, 100, 200, 500, 1000, 3000};
        long[] bucketCounts = new long[buckets.length];

        List<Timer> timers = meterRegistry.getTimers().stream().toList();
        for (Timer timer : timers) {
            if (!timer.getId().getName().startsWith("http.server.requests")) continue;
            String uri = timer.getId().getTag("uri");
            if (uri != null && uri.startsWith("/actuator")) continue;

            long count = timer.count();
            double meanMs = timer.mean(TimeUnit.MILLISECONDS);

            int bucketIdx = bucketBounds.length - 1;
            for (int i = bucketBounds.length - 1; i >= 0; i--) {
                if (meanMs >= bucketBounds[i]) {
                    bucketIdx = i;
                    break;
                }
            }
            bucketCounts[bucketIdx] += count;
        }

        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        for (int i = 0; i < buckets.length; i++) {
            labels.add(buckets[i]);
            values.add(bucketCounts[i]);
        }

        result.put("labels", labels);
        result.put("values", values);

        double avgMs = 0, p95Ms = 0, p99Ms = 0;
        long totalCount = 0;
        double totalTime = 0;
        for (Timer timer : timers) {
            if (!timer.getId().getName().startsWith("http.server.requests")) continue;
            String uri = timer.getId().getTag("uri");
            if (uri != null && uri.startsWith("/actuator")) continue;
            long c = timer.count();
            totalCount += c;
            totalTime += timer.mean(TimeUnit.MILLISECONDS) * c;
        }
        if (totalCount > 0) avgMs = totalTime / totalCount;
        result.put("avgMs", Math.round(avgMs * 100) / 100.0);
        result.put("p95Ms", Math.round(p95Ms * 100) / 100.0);
        result.put("p99Ms", Math.round(p99Ms * 100) / 100.0);

        return Result.success(result);
    }

    @GetMapping("/deviation-distribution")
    public Result<Map<String, Object>> getDeviationDistribution() {
        Map<String, Object> result = new HashMap<>();

        String startTime = LocalDateTime.now().minusHours(1)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<Map<String, Object>> hourlyData = deviationMapper.findHourlyAccuracy(
                LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );

        List<String> labels = new ArrayList<>();
        List<Double> avgDeviations = new ArrayList<>();
        List<Double> accuracyRates = new ArrayList<>();

        for (int i = 0; i < 12; i++) {
            int minutesAgo = (11 - i) * 5;
            labels.add(minutesAgo + "分钟前");
            avgDeviations.add(0.0);
            accuracyRates.add(0.0);
        }

        if (hourlyData != null && !hourlyData.isEmpty()) {
            int currentHour = LocalDateTime.now().getHour();
            for (Map<String, Object> row : hourlyData) {
                int hour = ((Number) row.get("hour_of_day")).intValue();
                long count = ((Number) row.get("total_count")).longValue();
                long accurate = row.get("accurate_count") != null
                        ? ((Number) row.get("accurate_count")).longValue() : 0;
                double avgDev = row.get("avg_deviation_rate") != null
                        ? ((Number) row.get("avg_deviation_rate")).doubleValue() : 0.0;

                int diff = currentHour - hour;
                if (diff >= 0 && diff < 12) {
                    int idx = 11 - diff;
                    avgDeviations.set(idx, Math.round(avgDev * 100 * 100) / 100.0);
                    accuracyRates.set(idx, count > 0 ? Math.round(accurate * 10000.0 / count) / 100.0 : 0.0);
                }
            }
        }

        result.put("labels", labels);
        result.put("avgDeviations", avgDeviations);
        result.put("accuracyRates", accuracyRates);

        return Result.success(result);
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> getMonitorOverview() {
        Map<String, Object> result = new HashMap<>();
        result.put("system", getSystemMetrics().getData());
        result.put("business", getBusinessMetrics().getData());
        result.put("api", getApiStats().getData());
        return Result.success(result);
    }

    private double getProcessCpuLoad(OperatingSystemMXBean osBean) {
        try {
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                return ((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuLoad() * 100;
            }
        } catch (Exception e) {
        }
        return -1;
    }
}
