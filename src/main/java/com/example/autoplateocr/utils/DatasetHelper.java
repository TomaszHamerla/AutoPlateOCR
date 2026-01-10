package com.example.autoplateocr.utils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DatasetHelper {

    public static Map<String, String> loadGroundTruthMap(File xmlFile) {
        Map<String, String> groundTruthMap = new HashMap<>();

        try {
            System.out.println("Wczytywanie adnotacji z: " + xmlFile.getAbsolutePath());

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            NodeList imageNodes = doc.getElementsByTagName("image");

            if (imageNodes.getLength() > 0) {
                System.out.println("Wykryto format oparty na tagach <image> (np. CVAT).");
                for (int i = 0; i < imageNodes.getLength(); i++) {
                    Element imageElement = (Element) imageNodes.item(i);
                    String fileName = imageElement.getAttribute("name");

                    NodeList boxNodes = imageElement.getElementsByTagName("box");
                    if (boxNodes.getLength() > 0) {
                        Element boxElement = (Element) boxNodes.item(0);
                        String label = boxElement.getAttribute("label");

                        if (label == null || label.isEmpty()) {
                        }

                        if (fileName != null && !fileName.isEmpty() && label != null && !label.isEmpty()) {
                            File f = new File(fileName);
                            groundTruthMap.put(f.getName(), label);
                        }
                    }
                }
            } else {
                System.out.println("Brak tagów <image>, próba formatu standardowego <annotation>...");
                NodeList annotationNodes = doc.getElementsByTagName("annotation");

                for (int i = 0; i < annotationNodes.getLength(); i++) {
                    Element annotation = (Element) annotationNodes.item(i);

                    String fileName = getTagValue(annotation, "filename");
                    String plate = "";

                    NodeList objects = annotation.getElementsByTagName("object");
                    if (objects.getLength() > 0) {
                        Element obj = (Element) objects.item(0);
                        plate = getTagValue(obj, "name");
                    }

                    if (fileName != null && plate != null && !plate.isEmpty()) {
                        groundTruthMap.put(fileName, plate);
                    }
                }
            }

            System.out.println("Załadowano " + groundTruthMap.size() + " adnotacji do pamięci.");

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Błąd podczas parsowania pliku XML: " + e.getMessage());
        }
        return groundTruthMap;
    }

    private static String getTagValue(Element element, String tagName) {
        NodeList nodeList = element.getElementsByTagName(tagName);
        if (nodeList != null && nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }
}