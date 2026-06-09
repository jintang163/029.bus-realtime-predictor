package com.bus.predictor.route.service;

import com.bus.predictor.route.entity.ScheduleEntity;
import com.bus.predictor.route.mapper.ScheduleMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private final ScheduleMapper scheduleMapper;

    public ScheduleService(ScheduleMapper scheduleMapper) {
        this.scheduleMapper = scheduleMapper;
    }

    public List<ScheduleEntity> listByLineAndDate(String lineId, LocalDate date) {
        return scheduleMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScheduleEntity>()
                        .eq("line_id", lineId)
                        .eq("schedule_date", date)
                        .orderByAsc("departure_time"));
    }

    @Transactional
    public ScheduleEntity create(ScheduleEntity entity) {
        scheduleMapper.insert(entity);
        return entity;
    }

    @Transactional
    public void deleteByLineAndDate(String lineId, LocalDate date) {
        scheduleMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScheduleEntity>()
                        .eq("line_id", lineId)
                        .eq("schedule_date", date));
    }

    @Transactional
    public int importFromExcel(MultipartFile file, String lineId, LocalDate scheduleDate) {
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            List<ScheduleEntity> entities = new ArrayList<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String vehicleId = getCellStringValue(row, 0);
                String driverName = getCellStringValue(row, 1);
                String departureTimeStr = getCellStringValue(row, 2);
                String tripIndexStr = getCellStringValue(row, 3);
                String directionStr = getCellStringValue(row, 4);

                if (vehicleId == null || vehicleId.isEmpty()) continue;

                ScheduleEntity entity = ScheduleEntity.builder()
                        .lineId(lineId)
                        .vehicleId(vehicleId)
                        .driverName(driverName)
                        .scheduleDate(scheduleDate)
                        .departureTime(parseTimeToInt(departureTimeStr))
                        .tripIndex(tripIndexStr != null ? Integer.parseInt(tripIndexStr) : 1)
                        .direction(directionStr != null ? Integer.parseInt(directionStr) : 0)
                        .status(1)
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build();

                entities.add(entity);
            }

            deleteByLineAndDate(lineId, scheduleDate);

            for (ScheduleEntity entity : entities) {
                scheduleMapper.insert(entity);
            }

            log.info("Imported {} schedules for line={}, date={}", entities.size(), lineId, scheduleDate);
            return entities.size();

        } catch (Exception e) {
            log.error("Import schedule Excel failed", e);
            throw new RuntimeException("导入排班数据失败: " + e.getMessage(), e);
        }
    }

    private String getCellStringValue(Row row, int colIdx) {
        org.apache.poi.ss.usermodel.Cell cell = row.getCell(colIdx);
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return null;
        }
    }

    private int parseTimeToInt(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return 0;
        String[] parts = timeStr.split(":");
        if (parts.length >= 2) {
            return Integer.parseInt(parts[0]) * 100 + Integer.parseInt(parts[1]);
        }
        try {
            return Integer.parseInt(timeStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
