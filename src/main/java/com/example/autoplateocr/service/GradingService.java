package com.example.autoplateocr.service;

import org.springframework.stereotype.Service;

@Service
public class GradingService {

    public double calculateFinalGrade(double accuracyPercent, double processingTimeSec) {
        if (accuracyPercent < 60 || processingTimeSec > 60) {
            return 2.0;
        }

        double accuracyNorm = (accuracyPercent - 60) / 40.0;

        double timeNorm = (60 - processingTimeSec) / 50.0;
        timeNorm = Math.max(0, Math.min(timeNorm, 1.0));

        double score = 0.7 * accuracyNorm + 0.3 * timeNorm;

        double grade = 2.0 + 3.0 * score;
        return Math.round(grade * 2) / 2.0;
    }
}