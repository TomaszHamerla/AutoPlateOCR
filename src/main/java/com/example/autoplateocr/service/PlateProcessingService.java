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

        this.tesseract = new Tesseract();
        this.tesseract.setDatapath("tessdata");
        this.tesseract.setLanguage("eng");
        this.tesseract.setTessVariable("tessedit_char_whitelist", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
    }

    public String processImage(File imageFile) {
        Mat src = Imgcodecs.imread(imageFile.getAbsolutePath());
        if (src.empty()) return "";

        double scale = 1.0;
        if (src.width() > 1000) {
            scale = 800.0 / src.width();
            Imgproc.resize(src, src, new Size(src.width() * scale, src.height() * scale));
        }

        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);

        MatOfRect plates = new MatOfRect();
        plateDetector.detectMultiScale(gray, plates, 1.1, 3, 0, new Size(20, 20), new Size());

        Rect[] platesArray = plates.toArray();
        String bestResult = "";

        if (platesArray.length > 0) {
            Rect bestRect = platesArray[0];
            for (Rect r : platesArray) {
                if (r.area() > bestRect.area()) bestRect = r;
            }

            Mat cropped = new Mat(src, bestRect);

            Mat binary = new Mat();
            Imgproc.cvtColor(cropped, binary, Imgproc.COLOR_BGR2GRAY);
            Imgproc.threshold(binary, binary, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

            bestResult = performOcr(matToBufferedImage(binary));
        }

        src.release();
        gray.release();

        return bestResult;
    }

    private String performOcr(BufferedImage img) {
        try {
            String txt = tesseract.doOCR(img);
            return txt.replaceAll("[^A-Z0-9]", "").trim();
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
            return null;
        }
    }
}
