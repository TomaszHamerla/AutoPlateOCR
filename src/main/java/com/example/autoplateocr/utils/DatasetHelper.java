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
            System.out.println("Wczytywanie XML: " + xmlFile.getAbsolutePath());
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            NodeList imageNodes = doc.getElementsByTagName("image");

            for (int i = 0; i < imageNodes.getLength(); i++) {
                Element imageElement = (Element) imageNodes.item(i);
                String fileName = imageElement.getAttribute("name");

                NodeList boxNodes = imageElement.getElementsByTagName("box");
                if (boxNodes.getLength() > 0) {
                    Element boxElement = (Element) boxNodes.item(0);

                    NodeList attributes = boxElement.getElementsByTagName("attribute");
                    String plateNumber = "";

                    for (int k = 0; k < attributes.getLength(); k++) {
                        Element attr = (Element) attributes.item(k);
                        if ("plate number".equals(attr.getAttribute("name"))) {
                            plateNumber = attr.getTextContent().trim();
                            break;
                        }
                    }

                    if (!plateNumber.isEmpty() && fileName != null) {
                        groundTruthMap.put(fileName, plateNumber);
                    }
                }
            }
            System.out.println("Poprawnie załadowano " + groundTruthMap.size() + " tablic z pliku XML.");

        } catch (Exception e) {
            e.printStackTrace();
        }
        return groundTruthMap;
    }
}