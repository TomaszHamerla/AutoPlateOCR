package com.example.autoplateocr.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.*;

@Service
public class PythonOcrService {

    private Process pythonProcess;
    private BufferedReader reader;
    private BufferedWriter writer;

    @PostConstruct
    public void init() {
        try {
            System.out.println(">>> [INIT] Uruchamianie silnika Python OCR...");

            ProcessBuilder pb = new ProcessBuilder("python", "ocr_engine.py");
            pb.redirectErrorStream(true);
            this.pythonProcess = pb.start();

            this.reader = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()));
            this.writer = new BufferedWriter(new OutputStreamWriter(pythonProcess.getOutputStream()));

            String line = reader.readLine();
            if ("READY".equals(line)) {
                System.out.println(">>> [INIT] Silnik OCR jest gotowy i nasłuchuje!");
            } else {
                throw new RuntimeException("Python nie wystartował poprawnie: " + line);
            }

        } catch (IOException e) {
            throw new RuntimeException("Błąd uruchamiania skryptu Pythona. Upewnij się, że masz zainstalowane biblioteki.", e);
        }
    }

    public synchronized String analyzeImage(String imagePath) {
        if (writer == null) return "ERROR_NO_ENGINE";

        try {
            writer.write(imagePath);
            writer.newLine();
            writer.flush();

            String result = reader.readLine();

            if (result == null) return "ERROR_NULL";
            return result.trim();

        } catch (IOException e) {
            e.printStackTrace();
            return "ERROR_IO";
        }
    }

    @PreDestroy
    public void cleanup() {
        System.out.println(">>> [CLEANUP] Zamykanie silnika Python...");
        try {
            if (writer != null) {
                writer.write("EXIT");
                writer.newLine();
                writer.flush();
            }
            if (pythonProcess != null) pythonProcess.destroy();
        } catch (Exception e) {
        }
    }
}