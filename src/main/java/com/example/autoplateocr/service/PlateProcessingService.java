package com.example.autoplateocr.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class PlateProcessingService {

    private Process pythonProcess;
    private BufferedReader reader;
    private BufferedWriter writer;

    @PostConstruct
    public void init() {
        try {
            System.out.println(">>> Uruchamianie silnika Python OCR...");

            ProcessBuilder pb = new ProcessBuilder("python", "ocr_server.py");
            pb.redirectErrorStream(true); // Przekieruj błędy do standardowego wyjścia
            this.pythonProcess = pb.start();

            this.reader = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()));
            this.writer = new BufferedWriter(new OutputStreamWriter(pythonProcess.getOutputStream()));

            // Czekamy na sygnał "READY" od Pythona
            String line = reader.readLine();
            if ("READY".equals(line)) {
                System.out.println(">>> Silnik OCR gotowy do pracy!");
            } else {
                throw new RuntimeException("Python zwrócił błąd przy starcie: " + line);
            }

        } catch (IOException e) {
            throw new RuntimeException("Nie udało się uruchomić skryptu Pythona. Sprawdź czy masz 'python' w PATH i zainstalowane biblioteki.", e);
        }
    }

    public String processImage(File imageFile) {
        if (!imageFile.exists() || writer == null) return "";

        try {
            // 1. Wysyłamy ścieżkę do Pythona
            writer.write(imageFile.getAbsolutePath());
            writer.newLine(); // Koniec linii to sygnał "wykonaj"
            writer.flush();

            // 2. Czekamy na odpowiedź (jedna linia)
            String result = reader.readLine();

            if (result == null || "NONE".equals(result) || result.startsWith("ERROR")) {
                return "";
            }
            return result;

        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (writer != null) {
                writer.write("EXIT");
                writer.newLine();
                writer.flush();
            }
            if (pythonProcess != null) {
                pythonProcess.destroy();
            }
        } catch (Exception e) {
            // ignore
        }
    }
}