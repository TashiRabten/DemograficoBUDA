package com.example.glossariobuda;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to safely process large PDF files by breaking them into smaller chunks
 * and transcribing (extracting text from) each chunk separately.
 *
 * This prevents memory issues and errors when dealing with very large PDFs.
 */
public class PDFChunkTranscriber {

    private static final int DEFAULT_CHUNK_SIZE = 20; // pages per chunk
    private static final String DEFAULT_OUTPUT_DIR = "pdf_output";

    private final int chunkSize;
    private final String outputDirectory;

    /**
     * Creates a transcriber with default chunk size (20 pages)
     */
    public PDFChunkTranscriber() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_OUTPUT_DIR);
    }

    /**
     * Creates a transcriber with custom chunk size
     * @param chunkSize Number of pages per chunk
     */
    public PDFChunkTranscriber(int chunkSize) {
        this(chunkSize, DEFAULT_OUTPUT_DIR);
    }

    /**
     * Creates a transcriber with custom chunk size and output directory
     * @param chunkSize Number of pages per chunk
     * @param outputDirectory Directory to save output files
     */
    public PDFChunkTranscriber(int chunkSize, String outputDirectory) {
        this.chunkSize = chunkSize;
        this.outputDirectory = outputDirectory;
    }

    /**
     * Process a large PDF file by splitting it into chunks and transcribing each chunk
     *
     * @param pdfFilePath Path to the PDF file to process
     * @return TranscriptionResult containing all extracted text and metadata
     * @throws IOException If there's an error reading the PDF or writing output
     */
    public TranscriptionResult processLargePDF(String pdfFilePath) throws IOException {
        return processLargePDF(pdfFilePath, true);
    }

    /**
     * Process a large PDF file by splitting it into chunks and transcribing each chunk
     *
     * @param pdfFilePath Path to the PDF file to process
     * @param saveChunkPDFs Whether to save each chunk as a separate PDF file
     * @return TranscriptionResult containing all extracted text and metadata
     * @throws IOException If there's an error reading the PDF or writing output
     */
    public TranscriptionResult processLargePDF(String pdfFilePath, boolean saveChunkPDFs) throws IOException {
        File pdfFile = new File(pdfFilePath);
        if (!pdfFile.exists()) {
            throw new IOException("PDF file not found: " + pdfFilePath);
        }

        // Create output directory
        Path outputPath = Paths.get(outputDirectory);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        // Get base name for output files
        String baseName = pdfFile.getName().replaceFirst("[.][^.]+$", "");

        TranscriptionResult result = new TranscriptionResult(baseName);

        System.out.println("Starting to process: " + pdfFile.getName());
        System.out.println("File size: " + (pdfFile.length() / (1024 * 1024)) + " MB");

        // Load the PDF to get total page count
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            int totalPages = document.getNumberOfPages();
            System.out.println("Total pages: " + totalPages);
            System.out.println("Chunk size: " + chunkSize + " pages");

            int totalChunks = (int) Math.ceil((double) totalPages / chunkSize);
            System.out.println("Total chunks: " + totalChunks);

            // Process each chunk
            for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
                int startPage = chunkIndex * chunkSize;
                int endPage = Math.min(startPage + chunkSize - 1, totalPages - 1);

                System.out.println("\nProcessing chunk " + (chunkIndex + 1) + "/" + totalChunks +
                                   " (pages " + (startPage + 1) + "-" + (endPage + 1) + ")");

                ChunkResult chunkResult = processChunk(document, chunkIndex, startPage, endPage,
                                                       baseName, saveChunkPDFs);
                result.addChunkResult(chunkResult);
            }
        }

        // Save complete transcription to a single file
        String completeTranscriptionPath = Paths.get(outputDirectory, baseName + "_complete_transcription.txt").toString();
        saveTranscription(completeTranscriptionPath, result.getCompleteText());
        result.setCompleteTranscriptionPath(completeTranscriptionPath);

        System.out.println("\n=== Processing Complete ===");
        System.out.println("Complete transcription saved to: " + completeTranscriptionPath);
        System.out.println("Total chunks processed: " + result.getChunkResults().size());
        System.out.println("Total text length: " + result.getCompleteText().length() + " characters");

        return result;
    }

    /**
     * Process a single chunk of the PDF
     */
    private ChunkResult processChunk(PDDocument document, int chunkIndex, int startPage, int endPage,
                                    String baseName, boolean saveChunkPDF) throws IOException {
        ChunkResult chunkResult = new ChunkResult(chunkIndex, startPage, endPage);

        // Create a new document for this chunk
        try (PDDocument chunkDocument = new PDDocument()) {
            // Copy pages to chunk document
            for (int pageIndex = startPage; pageIndex <= endPage; pageIndex++) {
                PDPage page = document.getPage(pageIndex);
                chunkDocument.addPage(page);
            }

            // Extract text from chunk
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(chunkDocument.getNumberOfPages());

            String extractedText = stripper.getText(chunkDocument);
            chunkResult.setText(extractedText);

            // Save chunk PDF if requested
            if (saveChunkPDF) {
                String chunkPDFPath = Paths.get(outputDirectory,
                    baseName + "_chunk_" + String.format("%03d", chunkIndex + 1) + ".pdf").toString();
                chunkDocument.save(chunkPDFPath);
                chunkResult.setChunkPDFPath(chunkPDFPath);
                System.out.println("  Saved chunk PDF: " + chunkPDFPath);
            }

            // Save chunk text
            String chunkTextPath = Paths.get(outputDirectory,
                baseName + "_chunk_" + String.format("%03d", chunkIndex + 1) + "_text.txt").toString();
            saveTranscription(chunkTextPath, extractedText);
            chunkResult.setTextFilePath(chunkTextPath);

            System.out.println("  Extracted " + extractedText.length() + " characters");
            System.out.println("  Saved text to: " + chunkTextPath);
        }

        return chunkResult;
    }

    /**
     * Save transcribed text to a file
     */
    private void saveTranscription(String filePath, String text) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(text);
        }
    }

    /**
     * Result class containing all transcription data
     */
    public static class TranscriptionResult {
        private final String baseName;
        private final List<ChunkResult> chunkResults;
        private String completeTranscriptionPath;

        public TranscriptionResult(String baseName) {
            this.baseName = baseName;
            this.chunkResults = new ArrayList<>();
        }

        public void addChunkResult(ChunkResult chunkResult) {
            this.chunkResults.add(chunkResult);
        }

        public String getCompleteText() {
            StringBuilder sb = new StringBuilder();
            for (ChunkResult chunk : chunkResults) {
                sb.append(chunk.getText());
                if (!chunk.getText().endsWith("\n")) {
                    sb.append("\n");
                }
                sb.append("\n"); // Add blank line between chunks
            }
            return sb.toString();
        }

        public List<ChunkResult> getChunkResults() {
            return chunkResults;
        }

        public String getBaseName() {
            return baseName;
        }

        public String getCompleteTranscriptionPath() {
            return completeTranscriptionPath;
        }

        public void setCompleteTranscriptionPath(String path) {
            this.completeTranscriptionPath = path;
        }
    }

    /**
     * Result class for a single chunk
     */
    public static class ChunkResult {
        private final int chunkIndex;
        private final int startPage;
        private final int endPage;
        private String text;
        private String chunkPDFPath;
        private String textFilePath;

        public ChunkResult(int chunkIndex, int startPage, int endPage) {
            this.chunkIndex = chunkIndex;
            this.startPage = startPage;
            this.endPage = endPage;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public int getStartPage() {
            return startPage;
        }

        public int getEndPage() {
            return endPage;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getChunkPDFPath() {
            return chunkPDFPath;
        }

        public void setChunkPDFPath(String path) {
            this.chunkPDFPath = path;
        }

        public String getTextFilePath() {
            return textFilePath;
        }

        public void setTextFilePath(String path) {
            this.textFilePath = path;
        }
    }

}

