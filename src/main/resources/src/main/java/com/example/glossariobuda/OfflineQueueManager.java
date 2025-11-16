// Enhanced version with extensive debugging

package com.example.glossariobuda;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class OfflineQueueManager {
    private final Path queueFilePath;
    private static final int MAX_RETRY_COUNT = 3;

    public OfflineQueueManager() {
        String userHome = System.getProperty("user.home");
        Path appDir = Paths.get(userHome, "Documents", ".glossariobuda");

        try {
            Files.createDirectories(appDir);
            System.out.println("[OfflineQueue] ✅ App directory created/verified: " + appDir);
        } catch (IOException e) {
            System.err.println("[OfflineQueue] ❌ Failed to create app directory: " + e.getMessage());
        }

        this.queueFilePath = appDir.resolve("offline_queue.txt");
        System.out.println("[OfflineQueue] 📁 Queue file path: " + queueFilePath.toAbsolutePath());
        System.out.println("[OfflineQueue] 📝 File exists: " + Files.exists(queueFilePath));

        // Check if file is writable
        if (Files.exists(queueFilePath)) {
            System.out.println("[OfflineQueue] ✍️ File writable: " + Files.isWritable(queueFilePath));
        }
    }

    /**
     * Add an operation to the offline queue with extensive debugging
     */
    public synchronized void addOperation(QueueOperationParams params) {
        System.out.println("\n[OfflineQueue] ========================================");
        System.out.println("[OfflineQueue] 🔔 ATTEMPTING TO ADD OPERATION");
        System.out.println("[OfflineQueue] Operation: " + params.operation);
        System.out.println("[OfflineQueue] Source Term: " + params.sourceTerm);
        System.out.println("[OfflineQueue] Term ID: " + params.termId);
        System.out.println("[OfflineQueue] Owner: " + params.owner);
        System.out.println("[OfflineQueue] Original Hash: " + (params.originalHash != null ? params.originalHash : "(null)"));
        System.out.println("[OfflineQueue] File path: " + queueFilePath.toAbsolutePath());

        try {
            // Ensure parent directory exists
            Files.createDirectories(queueFilePath.getParent());

            // Format the line (new format with owner and hash - 13 fields)
            String line = String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%d|%d|%s",
                    params.operation,
                    escape(params.sourceTerm),
                    escape(params.sourceLanguage),
                    escape(params.targetTerm),
                    escape(params.targetLanguage),
                    escape(params.context),
                    escape(params.contributor),
                    escape(params.notes),
                    escape(params.status),
                    escape(params.owner),
                    params.termId,
                    0,
                    escape(params.originalHash != null ? params.originalHash : "")); // Use provided hash or empty string

            System.out.println("[OfflineQueue] 📝 Line to write: " + line);

            // Write to file with proper resource management
            try (FileOutputStream fos = new FileOutputStream(queueFilePath.toFile(), true);
                 OutputStreamWriter osw = new OutputStreamWriter(fos, java.nio.charset.StandardCharsets.UTF_8);
                 BufferedWriter writer = new BufferedWriter(osw)) {

                writer.write(line);
                writer.newLine();
                writer.flush();
                fos.getFD().sync(); // Force OS to write to disk

                System.out.println("[OfflineQueue] ✅ Successfully wrote to file");
            }

            // Verify it was written
            if (Files.exists(queueFilePath)) {
                long fileSize = Files.size(queueFilePath);
                System.out.println("[OfflineQueue] 📊 File size after write: " + fileSize + " bytes");

                // Read and print file contents
                List<String> lines = Files.readAllLines(queueFilePath);
                System.out.println("[OfflineQueue] 📄 Total lines in file: " + lines.size());
                System.out.println("[OfflineQueue] 📄 File contents:");
                for (int i = 0; i < lines.size(); i++) {
                    System.out.println("  Line " + (i+1) + ": " + lines.get(i));
                }
            }

            System.out.println("[OfflineQueue] ✅ OPERATION ADDED SUCCESSFULLY");
            System.out.println("[OfflineQueue] ========================================\n");

        } catch (IOException e) {
            System.err.println("[OfflineQueue] ❌ FAILED TO ADD OPERATION");
            System.err.println("[OfflineQueue] Error: " + e.getMessage());
            e.printStackTrace();
            System.out.println("[OfflineQueue] ========================================\n");
        }
    }

    /**
     * Get all pending operations from the queue
     */
    public synchronized List<QueuedOperation> getPendingOperations() {
        List<QueuedOperation> operations = new ArrayList<>();

        System.out.println("\n[OfflineQueue] 🔍 GETTING PENDING OPERATIONS");
        System.out.println("[OfflineQueue] File path: " + queueFilePath.toAbsolutePath());
        System.out.println("[OfflineQueue] File exists: " + Files.exists(queueFilePath));

        if (!Files.exists(queueFilePath)) {
            System.out.println("[OfflineQueue] ⚠️ Queue file does not exist yet");
            return operations;
        }

        try {
            long fileSize = Files.size(queueFilePath);
            System.out.println("[OfflineQueue] File size: " + fileSize + " bytes");

            List<String> allLines = Files.readAllLines(queueFilePath);
            System.out.println("[OfflineQueue] Total lines in file: " + allLines.size());

        } catch (IOException e) {
            System.err.println("[OfflineQueue] Error checking file: " + e.getMessage());
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(queueFilePath.toFile()))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                System.out.println("[OfflineQueue] Reading line " + lineNum + ": " + line);

                if (line.trim().isEmpty()) {
                    System.out.println("[OfflineQueue] Skipping empty line");
                    continue;
                }

                try {
                    QueuedOperation op = parseLine(line);
                    if (op != null && op.retryCount < MAX_RETRY_COUNT) {
                        operations.add(op);
                        System.out.println("[OfflineQueue] ✅ Parsed operation: " + op.sourceTerm);
                    } else if (op != null && op.retryCount >= MAX_RETRY_COUNT) {
                        System.err.println("[OfflineQueue] ⚠️ Skipping operation after " +
                                MAX_RETRY_COUNT + " failed attempts: " + op.sourceTerm);
                    }
                } catch (Exception e) {
                    System.err.println("[OfflineQueue] ❌ Failed to parse line " + lineNum + ": " + line);
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("[OfflineQueue] ❌ Failed to read queue: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("[OfflineQueue] Found " + operations.size() + " pending operations");
        return operations;
    }

    /**
     * Increment retry count for failed operations
     */
    public synchronized void incrementRetryCount(List<QueuedOperation> failedOps) {
        if (failedOps == null || failedOps.isEmpty()) {
            return;
        }

        System.out.println("\n[OfflineQueue] 🔄 INCREMENTING RETRY COUNT for " + failedOps.size() + " operations");

        if (!Files.exists(queueFilePath)) {
            return;
        }

        Map<String, QueuedOperation> failedOpsMap = new HashMap<>();
        for (QueuedOperation op : failedOps) {
            String signature = createOperationSignature(op);
            failedOpsMap.put(signature, op);
        }

        List<String> updatedLines = new ArrayList<>();

        try {
            List<String> allLines = Files.readAllLines(queueFilePath);

            for (String line : allLines) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\|", -1);
                if (parts.length >= 11) {
                    String lineSignature = createSignatureFromParts(parts);

                    if (failedOpsMap.containsKey(lineSignature)) {
                        int currentRetry = Integer.parseInt(parts[10]);
                        int newRetry = currentRetry + 1;
                        parts[10] = String.valueOf(newRetry);

                        String updatedLine = String.join("|", parts);
                        updatedLines.add(updatedLine);

                        System.out.println("[OfflineQueue] Incrementing retry count to " +
                                newRetry + " for: " + unescape(parts[1]));
                    } else {
                        updatedLines.add(line);
                    }
                } else {
                    updatedLines.add(line);
                }
            }

        } catch (IOException e) {
            System.err.println("[OfflineQueue] Error reading queue: " + e.getMessage());
            return;
        }

        writeLinesToFile(updatedLines);
    }

    /**
     * Remove specific operations from the queue
     */
    public synchronized void removeOperations(List<QueuedOperation> opsToRemove) {
        if (opsToRemove == null || opsToRemove.isEmpty()) {
            return;
        }

        System.out.println("\n[OfflineQueue] 🗑️ REMOVING " + opsToRemove.size() + " operations");

        if (!Files.exists(queueFilePath)) {
            return;
        }

        Set<String> signaturesToRemove = new HashSet<>();
        for (QueuedOperation op : opsToRemove) {
            signaturesToRemove.add(createOperationSignature(op));
            System.out.println("[OfflineQueue] Will remove: " + op.sourceTerm);
        }

        List<String> remainingLines = new ArrayList<>();
        int removedCount = 0;

        try {
            List<String> allLines = Files.readAllLines(queueFilePath);
            System.out.println("[OfflineQueue] Total lines before removal: " + allLines.size());

            for (String line : allLines) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\|", -1);
                if (parts.length >= 8) {
                    String lineSignature = createSignatureFromParts(parts);

                    if (signaturesToRemove.contains(lineSignature)) {
                        removedCount++;
                        System.out.println("[OfflineQueue] ✅ Removing successful operation: " +
                                unescape(parts[1]));
                    } else {
                        remainingLines.add(line);
                    }
                } else {
                    remainingLines.add(line);
                }
            }

        } catch (IOException e) {
            System.err.println("[OfflineQueue] Error reading queue: " + e.getMessage());
            return;
        }

        writeLinesToFile(remainingLines);
        System.out.println("[OfflineQueue] Removed " + removedCount + " operations");
        System.out.println("[OfflineQueue] Remaining lines: " + remainingLines.size());
    }

    private void writeLinesToFile(List<String> lines) {
        System.out.println("[OfflineQueue] 💾 Writing " + lines.size() + " lines to file");

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("offline_queue_", ".tmp");

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile.toFile()))) {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
                writer.flush();
            }

            Files.move(tempFile, queueFilePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            System.out.println("[OfflineQueue] ✅ File written successfully");

        } catch (IOException e) {
            System.err.println("[OfflineQueue] ❌ Error writing queue: " + e.getMessage());
            e.printStackTrace();
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupEx) {
                    // Ignore
                }
            }
        }
    }
    private QueuedOperation parseLine(String line) {
        String[] parts = line.split("\\|", -1);

        if (parts.length < 10) {
            return null;
        }

        QueuedOperation op = new QueuedOperation();
        op.operation = parts[0];
        op.sourceTerm = unescape(parts[1]);
        op.sourceLanguage = unescape(parts[2]);
        op.targetTerm = unescape(parts[3]);
        op.targetLanguage = unescape(parts[4]);
        op.context = unescape(parts[5]);
        op.contributor = unescape(parts[6]);
        op.notes = unescape(parts[7]);
        op.status = unescape(parts[8]);

        // Handle both old format (without owner) and new format (with owner)
        if (parts.length >= 13) {
            // New format with owner field (13+ fields)
            op.owner = unescape(parts[9]);
            op.termId = Integer.parseInt(parts[10]);
            op.retryCount = Integer.parseInt(parts[11]);
            op.originalHash = unescape(parts[12]);
        } else if (parts.length == 12) {
            // Ambiguous: could be old format OR new format missing hash
            // Check if parts[9] is numeric to determine format
            try {
                Integer.parseInt(parts[9]);
                // parts[9] is numeric -> Old format without owner
                op.owner = null;
                op.termId = Integer.parseInt(parts[9]);
                op.retryCount = Integer.parseInt(parts[10]);
                op.originalHash = unescape(parts[11]);
            } catch (NumberFormatException e) {
                // parts[9] is NOT numeric -> New format missing hash
                op.owner = unescape(parts[9]);
                op.termId = Integer.parseInt(parts[10]);
                op.retryCount = Integer.parseInt(parts[11]);
                op.originalHash = ""; // Missing hash
            }
        } else {
            // Old format without owner field (10-11 fields)
            op.owner = null;
            op.termId = Integer.parseInt(parts[9]);
            op.retryCount = parts.length >= 11 ? Integer.parseInt(parts[10]) : 0;
            op.originalHash = parts.length >= 12 ? unescape(parts[11]) : null;
        }

        return op;
    }

    private String createOperationSignature(QueuedOperation op) {
        return String.format("%s|%s|%s|%s|%s|%s",
                op.operation != null ? op.operation : "",
                op.sourceTerm != null ? op.sourceTerm : "",
                op.sourceLanguage != null ? op.sourceLanguage : "",
                op.targetTerm != null ? op.targetTerm : "",
                op.targetLanguage != null ? op.targetLanguage : "",
                op.context != null ? op.context : ""
        );
    }

    private String createSignatureFromParts(String[] parts) {
        if (parts.length < 6) return "";

        return String.format("%s|%s|%s|%s|%s|%s",
                parts[0],
                unescape(parts[1]),
                unescape(parts[2]),
                unescape(parts[3]),
                unescape(parts[4]),
                unescape(parts[5])
        );
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("|", "\\|").replace("\n", "\\n");
    }

    private String unescape(String value) {
        if (value == null) return "";
        return value.replace("\\|", "|").replace("\\n", "\n");
    }

    public synchronized boolean hasPendingOperations() {
        try {
            if (!Files.exists(queueFilePath)) return false;
            List<String> lines = Files.readAllLines(queueFilePath);
            boolean hasPending = lines.stream().anyMatch(line -> !line.trim().isEmpty());
            System.out.println("[OfflineQueue] Has pending operations: " + hasPending);
            return hasPending;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized int getPendingCount() {
        try {
            if (!Files.exists(queueFilePath)) return 0;
            List<String> lines = Files.readAllLines(queueFilePath);
            int count = (int) lines.stream().filter(line -> !line.trim().isEmpty()).count();
            System.out.println("[OfflineQueue] Pending count: " + count);
            return count;
        } catch (IOException e) {
            return 0;
        }
    }

    public synchronized void clearQueue() {
        System.out.println("[OfflineQueue] 🗑️ CLEARING QUEUE");
        try {
            if (Files.exists(queueFilePath)) {
                Files.delete(queueFilePath);
                System.out.println("[OfflineQueue] ✅ Queue cleared");
            }
        } catch (IOException e) {
            System.err.println("[OfflineQueue] ❌ Error clearing queue: " + e.getMessage());
        }
    }

    public static class QueuedOperation {
        public String operation;
        public int termId;
        public String sourceTerm;
        public String sourceLanguage;
        public String targetTerm;
        public String targetLanguage;
        public String context;
        public String contributor;
        public String notes;
        public String status;
        public String owner;         // Owner field for imported terms
        public int retryCount;
        public String errorMessage;  // Store error details
        public boolean shouldRetry;  // Whether this should be retried
        public String originalHash;  // Store the original hash to identify the record
    }

    public synchronized void addOperationObject(OfflineQueueManager.QueuedOperation op) {
        try {
            Files.createDirectories(queueFilePath.getParent());

            String line = String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%d|%d|%s",
                    op.operation,
                    escape(op.sourceTerm),
                    escape(op.sourceLanguage),
                    escape(op.targetTerm),
                    escape(op.targetLanguage),
                    escape(op.context),
                    escape(op.contributor),
                    escape(op.notes),
                    escape(op.status),
                    escape(op.owner),
                    op.termId,
                    op.retryCount,
                    escape(op.originalHash));

            try (FileOutputStream fos = new FileOutputStream(queueFilePath.toFile(), true);
                 OutputStreamWriter osw = new OutputStreamWriter(fos, java.nio.charset.StandardCharsets.UTF_8);
                 BufferedWriter writer = new BufferedWriter(osw)) {
                writer.write(line);
                writer.newLine();
                writer.flush();
                fos.getFD().sync();
            }

            System.out.println("[OfflineQueue] ✅ Operation added with hash: " + op.originalHash);

        } catch (IOException e) {
            System.err.println("[OfflineQueue] ❌ Failed to add operation: " + e.getMessage());
        }
    }

}