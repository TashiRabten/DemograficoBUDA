package com.example.glossariobuda;

import java.io.IOException;

public class SimpleRunner {
    public static void main(String[] args) {
        String pdfFilePath = "C:\\Users\\tashi.TASHI-LENOVO\\OneDrive\\Desktop\\Darma\\Comentario\\19-DBU-MA-DGONGS-PA-RAB-GSAL-LoTC-VOL-19-Scan OCR.pdf";
        
        PDFChunkTranscriber transcriber = new PDFChunkTranscriber(20, "pdf_output");
        
        try {
            System.out.println("Processing PDF...");
            PDFChunkTranscriber.TranscriptionResult result = transcriber.processLargePDF(pdfFilePath);
            System.out.println("\nDONE! Check: " + result.getCompleteTranscriptionPath());
        } catch (IOException e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}