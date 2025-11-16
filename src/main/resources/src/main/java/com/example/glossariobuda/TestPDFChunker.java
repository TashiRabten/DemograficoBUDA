package com.example.glossariobuda;

import java.io.IOException;

/**
 * Test class to demonstrate PDF chunking and transcription for large PDFs
 *
 * Usage: Run this class with the path to a large PDF as a command-line argument
 * Example: java TestPDFChunker "/path/to/large.pdf"
 */
public class TestPDFChunker {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java TestPDFChunker <pdf-file-path> [chunk-size] [output-dir]");
            System.out.println();
            System.out.println("Examples of large PDFs in this project:");
            System.out.println("  - tibetan-dictionary-master/tibetan-dictionary-master/_input/dictionaries/conversion/pdfs/ChandraDas/tibetanenglishdi00dassuoft_bw.pdf (74M)");
            System.out.println("  - tibetan-dictionary-master/tibetan-dictionary-master/_input/dictionaries/conversion/Jaeschke/Jaeschke_1881_Indexed_mKhan_po_sKal bzang.pdf (61M)");
            System.out.println("  - tibetan-dictionary-master/tibetan-dictionary-master/_input/dictionaries/conversion/pdfs/Jaeschke/tibetanenglishdi00jsuoft_bw.pdf (56M)");
            System.out.println();
            System.out.println("Parameters:");
            System.out.println("  pdf-file-path: Path to the PDF file to process (required)");
            System.out.println("  chunk-size: Number of pages per chunk (default: 20)");
            System.out.println("  output-dir: Directory to save output files (default: pdf_output)");
            return;
        }

        String pdfPath = args[0];
        int chunkSize = 20; // default
        String outputDir = "pdf_output"; // default

        // Parse optional chunk size
        if (args.length >= 2) {
            try {
                chunkSize = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid chunk size: " + args[1] + ". Using default: 20");
            }
        }

        // Parse optional output directory
        if (args.length >= 3) {
            outputDir = args[2];
        }

        System.out.println("=== PDF Chunk Transcriber ===");
        System.out.println("PDF File: " + pdfPath);
        System.out.println("Chunk Size: " + chunkSize + " pages");
        System.out.println("Output Directory: " + outputDir);
        System.out.println("==============================\n");

        try {
            // Create transcriber with specified parameters
            PDFChunkTranscriber transcriber = new PDFChunkTranscriber(chunkSize, outputDir);

            // Process the PDF
            PDFChunkTranscriber.TranscriptionResult result = transcriber.processLargePDF(pdfPath, true);

            // Print summary
            System.out.println("\n=== Summary ===");
            System.out.println("Base name: " + result.getBaseName());
            System.out.println("Number of chunks: " + result.getChunkResults().size());
            System.out.println("Complete transcription: " + result.getCompleteTranscriptionPath());
            System.out.println("\nChunk details:");
            for (PDFChunkTranscriber.ChunkResult chunk : result.getChunkResults()) {
                System.out.println(String.format("  Chunk %d: pages %d-%d, %d characters",
                    chunk.getChunkIndex() + 1,
                    chunk.getStartPage() + 1,
                    chunk.getEndPage() + 1,
                    chunk.getText().length()));
            }

        } catch (IOException e) {
            System.err.println("Error processing PDF: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Example method to process a specific PDF from the repository
     */
    public static void processRepositoryPDF(String relativePath) {
        String fullPath = System.getProperty("user.dir") + "/" + relativePath;
        System.out.println("Processing: " + fullPath);

        try {
            PDFChunkTranscriber transcriber = new PDFChunkTranscriber(10, "pdf_output");
            PDFChunkTranscriber.TranscriptionResult result = transcriber.processLargePDF(fullPath, false);
            System.out.println("Successfully processed " + result.getChunkResults().size() + " chunks");
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
