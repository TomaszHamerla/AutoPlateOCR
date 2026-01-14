package com.example.autoplateocr;

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

    @Override
    public void run(String... args) {
        System.out.println(">>> START HYBRYDOWEGO SYSTEMU (Haar + EasyOCR) <<<");

        File imagesDir = new File("dataset/images");
        File annotationFile = new File("dataset/annotations/annotations.xml");

        // Wczytanie XML (używamy tej samej klasy DatasetHelper co wcześniej)
        Map<String, String> groundTruth = DatasetHelper.loadGroundTruthMap(annotationFile);

        if (groundTruth.isEmpty()) {
            System.err.println("BŁĄD: Mapa XML jest pusta! Sprawdź parser DatasetHelper.");
            return;
        }

        File[] files = imagesDir.listFiles((d, n) -> n.toLowerCase().endsWith(".jpg"));
        List<File> testSet = new ArrayList<>(Arrays.asList(files));

        // --- TESTUJEMY NA 100 ZDJĘCIACH ---
        Collections.shuffle(testSet);
        if (testSet.size() > 100) testSet = testSet.subList(0, 100);

        System.out.println("Rozpoczynam przetwarzanie " + testSet.size() + " zdjęć...");

        int correct = 0;
        long startTime = System.currentTimeMillis();

        for (File img : testSet) {
            // Jeśli nie mamy odpowiedzi w XML dla tego pliku, pomiń
            if (!groundTruth.containsKey(img.getName())) continue;

            String real = groundTruth.get(img.getName()).replaceAll("[^A-Z0-9]", "");
            String predicted = plateService.processImage(img);

            boolean ok = checkMatch(predicted, real);
            if (ok) correct++;

            // --- LOGOWANIE BŁĘDÓW (Żebyś widział co nie działa) ---
            if (!ok) {
                System.out.printf("[BŁĄD] Plik: %-10s | OCR: %-10s | XML: %-10s%n",
                        img.getName(), predicted, real);
            } else {
                // Opcjonalnie loguj sukcesy
                // System.out.println("[OK] " + img.getName());
            }
        }

        long endTime = System.currentTimeMillis();
        double timeSec = (endTime - startTime) / 1000.0;
        double accuracy = ((double) correct / testSet.size()) * 100.0;

        // Estymacja czasu dla 100 zdjęć (jeśli testowaliśmy na mniejszej liczbie)
        double timePer100 = (timeSec / testSet.size()) * 100.0;

        System.out.println("=============================================");
        System.out.printf("Trafienia:  %d / %d%n", correct, testSet.size());
        System.out.printf("DOKŁADNOŚĆ: %.2f%%%n", accuracy);
        System.out.printf("CZAS (100): %.2fs%n", timePer100);
        System.out.printf("Średnio:    %.3fs / zdjęcie%n", timeSec / testSet.size());

        double grade = calculateGrade(accuracy, timePer100);
        System.out.println("OCENA:      " + grade);
        System.out.println("=============================================");
    }

    private boolean checkMatch(String predicted, String actual) {
        if (predicted.equals(actual)) return true;
        if (predicted.length() < 3 || actual.length() < 3) return false;

        // Tolerancja 1 błędu (np. O vs 0)
        if (predicted.length() == actual.length()) {
            int diff = 0;
            for(int i=0; i<predicted.length(); i++) if(predicted.charAt(i) != actual.charAt(i)) diff++;
            if(diff <= 1) return true;
        }

        // Tolerancja zawierania (gdy OCR złapie o jedną literę za dużo/za mało)
        if (predicted.contains(actual)) return true;
        if (actual.contains(predicted) && predicted.length() >= actual.length() - 1) return true;

        return false;
    }

    private double calculateGrade(double acc, double time) {
        // Wymogi minimalne (dokładność < 60% lub czas > 60s -> ocena 2.0)
        if (acc < 60 || time > 60) return 2.0;

        // Normalizacja dokładności: 60% -> 0.0, 100% -> 1.0
        double accNorm = (acc - 60) / 40.0;

        // Normalizacja czasu: 60s -> 0.0, 10s -> 1.0
        double timeNorm = (60 - time) / 50.0;

        // Zabezpieczenie (clamp), żeby czas poniżej 10s nie dawał punktów > 1.0
        if(timeNorm > 1.0) timeNorm = 1.0;

        // Wynik ważony (wagi 0.7 i 0.3)
        double score = 0.7 * accNorm + 0.3 * timeNorm;

        // Wyliczenie oceny końcowej
        double grade = 2.0 + 3.0 * score;

        // Zaokrąglenie do najbliższej 0.5
        return Math.round(grade * 2) / 2.0;
    }
}