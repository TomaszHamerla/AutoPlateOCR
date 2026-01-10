package com.example.autoplateocr.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;

@Service
public class PlateProcessingService {

    private final ITesseract tesseract;
    private final CascadeClassifier plateDetector;

    public PlateProcessingService() {
        OpenCV.loadLocally();

        this.plateDetector = new CascadeClassifier("haarcascade_plate.xml");
        if (this.plateDetector.empty()) {
            throw new RuntimeException("Nie udało się załadować haarcascade_plate.xml! Sprawdź czy plik jest w katalogu projektu.");
        }

        this.tesseract = new Tesseract();
        this.tesseract.setDatapath("tessdata");
        this.tesseract.setLanguage("eng");
        this.tesseract.setTessVariable("tessedit_char_whitelist", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
    }

    public String processImage(File imageFile) {
        Mat src = Imgcodecs.imread(imageFile.getAbsolutePath());
        if (src.empty()) return "";

        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);

        MatOfRect plates = new MatOfRect();
        plateDetector.detectMultiScale(gray, plates, 1.1, 3, 0, new Size(30, 30), new Size());

        Rect[] platesArray = plates.toArray();
        String bestResult = "";

        if (platesArray.length > 0) {
            Rect bestRect = platesArray[0];
            for (Rect r : platesArray) {
                if (r.area() > bestRect.area()) bestRect = r;
            }

            Mat cropped = new Mat(src, bestRect);

            Mat resized = new Mat();
            Imgproc.resize(cropped, resized, new Size(), 2.0, 2.0, Imgproc.INTER_CUBIC);
            Mat binary = new Mat();
            Imgproc.cvtColor(resized, binary, Imgproc.COLOR_BGR2GRAY);
            Imgproc.threshold(binary, binary, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

            bestResult = performOcr(matToBufferedImage(binary));
        } else {
            bestResult = performOcr(matToBufferedImage(gray));
        }

        src.release(); gray.release();

        return bestResult;
    }

    private String performOcr(BufferedImage img) {
        try {
            String result = tesseract.doOCR(img);
            return result.replaceAll("[^A-Z0-9]", "").trim();
        } catch (TesseractException e) {
            return "";
        }
    }

    private BufferedImage matToBufferedImage(Mat matrix) {
        MatOfByte mob = new MatOfByte();
        Imgcodecs.imencode(".png", matrix, mob);
        try {
            return ImageIO.read(new ByteArrayInputStream(mob.toArray()));
        } catch (Exception e) {
            throw new RuntimeException("Błąd konwersji obrazu", e);
        }
    }
}
