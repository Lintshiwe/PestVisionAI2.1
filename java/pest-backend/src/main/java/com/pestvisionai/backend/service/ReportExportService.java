package com.pestvisionai.backend.service;

import com.pestvisionai.backend.model.Detection;
import com.pestvisionai.backend.repository.DetectionRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReportExportService {

    private static final Logger log = LoggerFactory.getLogger(ReportExportService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final DetectionRepository detectionRepository;

    public ReportExportService(DetectionRepository detectionRepository) {
        this.detectionRepository = detectionRepository;
    }

    public byte[] exportRecentDetections(int limit) {
        List<Detection> detections = detectionRepository.findTop50ByOrderByDetectedAtDesc();
        if (detections.size() > limit) {
            detections = detections.subList(0, limit);
        }

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Detections");
            buildHeaderRow(sheet);
            populateRows(sheet, detections);
            for (int i = 0; i < 8; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            log.error("Failed to export detections report", ex);
            throw new IllegalStateException("Unable to generate Excel report", ex);
        }
    }

    private void buildHeaderRow(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] labels = new String[] {
            "Detected At (UTC)",
            "Stream",
            "Service",
            "Pest Type",
            "Count",
            "Max Confidence",
            "AI Summary",
            "Boxes"
        };
        for (int i = 0; i < labels.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(labels[i]);
        }
    }

    private void populateRows(Sheet sheet, List<Detection> detections) {
        AtomicInteger rowIndex = new AtomicInteger(1);
        CellStyle wrapStyle = sheet.getWorkbook().createCellStyle();
        wrapStyle.setWrapText(true);
        detections.forEach(detection -> {
            Row row = sheet.createRow(rowIndex.getAndIncrement());
            int col = 0;
            createCell(row, col++, TIMESTAMP_FORMATTER.format(detection.getDetectedAt().atOffset(ZoneOffset.UTC)));
            createCell(row, col++, detection.getStreamId());
            createCell(row, col++, detection.getServiceName());
            createCell(row, col++, detection.getPestType());
            createNumericCell(row, col++, detection.getPestCount());
            createCell(row, col++, String.format("%.2f", detection.getMaxConfidence()));
            Cell summaryCell = row.createCell(col++);
            summaryCell.setCellValue(detection.getAnalysisSummary() == null ? "" : detection.getAnalysisSummary());
            summaryCell.setCellStyle(wrapStyle);

            String boxes = detection.getBoxes().stream()
                    .map(box -> String.format("label=%s (%.2f) [x=%d y=%d w=%d h=%d]",
                            box.getLabel(), box.getConfidence(), box.getX(), box.getY(), box.getWidth(), box.getHeight()))
                    .reduce((first, second) -> first + "\n" + second)
                    .orElse("");
            Cell boxCell = row.createCell(col);
            boxCell.setCellValue(boxes);
            boxCell.setCellStyle(wrapStyle);
        });
    }

    private void createCell(Row row, int index, String value) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value == null ? "" : value);
    }

    private void createNumericCell(Row row, int index, double value) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value);
    }
}
