package com.example.autoplateocr;

import com.example.autoplateocr.service.GradingService;
import com.example.autoplateocr.service.PlateProcessingService;
import com.example.autoplateocr.utils.DatasetHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;

@Component
public class EvaluationRunner implements CommandLineRunner {

    @Autowired
    private PlateProcessingService plateService;

    @Autowired
    private GradingService gradingService;

    @Override
    public void run(String... args) {
        System.out.println(">>> START SYSTEMU DETEKCJI <<<");

        File imagesDir = new File("dataset/images");
        File annotationFile = new File("dataset/annotations/annotations.xml");

        Map<String, String> groundTruthMap = DatasetHelper.loadGroundTruthMap(annotationFile);

        if (groundTruthMap.isEmpty()) {
            System.err.println("BŁĄD: Mapa pusta. Sprawdź DatasetHelper.");
            return;
        }

        File[] imageFiles = imagesDir.listFiles((dir, name) -> name.endsWith(".jpg"));
        List<File> testSet = new ArrayList<>(Arrays.asList(imageFiles));

        if (testSet.size() > 100) {
            Collections.shuffle(testSet);
            testSet = testSet.subList(0, 100);
        }

        System.out.println("Testowanie na " + testSet.size() + " zdjęciach...");

        int correct = 0;
        int processed = 0;
        long startTime = System.currentTimeMillis();

        for (File img : testSet) {
            if (!groundTruthMap.containsKey(img.getName())) continue;

            String actual = groundTruthMap.get(img.getName());
            String predicted = plateService.processImage(img);

            String pClean = predicted.replaceAll("[^A-Z0-9]", "");
            String aClean = actual.replaceAll("[^A-Z0-9]", "");

            System.out.println(img.getName() + " -> OCR: [" + pClean + "] vs XML: [" + aClean + "]");

            if (pClean.equalsIgnoreCase(aClean) && !pClean.isEmpty()) {
                correct++;
            }
            processed++;
        }

        long endTime = System.currentTimeMillis();
        double timeSec = (endTime - startTime) / 1000.0;

        if (processed == 0) processed = 1;

        double accuracy = ((double) correct / processed) * 100.0;
        double timeFor100 = (timeSec / processed) * 100.0;

        System.out.println("\n------------------------------------------------");
        System.out.println("WYNIKI:");
        System.out.println("Dokładność: " + String.format("%.2f", accuracy) + "%");
        System.out.println("Czas (dla 100): " + String.format("%.2f", timeFor100) + "s");

        double grade = gradingService.calculateFinalGrade(accuracy, timeFor100);
        System.out.println("OCENA: " + grade);
        System.out.println("------------------------------------------------");
    }
}