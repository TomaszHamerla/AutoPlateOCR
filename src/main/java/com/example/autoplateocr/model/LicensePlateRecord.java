package com.example.autoplateocr.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
public class LicensePlateRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filePath;
    private String detectedPlate;
    private LocalDateTime processedAt;
    private String source;
    public LicensePlateRecord(String filePath, String detectedPlate, String source) {
        this.filePath = filePath;
        this.detectedPlate = detectedPlate;
        this.source = source;
        this.processedAt = LocalDateTime.now();
    }
}