package com.example.autoplateocr.controller;

import com.example.autoplateocr.service.PythonOcrService;
import com.example.autoplateocr.service.QueueConsumer;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@RestController
@RequestMapping("/api/plates")
public class OcrController {

    @Autowired
    private PythonOcrService ocrService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private String saveFile(MultipartFile file) throws IOException {
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        File target = new File("uploads/" + filename);
        target.getParentFile().mkdirs();
        Files.copy(file.getInputStream(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return target.getAbsolutePath();
    }

    @PostMapping("/analyze-sync")
    public ResponseEntity<String> analyzeSync(@RequestParam("image") MultipartFile file) {
        try {
            String path = saveFile(file);
            long start = System.currentTimeMillis();

            String plateNumber = ocrService.analyzeImage(path);

            long time = System.currentTimeMillis() - start;
            return ResponseEntity.ok("Tablica: " + plateNumber + " (Czas: " + time + "ms)");
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Błąd zapisu pliku");
        }
    }

    @PostMapping("/analyze-async")
    public ResponseEntity<String> analyzeAsync(@RequestParam("image") MultipartFile file) {
        try {
            String path = saveFile(file);

            rabbitTemplate.convertAndSend(QueueConsumer.QUEUE_NAME, path);

            return ResponseEntity.ok("Zadanie przyjęte. Plik zapisany: " + path);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Błąd zapisu pliku");
        }
    }
}