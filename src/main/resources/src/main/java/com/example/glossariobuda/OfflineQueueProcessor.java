package com.example.glossariobuda;

import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for processing offline queue operations.
 * Handles batched synchronization of ADD, EDIT, and DELETE operations when connection is restored.
 *
 * Uses parallel workers for large batches (>1000 operations) to prevent statement timeouts.
 */
public class OfflineQueueProcessor {

    private static final int MAX_RETRY_ATTEMPTS = 5;

    // Parallel processing thresholds (same strategy as download sync)
    private static final int PARALLEL_BATCH_THRESHOLD = 1000;  // Use parallel workers if batch > 1000
    private static final int PARALLEL_WORKER_COUNT = 4;        // Split into 4 parallel workers
    private static final int WORKER_BATCH_SIZE = 100;          // Each worker inserts 100 ops at a time (payload size limit for large Tibetan text)

    private final SupabaseClient supabaseClient;
    private final OfflineQueueManager offlineQueue;
    private final NetworkStatusMonitor networkMonitor;
    private final AtomicLong lastConnectionCheckTime;

    // Callback interface for connection checking
    public interface ConnectionChecker {
        boolean isOnline();
    }

    // Callback interface for sync operations
    public interface SyncOperationHandler {
        void syncEditToCloudBlocking(int localId, SyncEditParams params) throws Exception;
        String generateHash(String sourceTerm, String sourceLanguage,
                            String targetTerm, String targetLanguage,
                            String context, String contributor);
    }

    private ConnectionChecker connectionChecker;
    private SyncOperationHandler syncHandler;

    public OfflineQueueProcessor(SupabaseClient supabaseClient,
                                  OfflineQueueManager offlineQueue,
                                  NetworkStatusMonitor networkMonitor,
                                  AtomicLong lastConnectionCheckTime) {
        this.supabaseClient = supabaseClient;
        this.offlineQueue = offlineQueue;
        this.networkMonitor = networkMonitor;
        this.lastConnectionCheckTime = lastConnectionCheckTime;
    }

    /**
     * Set the connection checker callback.
     */
    public void setConnectionChecker(ConnectionChecker checker) {
        this.connectionChecker = checker;
    }

    /**
     * Set the sync operation handler callback.
     */
    public void setSyncOperationHandler(SyncOperationHandler handler) {
        this.syncHandler = handler;
    }

    /**
     * Process offline queue in batch mode.
     * Returns number of successfully processed operations.
     */
    public int processOfflineQueueBatch() {
        logBatchProcessingStart();

        if (!hasOperationsToProcess()) {
            return 0;
        }

        notifyProcessingStarted();

        if (!checkConnectionAndNotify()) {
            return 0;
        }

        List<OfflineQueueManager.QueuedOperation> operations = offlineQueue.getPendingOperations();
        System.out.println("[OfflineQueue] 📋 Total de operações na fila: " + operations.size());

        SeparatedOperations separated = separateOperationsByType(operations);

        ProcessingResults results = processAllOperations(separated);

        updateQueueWithResults(results);

        logBatchProcessingComplete(results);

        updateNetworkMonitorStatus(results);

        return results.successful.size();
    }

    private void logBatchProcessingStart() {
        System.out.println("\n[OfflineQueue] ═══════════════════════════════════");
        System.out.println("[OfflineQueue] 🔄 BATCH PROCESSING OFFLINE QUEUE");
        System.out.println("[OfflineQueue] ═══════════════════════════════════");
    }

    private boolean hasOperationsToProcess() {
        if (!offlineQueue.hasPendingOperations()) {
            System.out.println("[OfflineQueue] ℹ️ Nenhuma operação pendente na fila");
            if (networkMonitor != null) {
                networkMonitor.clearOperationMessage();
            }
            return false;
        }
        return true;
    }

    private void notifyProcessingStarted() {
        if (networkMonitor != null) {
            networkMonitor.setSyncing("Processando fila offline em lote...");
        }
    }

