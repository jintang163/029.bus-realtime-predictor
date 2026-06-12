package com.bus.predictor.webapi.controller;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final VehiclePositionRedisDao vehiclePositionRedisDao;
    private final RoadSegmentManager roadSegmentManager;
    private final MeterRegistry meterRegistry;

    @Autowired
    public MonitorController(VehiclePositionRedisDao vehiclePositionRedisDao,
                             RoadSegmentManager roadSegmentManager,
                             MeterRegistry meterRegistry) {
        this.vehiclePositionRedisDao = vehiclePositionRedisDao;
        this.roadSegmentManager = roadSegmentManager;
        this.meterRegistry = meterRegistry;
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
        double avgResponseTime = 0;
        double p95ResponseTime = 0;
        double p99ResponseTime = 0;

        for (Timer timer : timers) {
            if (timer.getId().getName().startsWith("http.server.requests")) {
                totalRequests += timer.count();
                avgResponseTime += timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS) * timer.count();
                p95ResponseTime = Math.max(p95ResponseTime,
                        timer.takeSnapshot().percentileValues()[0].value(java.util.concurrent.TimeUnit.MILLISECONDS));
                p99ResponseTime = Math.max(p99ResponseTime,
                        timer.takeSnapshot().percentileValues()[1].value(java.util.concurrent.TimeUnit.MILLISECONDS));
            }
        }

        if (totalRequests > 0) {
            avgResponseTime = avgResponseTime / totalRequests;
        }

        result.put("totalRequests", (long) totalRequests);
        result.put("avgResponseTimeMs", Math.round(avgResponseTime * 100) / 100.0);
        result.put("p95ResponseTimeMs", Math.round(p95ResponseTime * 100) / 100.0);
        result.put("p99ResponseTimeMs", Math.round(p99ResponseTime * 100) / 100.0);
        result.put("qps", Math.round(totalRequests / 3600.0 * 100) / 100.0);

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
