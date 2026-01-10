package com.example.autoplateocr;

import com.example.autoplateocr.service.GradingService;
import com.example.autoplateocr.service.PlateProcessingService;
import com.example.autoplateocr.utils.DatasetHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class EvaluationRunner implements CommandLineRunner {

    @Autowired
    private PlateProcessingService plateService;

    @Autowired
    private GradingService gradingService;

    @Override
    public void run(String... args) {
        System.out.println(">>> START SYSTEMU DETEKCJI (WERSJA XML-MAP) <<<");

        File imagesDir = new File("dataset/images");
        File annotationFile = new File("dataset/annotations/annotations.xml");

        if (!imagesDir.exists()) {
            System.err.println("BŁĄD: Folder dataset/images nie istnieje.");
            return;
        }
        if (!annotationFile.exists()) {
            System.err.println("BŁĄD: Plik dataset/annotations/annotations.xml nie istnieje.");
            return;
        }

        Map<String, String> groundTruthMap = DatasetHelper.loadGroundTruthMap(annotationFile);

        if (groundTruthMap.isEmpty()) {
            System.err.println("BŁĄD: Nie udało się wczytać żadnych danych z pliku XML.");
            System.err.println("Sprawdź strukturę pliku annotations.xml (czy ma tagi <image> i atrybuty 'name' oraz <box label='...'>?)");
            return;
        }

        File[] imageFiles = imagesDir.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".png")
        );

        if (imageFiles == null || imageFiles.length == 0) {
            System.err.println("Brak zdjęć w folderze images.");
            return;
        }

        List<File> testSet = new ArrayList<>(Arrays.asList(imageFiles));
        if (testSet.size() > 100) {
            testSet = testSet.subList(0, 100);
        }

        System.out.println("Rozpoczynam test na " + testSet.size() + " zdjęciach...");

        int correct = 0;
        int processedCount = 0;
        long startTime = System.currentTimeMillis();

        for (File img : testSet) {
            String fileName = img.getName();

            if (!groundTruthMap.containsKey(fileName)) {
                System.out.println("POMINIĘTO: Brak wpisu w XML dla pliku: " + fileName);
                continue;
            }

            String actualPlate = groundTruthMap.get(fileName);

            String predictedPlate = "";
            try {
                predictedPlate = plateService.processImage(img);
            } catch (Exception e) {
                System.out.println("Błąd OCR dla " + fileName);
            }

            String pClean = predictedPlate.replaceAll("[^A-Z0-9]", "");
            String aClean = actualPlate.replaceAll("[^A-Z0-9]", "");

            System.out.println(String.format("[%s] OCR: %-10s | XML: %-10s", fileName, pClean, aClean));

            if (pClean.equalsIgnoreCase(aClean) && !pClean.isEmpty()) {
                correct++;
            }
            processedCount++;
        }

        long endTime = System.currentTimeMillis();

        if (processedCount == 0) {
            System.out.println("Koniec testu. Nie przetworzono żadnych par.");
            return;
        }

        double timeSec = (endTime - startTime) / 1000.0;
        double accuracy = ((double) correct / processedCount) * 100.0;
        double estimatedTimeFor100 = (timeSec / processedCount) * 100.0;

        System.out.println("\n------------------------------------------------");
        System.out.println("WYNIKI KOŃCOWE:");
        System.out.println("Przetworzono par: " + processedCount);
        System.out.println("Poprawne: " + correct);
        System.out.println("Dokładność: " + String.format("%.2f", accuracy) + "%");
        System.out.println("Czas (estymowany dla 100): " + String.format("%.2f", estimatedTimeFor100) + "s");

        double grade = gradingService.calculateFinalGrade(accuracy, estimatedTimeFor100);
        System.out.println("\nOCENA: " + grade);
        System.out.println("------------------------------------------------");
    }
}