    private boolean checkConnectionAndNotify() {
        lastConnectionCheckTime.set(0);
        boolean online = connectionChecker != null && connectionChecker.isOnline();

        System.out.println("[OfflineQueue] Status de conexão: " + (online ? "ONLINE ✅" : "OFFLINE ❌"));

        if (!online) {
            handleOfflineState();
            return false;
        }
        return true;
    }

    private void handleOfflineState() {
        System.out.println("[OfflineQueue] ❌ Ainda offline - não é possível sincronizar");

        if (networkMonitor != null) {
            networkMonitor.setSyncError("Sem conexão para sincronizar");
            networkMonitor.setQueued(offlineQueue.getPendingCount());
        }

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Offline");
            alert.setHeaderText(null);
            alert.setContentText("Sem conexão com a internet.\nAs operações serão sincronizadas automaticamente quando a conexão for restaurada.");
            alert.getDialogPane().setPrefWidth(500);
            alert.getDialogPane().setPrefHeight(180);
            alert.showAndWait();
        });
    }

    private SeparatedOperations separateOperationsByType(List<OfflineQueueManager.QueuedOperation> operations) {
        SeparatedOperations separated = new SeparatedOperations();

        for (OfflineQueueManager.QueuedOperation op : operations) {
            if (op.retryCount >= MAX_RETRY_ATTEMPTS) {
                System.err.println("[OfflineQueue] ⚠️ Max retries reached for: " + op.sourceTerm);
                continue;
            }

            switch (op.operation) {
                case "ADD" -> separated.addOps.add(op);
                case "EDIT" -> separated.editOps.add(op);
                case "DELETE" -> separated.deleteOps.add(op);
            }
        }

        return separated;
    }

    private ProcessingResults processAllOperations(SeparatedOperations separated) {
        ProcessingResults results = new ProcessingResults();

        if (!separated.addOps.isEmpty()) {
            processBatchAddOperations(separated.addOps, results.successful, results.failed, results.errorMessages);
        }

        if (!separated.editOps.isEmpty()) {
            processEditOperations(separated.editOps, results.successful, results.failed, results.errorMessages);
        }

        if (!separated.deleteOps.isEmpty()) {
            processDeleteOperations(separated.deleteOps, results.successful, results.failed, results.errorMessages);
        }

        return results;
    }

    private void updateQueueWithResults(ProcessingResults results) {
        if (!results.successful.isEmpty()) {
            System.out.println("[OfflineQueue] 🗑️ Removendo " + results.successful.size() + " operações bem-sucedidas");
            offlineQueue.removeOperations(results.successful);
        }

        if (!results.failed.isEmpty()) {
            System.out.println("[OfflineQueue] 🔄 Incrementando contador para " + results.failed.size() + " operações falhadas");
            offlineQueue.incrementRetryCount(results.failed);
        }
    }

    private void logBatchProcessingComplete(ProcessingResults results) {
        System.out.println("[OfflineQueue] ═══════════════════════════════════");
        System.out.println("[OfflineQueue] 📊 BATCH QUEUE PROCESSING COMPLETE:");
        System.out.println("[OfflineQueue]   ✅ Sucesso: " + results.successful.size());
        System.out.println("[OfflineQueue]   ❌ Falhas: " + results.failed.size());
        System.out.println("[OfflineQueue] ═══════════════════════════════════\n");
    }

    private void updateNetworkMonitorStatus(ProcessingResults results) {
        if (networkMonitor != null) {
            if (results.failed.isEmpty()) {
                networkMonitor.setSyncSuccess("Sincronizado: " + results.successful.size() + " operação(ões)");
            } else {
                networkMonitor.setSyncError("Parcial: " + results.successful.size() + " OK, " + results.failed.size() + " falharam");
            }
        }
    }

    private static class SeparatedOperations {
        List<OfflineQueueManager.QueuedOperation> addOps = new ArrayList<>();
        List<OfflineQueueManager.QueuedOperation> editOps = new ArrayList<>();
        List<OfflineQueueManager.QueuedOperation> deleteOps = new ArrayList<>();
    }

    private static class ProcessingResults {
        List<OfflineQueueManager.QueuedOperation> successful = new ArrayList<>();
        List<OfflineQueueManager.QueuedOperation> failed = new ArrayList<>();
        Map<OfflineQueueManager.QueuedOperation, String> errorMessages = new HashMap<>();
    }

    /**
     * Process batch ADD operations.
     * Uses parallel workers for large batches (>1000 ops) to prevent statement timeouts.
     */
    private void processBatchAddOperations(List<OfflineQueueManager.QueuedOperation> addOps,
                                            List<OfflineQueueManager.QueuedOperation> successful,
                                            List<OfflineQueueManager.QueuedOperation> failed,
                                            Map<OfflineQueueManager.QueuedOperation, String> errorMessages) {
        System.out.println("[OfflineQueue] 🔄 Batch processing " + addOps.size() + " ADD operations...");

        // SMART BATCH PROCESSING: Use parallel workers for large batches
        if (addOps.size() >= PARALLEL_BATCH_THRESHOLD) {
            System.out.println("[OfflineQueue] ⚡ Large batch detected - using " + PARALLEL_WORKER_COUNT +
                             " parallel workers to prevent timeout");
            processBatchAddOperationsParallel(addOps, successful, failed, errorMessages);
            return;
        }

        // Small batch: Use traditional single batch insert
        List<SupabaseClient.TermDTO> dtos = new ArrayList<>();
        for (OfflineQueueManager.QueuedOperation op : addOps) {
            String hash = syncHandler.generateHash(op.sourceTerm, op.sourceLanguage,
                    op.targetTerm, op.targetLanguage, op.context, op.contributor);

            SupabaseClient.TermDTO dto = new SupabaseClient.TermDTO();
            dto.source_term = op.sourceTerm;
            dto.source_language = op.sourceLanguage;
            dto.target_term = op.targetTerm;
            dto.target_language = op.targetLanguage;
            dto.context = op.context;
            dto.contributor = op.contributor;
            dto.notes = op.notes;
            dto.content_hash = hash;
            dto.verified_status = op.status;
            dtos.add(dto);
        }

        try {
            int inserted = supabaseClient.batchInsertTerms(dtos);

            if (inserted > 0) {
                successful.addAll(addOps);
                System.out.println("[OfflineQueue] ✅ Batch ADD successful: " + inserted + "/" + addOps.size() + " operations");

                if (inserted < addOps.size()) {
                    System.out.println("[OfflineQueue] ⚠️ Some duplicates detected: " + (addOps.size() - inserted) + " skipped");
                }
            } else {
                failed.addAll(addOps);
                System.out.println("[OfflineQueue] ⚠️ Batch ADD failed - all duplicates or errors");
            }
        } catch (Exception e) {
            failed.addAll(addOps);
            System.err.println("[OfflineQueue] ❌ Batch ADD failed: " + e.getMessage());
            for (OfflineQueueManager.QueuedOperation op : addOps) {
                errorMessages.put(op, e.getMessage());
            }
        }
    }

    /**
     * Process large batches of ADD operations using parallel workers.
     * Splits operations into chunks and processes each chunk in parallel to prevent timeouts.
     */
    private void processBatchAddOperationsParallel(List<OfflineQueueManager.QueuedOperation> addOps,
                                                    List<OfflineQueueManager.QueuedOperation> successful,
                                                    List<OfflineQueueManager.QueuedOperation> failed,
                                                    Map<OfflineQueueManager.QueuedOperation, String> errorMessages) {
        try {
            // Calculate operations per worker
            int opsPerWorker = (int) Math.ceil((double) addOps.size() / PARALLEL_WORKER_COUNT);

            System.out.println("[OfflineQueue] 🚀 Starting parallel batch processing:");
            System.out.println("[OfflineQueue]   Total operations: " + addOps.size());
            System.out.println("[OfflineQueue]   Workers: " + PARALLEL_WORKER_COUNT);
            System.out.println("[OfflineQueue]   Operations per worker: ~" + opsPerWorker);

            // Create thread pool for parallel execution
            ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_WORKER_COUNT);
            List<Future<WorkerResult>> futures = new ArrayList<>();

            // Split operations into chunks and submit worker tasks
            for (int i = 0; i < PARALLEL_WORKER_COUNT; i++) {
                int startIndex = i * opsPerWorker;
                int endIndex = Math.min(startIndex + opsPerWorker, addOps.size());

                // Skip if no operations in this range
                if (startIndex >= addOps.size()) {
                    break;
                }

                final int workerIndex = i;
                final List<OfflineQueueManager.QueuedOperation> chunk =
                    new ArrayList<>(addOps.subList(startIndex, endIndex));

                System.out.println("[OfflineQueue] Worker " + workerIndex + " assigned: " +
                    chunk.size() + " operations (index " + startIndex + " to " + (endIndex-1) + ")");

                Future<WorkerResult> future = executor.submit(() ->
                    processWorkerChunk(workerIndex, chunk));

                futures.add(future);
            }

            // Collect results from all workers
            List<Integer> failedWorkers = new ArrayList<>();
            int totalInserted = 0;

            for (int i = 0; i < futures.size(); i++) {
                try {
                    WorkerResult result = futures.get(i).get();

                    successful.addAll(result.successfulOps);
                    failed.addAll(result.failedOps);
                    errorMessages.putAll(result.errors);
                    totalInserted += result.insertedCount;

                    System.out.println("[OfflineQueue] Worker " + i + " completed: " +
                        result.insertedCount + " inserted, " +
                        result.failedOps.size() + " failed");

                } catch (Exception e) {
                    failedWorkers.add(i);
                    System.err.println("[OfflineQueue] Worker " + i + " failed: " + e.getMessage());
                }
            }

            // Shutdown executor
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // RETRY LOGIC: Retry failed workers with exponential backoff
            if (!failedWorkers.isEmpty()) {
                System.out.println("[OfflineQueue] 🔄 Retrying " + failedWorkers.size() + " failed worker(s)...");

                for (int workerIndex : failedWorkers) {
                    int startIndex = workerIndex * opsPerWorker;
                    int endIndex = Math.min(startIndex + opsPerWorker, addOps.size());
                    List<OfflineQueueManager.QueuedOperation> chunk =
                        new ArrayList<>(addOps.subList(startIndex, endIndex));

                    try {
                        // Wait before retry (2 seconds)
                        Thread.sleep(2000);

                        System.out.println("[OfflineQueue] Retrying Worker " + workerIndex +
                            " (" + chunk.size() + " operations)...");

                        WorkerResult result = processWorkerChunk(workerIndex, chunk);

                        successful.addAll(result.successfulOps);
                        failed.addAll(result.failedOps);
                        errorMessages.putAll(result.errors);
                        totalInserted += result.insertedCount;

                        System.out.println("[OfflineQueue] ✅ Worker " + workerIndex +
                            " succeeded on retry: " + result.insertedCount + " inserted");

                    } catch (Exception e) {
                        // Retry failed - mark all operations as failed
                        failed.addAll(chunk);
                        System.err.println("[OfflineQueue] Worker " + workerIndex +
                            " retry failed: " + e.getMessage());
                        for (OfflineQueueManager.QueuedOperation op : chunk) {
                            errorMessages.put(op, "Worker retry failed: " + e.getMessage());
                        }
                    }
                }
            }

            System.out.println("[OfflineQueue] ✅ Parallel batch processing complete:");
            System.out.println("[OfflineQueue]   Total inserted: " + totalInserted);
            System.out.println("[OfflineQueue]   Successful operations: " + successful.size());
            System.out.println("[OfflineQueue]   Failed operations: " + failed.size());

        } catch (Exception e) {
            // If parallel processing completely fails, mark all as failed
            System.err.println("[OfflineQueue] ❌ Parallel processing failed: " + e.getMessage());
            failed.addAll(addOps);
            for (OfflineQueueManager.QueuedOperation op : addOps) {
                errorMessages.put(op, "Parallel processing error: " + e.getMessage());
            }
        }
    }

    /**
     * Process a chunk of operations for a single worker.
     * IMPORTANT: Processes the chunk in smaller batches of 50 to prevent timeouts.
     */
    private WorkerResult processWorkerChunk(int workerIndex, List<OfflineQueueManager.QueuedOperation> chunk) {
        WorkerResult result = new WorkerResult();

        System.out.println("[OfflineQueue] Worker " + workerIndex + " processing " + chunk.size() +
            " operations in batches of " + WORKER_BATCH_SIZE);

        try {
            // Process chunk in smaller batches to prevent timeout
            for (int batchStart = 0; batchStart < chunk.size(); batchStart += WORKER_BATCH_SIZE) {
                int batchEnd = Math.min(batchStart + WORKER_BATCH_SIZE, chunk.size());
                List<OfflineQueueManager.QueuedOperation> batchOps = chunk.subList(batchStart, batchEnd);

                // Convert batch operations to DTOs
                List<SupabaseClient.TermDTO> dtos = new ArrayList<>();
                for (OfflineQueueManager.QueuedOperation op : batchOps) {
                    String hash = syncHandler.generateHash(op.sourceTerm, op.sourceLanguage,
                            op.targetTerm, op.targetLanguage, op.context, op.contributor);

                    SupabaseClient.TermDTO dto = new SupabaseClient.TermDTO();
                    dto.source_term = op.sourceTerm;
                    dto.source_language = op.sourceLanguage;
                    dto.target_term = op.targetTerm;
                    dto.target_language = op.targetLanguage;
                    dto.context = op.context;
                    dto.contributor = op.contributor;
                    dto.notes = op.notes;
                    dto.content_hash = hash;
                    dto.verified_status = op.status;
                    dtos.add(dto);
                }

                try {
                    // Insert this small batch
                    int inserted = supabaseClient.batchInsertTerms(dtos);

                    if (inserted > 0) {
                        result.successfulOps.addAll(batchOps);
                        result.insertedCount += inserted;

                        if (inserted < batchOps.size()) {
                            System.out.println("[OfflineQueue] Worker " + workerIndex +
                                " batch " + (batchStart / WORKER_BATCH_SIZE + 1) +
                                ": " + inserted + "/" + batchOps.size() + " inserted (duplicates skipped)");
                        }
                    } else {
                        // No insertions - all duplicates (success, not failure)
                        result.successfulOps.addAll(batchOps);
                    }

                    // Log progress every 10 batches
                    int batchNum = batchStart / WORKER_BATCH_SIZE + 1;
                    if (batchNum % 10 == 0) {
                        System.out.println("[OfflineQueue] Worker " + workerIndex + " progress: " +
                            batchEnd + "/" + chunk.size() + " operations processed");
                    }

                } catch (Exception e) {
                    // Small batch failed - try splitting in half if batch is large enough
                    if (batchOps.size() >= 20 && e.getMessage() != null &&
                        (e.getMessage().contains("57014") || e.getMessage().contains("timeout"))) {

                        System.err.println("[OfflineQueue] Worker " + workerIndex +
                            " batch of " + batchOps.size() + " timed out - splitting in half and retrying...");

                        // Split batch in half and retry each half
                        int halfSize = batchOps.size() / 2;
                        List<List<OfflineQueueManager.QueuedOperation>> halves = Arrays.asList(
                            batchOps.subList(0, halfSize),
                            batchOps.subList(halfSize, batchOps.size())
                        );

                        for (List<OfflineQueueManager.QueuedOperation> half : halves) {
                            try {
                                List<SupabaseClient.TermDTO> halfDtos = new ArrayList<>();
                                for (OfflineQueueManager.QueuedOperation op : half) {
                                    String hash = syncHandler.generateHash(op.sourceTerm, op.sourceLanguage,
                                            op.targetTerm, op.targetLanguage, op.context, op.contributor);

                                    SupabaseClient.TermDTO dto = new SupabaseClient.TermDTO();
                                    dto.source_term = op.sourceTerm;
                                    dto.source_language = op.sourceLanguage;
                                    dto.target_term = op.targetTerm;
                                    dto.target_language = op.targetLanguage;
                                    dto.context = op.context;
                                    dto.contributor = op.contributor;
                                    dto.notes = op.notes;
                                    dto.content_hash = hash;
                                    dto.verified_status = op.status;
                                    halfDtos.add(dto);
                                }

                                int inserted = supabaseClient.batchInsertTerms(halfDtos);
                                if (inserted >= 0) {
                                    result.successfulOps.addAll(half);
                                    result.insertedCount += inserted;
                                    System.out.println("[OfflineQueue] Worker " + workerIndex +
                                        " half-batch retry succeeded: " + inserted + " inserted");
                                } else {
                                    result.failedOps.addAll(half);
                                }
                            } catch (Exception e2) {
                                // Half-batch also failed - mark as failed
                                result.failedOps.addAll(half);
                                String errorMsg = "Worker " + workerIndex + " half-batch retry failed: " + e2.getMessage();
                                System.err.println("[OfflineQueue] " + errorMsg);
                                for (OfflineQueueManager.QueuedOperation op : half) {
                                    result.errors.put(op, errorMsg);
                                }
                            }
                        }
                    } else {
                        // Batch too small to split or different error - mark as failed
                        result.failedOps.addAll(batchOps);
                        String errorMsg = "Worker " + workerIndex + " batch failed: " + e.getMessage();
                        System.err.println("[OfflineQueue] " + errorMsg);

                        for (OfflineQueueManager.QueuedOperation op : batchOps) {
                            result.errors.put(op, errorMsg);
                        }
                    }
                }

                // Small delay between batches to avoid rate limiting (50ms)
                if (batchEnd < chunk.size()) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            System.out.println("[OfflineQueue] Worker " + workerIndex + " finished: " +
                result.insertedCount + " inserted, " +
                result.failedOps.size() + " failed");

        } catch (Exception e) {
            // Worker completely failed - mark all remaining operations as failed
            for (OfflineQueueManager.QueuedOperation op : chunk) {
                if (!result.successfulOps.contains(op) && !result.failedOps.contains(op)) {
                    result.failedOps.add(op);
                    result.errors.put(op, "Worker error: " + e.getMessage());
                }
            }
            System.err.println("[OfflineQueue] Worker " + workerIndex + " error: " + e.getMessage());
        }

        return result;
    }

    /**
     * Result from a worker processing a chunk of operations.
     */
    private static class WorkerResult {
        List<OfflineQueueManager.QueuedOperation> successfulOps = new ArrayList<>();
        List<OfflineQueueManager.QueuedOperation> failedOps = new ArrayList<>();
        Map<OfflineQueueManager.QueuedOperation, String> errors = new HashMap<>();
        int insertedCount = 0;
    }

    /**
     * Process EDIT operations individually.
     */
    private void processEditOperations(List<OfflineQueueManager.QueuedOperation> editOps,
                                        List<OfflineQueueManager.QueuedOperation> successful,
                                        List<OfflineQueueManager.QueuedOperation> failed,
                                        Map<OfflineQueueManager.QueuedOperation, String> errorMessages) {
        System.out.println("[OfflineQueue] 🔄 Processing " + editOps.size() + " EDIT operations...");

        for (OfflineQueueManager.QueuedOperation op : editOps) {
            try {
                SyncEditParams params = new SyncEditParams(op.sourceTerm, op.sourceLanguage,
                        op.targetTerm, op.targetLanguage, op.context,
                        op.contributor, op.notes, op.originalHash, op.status);
                syncHandler.syncEditToCloudBlocking(op.termId, params);
                successful.add(op);
                System.out.println("[OfflineQueue] ✅ EDIT: " + op.sourceTerm);
            } catch (Exception e) {
                failed.add(op);
                errorMessages.put(op, e.getMessage());
                System.err.println("[OfflineQueue] ❌ EDIT failed: " + e.getMessage());
            }
        }
    }

    /**
     * Process DELETE operations individually.
     */
    private void processDeleteOperations(List<OfflineQueueManager.QueuedOperation> deleteOps,
                                          List<OfflineQueueManager.QueuedOperation> successful,
                                          List<OfflineQueueManager.QueuedOperation> failed,
                                          Map<OfflineQueueManager.QueuedOperation, String> errorMessages) {
        System.out.println("[OfflineQueue] 🔄 Processing " + deleteOps.size() + " DELETE operations...");

        for (OfflineQueueManager.QueuedOperation op : deleteOps) {
            try {
                String hash = op.originalHash != null ? op.originalHash :
                        syncHandler.generateHash(op.sourceTerm, op.sourceLanguage,
                                op.targetTerm, op.targetLanguage, op.context, op.contributor);

                boolean deleted = supabaseClient.deleteTermByHash(hash);
                if (deleted) {
                    successful.add(op);
                    System.out.println("[OfflineQueue] ✅ DELETE: " + op.sourceTerm);
                } else {
                    successful.add(op);
                    System.out.println("[OfflineQueue] ⚠️ DELETE: Term not in cloud (already deleted): " + op.sourceTerm);
                }
            } catch (Exception e) {
                failed.add(op);
                errorMessages.put(op, e.getMessage());
                System.err.println("[OfflineQueue] ❌ DELETE failed: " + e.getMessage());
            }
        }
    }

    /**
     * Get count of queued operations.
     */
    public int getQueuedOperationsCount() {
        return offlineQueue.getPendingCount();
    }

    /**
     * Check if there are queued operations.
     */
    public boolean hasQueuedOperations() {
        return offlineQueue.hasPendingOperations();
    }

    /**
     * Get queue status as string.
     */
    public String getQueueStatus() {
        int count = offlineQueue.getPendingCount();
        return count == 0 ? "Nenhuma operação pendente" : count + " operação(ões) pendente(s)";
    }

    /**
     * Get reference to the offline queue manager.
     */
    public OfflineQueueManager getOfflineQueue() {
        return offlineQueue;
    }

    /**
     * Clear offline queue with confirmation.
     */
    public void clearOfflineQueueWithConfirmation() {
        Platform.runLater(() -> {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Limpar Fila Offline");
            confirmAlert.setHeaderText("Tem certeza?");
            confirmAlert.setContentText("Isso irá descartar " + offlineQueue.getPendingCount() +
                                       " operação(ões) pendente(s) na fila offline.\n\nEsta ação não pode ser desfeita.");

            confirmAlert.showAndWait().ifPresent(response -> {
                if (response == javafx.scene.control.ButtonType.OK) {
                    int cleared = offlineQueue.getPendingCount();
                    offlineQueue.clearQueue();

                    Alert resultAlert = new Alert(Alert.AlertType.INFORMATION);
                    resultAlert.setTitle("Fila Limpa");
                    resultAlert.setHeaderText(null);
                    resultAlert.setContentText("Fila offline limpa: " + cleared + " operação(ões) removida(s).");
                    resultAlert.showAndWait();

                    if (networkMonitor != null) {
                        networkMonitor.clearOperationMessage();
                    }
                }
            });
        });
    }
}
