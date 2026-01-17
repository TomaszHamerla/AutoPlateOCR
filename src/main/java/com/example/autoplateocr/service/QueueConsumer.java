package com.example.autoplateocr.service;

import com.example.autoplateocr.config.RabbitConfig;
import com.example.autoplateocr.model.LicensePlateRecord;
import com.example.autoplateocr.repository.LicensePlateRepository;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class QueueConsumer {

    public static final String QUEUE_NAME = "plate-analysis-queue";

    @Autowired
    private PythonOcrService ocrService;

    @Autowired
    private LicensePlateRepository repository;

    @Bean
    public Queue myQueue() {
        return new Queue(QUEUE_NAME, false);
    }

    @RabbitListener(queues = QUEUE_NAME)
    public void handleMessage(String imagePath) {
        System.out.println(" [QUEUE] Otrzymano zadanie dla pliku: " + imagePath);

        String result = ocrService.analyzeImage(imagePath);

        System.out.println(" [QUEUE] Wynik OCR: " + result);

        LicensePlateRecord record = new LicensePlateRecord(imagePath, result, "QUEUE");
        repository.save(record);
    }
}