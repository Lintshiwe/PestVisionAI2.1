package com.pestvisionai.backend.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "detections")
public class Detection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant detectedAt;
    private String streamId;
    private String serviceName;
    private String pestType;
    private int pestCount;
    private double maxConfidence;
    private String snapshotPath;
    @Column(length = 2048)
    private String analysisSummary;

    @ElementCollection
    @CollectionTable(name = "detection_boxes", joinColumns = @JoinColumn(name = "detection_id"))
    private List<BoundingBox> boxes = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getPestType() {
        return pestType;
    }

    public void setPestType(String pestType) {
        this.pestType = pestType;
    }

    public int getPestCount() {
        return pestCount;
    }

    public void setPestCount(int pestCount) {
        this.pestCount = pestCount;
    }

    public double getMaxConfidence() {
        return maxConfidence;
    }

    public void setMaxConfidence(double maxConfidence) {
        this.maxConfidence = maxConfidence;
    }

    public String getSnapshotPath() {
        return snapshotPath;
    }

    public void setSnapshotPath(String snapshotPath) {
        this.snapshotPath = snapshotPath;
    }

    public String getAnalysisSummary() {
        return analysisSummary;
    }

    public void setAnalysisSummary(String analysisSummary) {
        this.analysisSummary = analysisSummary;
    }

    public List<BoundingBox> getBoxes() {
        return boxes;
    }

    public void setBoxes(List<BoundingBox> boxes) {
        this.boxes = boxes;
    }
}
