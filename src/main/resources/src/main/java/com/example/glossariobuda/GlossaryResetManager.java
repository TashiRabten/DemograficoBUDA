package com.example.glossariobuda;

import com.example.glossariobuda.exceptions.CloudOperationException;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Refactored service responsible for handling glossary reset operations.
 * Manages version checking, date-based synchronization, database backup, and application restart.
 */
public class GlossaryResetManager {

    private static final Logger logger = Logger.getLogger(GlossaryResetManager.class.getName());
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int USER_CONFIRMATION_TIMEOUT_SECONDS = 60;
    private static final int PROCESS_START_TIMEOUT_SECONDS = 5;

    private final SupabaseClient supabaseClient;
    private final Connection localConnection;
    private final OfflineQueueManager offlineQueue;
    private final NetworkStatusMonitor networkMonitor;
    private final Object dbLock;
    private final ApplicationRestarter restarter;

    private volatile boolean resetInProgress = false;

    // Callback interfaces
    public interface ConnectionChecker {
        boolean isOnline();
    }

    public interface ProgressCallback {
        void onProgress(String message, int current, int total);
        void onComplete();
    }

    public interface StatusUpdater {
        void showStatusTemporarily(String message, int durationMs);
    }

    public interface MetadataManager {
        int getLocalGlossaryVersion();
        void updateLocalGlossaryVersion(int version);
        void updateLastSyncTime();
    }

    public interface TermInserter {
        List<SupabaseClient.TermDTO> batchInsertTermsForReset(List<SupabaseClient.TermDTO> terms) throws SQLException;
    }

    public interface QueueProcessor {
        int processOfflineQueueBatch();
    }

    private ConnectionChecker connectionChecker;
    private ProgressCallback progressCallback;
    private StatusUpdater statusUpdater;
    private MetadataManager metadataManager;
    private TermInserter termInserter;
    private QueueProcessor queueProcessor;

    public GlossaryResetManager(SupabaseClient supabaseClient,
                                Connection localConnection,
                                OfflineQueueManager offlineQueue,
                                NetworkStatusMonitor networkMonitor,
                                Object dbLock) {
        this(supabaseClient, localConnection, offlineQueue, networkMonitor, dbLock,
                new ApplicationRestarter());
    }

    // Constructor with dependency injection for testing
    GlossaryResetManager(SupabaseClient supabaseClient,
                         Connection localConnection,
                         OfflineQueueManager offlineQueue,
                         NetworkStatusMonitor networkMonitor,
                         Object dbLock,
                         ApplicationRestarter restarter) {
        this.supabaseClient = supabaseClient;
        this.localConnection = localConnection;
        this.offlineQueue = offlineQueue;
        this.networkMonitor = networkMonitor;
        this.dbLock = dbLock;
        this.restarter = restarter;
    }

    // Setters for callbacks
    public void setConnectionChecker(ConnectionChecker checker) {
        this.connectionChecker = checker;
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    public void setStatusUpdater(StatusUpdater updater) {
        this.statusUpdater = updater;
    }

    public void setMetadataManager(MetadataManager manager) {
        this.metadataManager = manager;
    }

    public void setTermInserter(TermInserter inserter) {
        this.termInserter = inserter;
    }

    public void setQueueProcessor(QueueProcessor processor) {
        this.queueProcessor = processor;
    }

    public boolean isResetInProgress() {
        return resetInProgress;
    }

    /**
     * Check if cloud glossary has been reset and handle it if necessary.
     * Returns true if reset was detected and handled.
     */
    public boolean checkAndHandleGlossaryReset() {
        try {
            if (connectionChecker != null && !connectionChecker.isOnline()) {
                logger.info("Offline - skipping version check");
                return false;
            }

            SupabaseClient.GlossaryMetadata cloudMetadata = supabaseClient.getGlossaryMetadata();
            int cloudVersion = cloudMetadata.version;
            int localVersion = metadataManager != null ? metadataManager.getLocalGlossaryVersion() : 1;

            logger.info(String.format("Glossary version check: Local=%d, Cloud=%d", localVersion, cloudVersion));

            if (cloudVersion > localVersion) {
                logger.warning(String.format("GLOSSARY RESET DETECTED! Cloud: %d, Local: %d, Reason: %s",
                        cloudVersion, localVersion, cloudMetadata.lastResetReason));

                boolean shouldSync = confirmFullSync(cloudMetadata);

                if (shouldSync) {
                    validateAndPerformSync(cloudMetadata, cloudVersion);
                    return true;
                }
            } else {
                logger.info("Glossary version is up to date");
            }

            return false;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error checking glossary version", e);
            return false;
        }
    }

    private void validateAndPerformSync(SupabaseClient.GlossaryMetadata metadata, int cloudVersion) {
        String startDate = metadata.resetStartDate;
        String endDate = metadata.resetEndDate;

        if ((startDate != null && !startDate.isEmpty()) || (endDate != null && !endDate.isEmpty())) {
            logger.info(String.format("Using date range for reset: %s to %s", startDate, endDate));
            performDateBasedSync(startDate, endDate, cloudVersion);
        } else {
            logger.severe("Reset detected but no date range specified! Cannot perform date-based sync.");
            showError("Erro de Sincronização",
                    "Reset detectado mas sem intervalo de datas especificado.");
        }
    }

    /**
     * Perform date-based synchronization after reset detection.
     */
    public void performDateBasedSync(String startDate, String endDate, int newVersion) {
        try {
            resetInProgress = true;
            logSyncStart(startDate, endDate, newVersion);

            updateProgress("Criando backup da base local...", 0, 100);
            updateSyncStatus("Reset detectado - criando backup...");

            createDatabaseBackup(newVersion);
            updateLocalVersion(newVersion);

            SyncContext context = prepareSyncContext(startDate, endDate);
            clearLocalDatabase(context.queuedTermIds);

            List<SupabaseClient.TermDTO> cloudTerms = downloadCloudTerms(startDate, endDate);
            SyncResult result = importTerms(cloudTerms, context);

            finalizeSync(result, context);
            showRestartDialog(result);

        } catch (Exception e) {
            handleSyncError(e);
        } finally {
            resetInProgress = false;
        }
    }

    private void logSyncStart(String startDate, String endDate, int newVersion) {
        logger.info("═══════════════════════════════════");
        logger.info("Starting DATE-RANGE SYNC (Reset Detected)");
        logger.info(String.format("Date range: %s to %s", startDate, endDate));
        logger.info(String.format("New version: %d", newVersion));
        logger.info("═══════════════════════════════════");
    }

    private void updateLocalVersion(int newVersion) {
        logger.info(String.format("Updating local version to %d", newVersion));
        if (metadataManager != null) {
            metadataManager.updateLocalGlossaryVersion(newVersion);
        }
        logger.info("Version updated - will resume from here if interrupted");
    }

    private SyncContext prepareSyncContext(String startDate, String endDate) throws SQLException {
        updateProgress("Verificando operações pendentes...", 10, 100);

        Set<Integer> queuedTermIds = getQueuedTermIds();
        logger.info(String.format("Found %d terms in offline queue - will preserve", queuedTermIds.size()));

        List<SupabaseClient.TermDTO> protectedLocalTerms =
                getLocalTermsOutsideRange(startDate, endDate, queuedTermIds);
        logger.info(String.format("Found %d local terms outside range - will preserve",
                protectedLocalTerms.size()));

        debugDateRangeQuery(startDate, endDate);

        return new SyncContext(queuedTermIds, protectedLocalTerms);
    }

    private void clearLocalDatabase(Set<Integer> queuedTermIds) throws SQLException {
        updateProgress("Limpando base local...", 15, 100);
        logger.info("Clearing local database...");

        String clearSQL = buildClearSQL(queuedTermIds);

        try (Statement stmt = localConnection.createStatement()) {
            int totalDeleted;
            synchronized (dbLock) {
                totalDeleted = stmt.executeUpdate(clearSQL);
            }
            logger.info(String.format("Cleared %d terms from local database", totalDeleted));
        }
    }

    private String buildClearSQL(Set<Integer> queuedTermIds) {
        if (queuedTermIds.isEmpty()) {
            return "DELETE FROM terms";
        }

        String ids = queuedTermIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        return "DELETE FROM terms WHERE id NOT IN (" + ids + ")";
    }

    private List<SupabaseClient.TermDTO> downloadCloudTerms(String startDate, String endDate) {
        updateProgress("Baixando termos do cloud...", 30, 100);
        logger.info("Downloading ALL terms from Supabase...");

        List<SupabaseClient.TermDTO> termsInRange = null;
        try {
            termsInRange = supabaseClient.getTermsInDateRange(startDate, endDate);
        } catch (CloudOperationException e) {
            throw new RuntimeException(e);
        }
        logger.info(String.format("Downloaded %d terms in range", termsInRange.size()));

        List<SupabaseClient.TermDTO> termsOutsideRange =
                null;
        try {
            termsOutsideRange = supabaseClient.getTermsOutsideDateRange(startDate, endDate);
        } catch (CloudOperationException e) {
            throw new RuntimeException(e);
        }
        logger.info(String.format("Downloaded %d terms outside range", termsOutsideRange.size()));

        List<SupabaseClient.TermDTO> allTerms = new ArrayList<>();
        allTerms.addAll(termsInRange);
        allTerms.addAll(termsOutsideRange);

        return allTerms;
    }

    private SyncResult importTerms(List<SupabaseClient.TermDTO> cloudTerms, SyncContext context)
            throws SQLException {
        updateProgress(String.format("Importando %d termos...", cloudTerms.size()),
                50, cloudTerms.size() + 50);

        logger.info(String.format("BATCH inserting %d terms from cloud...", cloudTerms.size()));

        int imported = 0;
        int skipped = 0;

        if (termInserter != null) {
            List<SupabaseClient.TermDTO> importedDTOs =
                    termInserter.batchInsertTermsForReset(cloudTerms);
            imported = importedDTOs.size();
            skipped = cloudTerms.size() - imported;

            logger.info(String.format("Batch import complete: %d terms inserted%s",
                    imported, skipped > 0 ? ", " + skipped + " skipped" : ""));
        }

        int reinserted = reinsertLocalTerms(context.protectedLocalTerms, cloudTerms);

        return new SyncResult(imported, skipped, reinserted, context.queuedTermIds.size());
    }

    private int reinsertLocalTerms(List<SupabaseClient.TermDTO> protectedTerms,
                                   List<SupabaseClient.TermDTO> cloudTerms) throws SQLException {
        if (protectedTerms.isEmpty()) {
            return 0;
        }

        logger.info("Re-inserting local-only terms...");

        Set<String> cloudHashes = cloudTerms.stream()
                .map(t -> t.content_hash)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<SupabaseClient.TermDTO> uniqueLocalTerms = protectedTerms.stream()
                .filter(term -> term.content_hash == null || !cloudHashes.contains(term.content_hash))
                .collect(Collectors.toList());

        int skippedLocal = protectedTerms.size() - uniqueLocalTerms.size();

        if (!uniqueLocalTerms.isEmpty() && termInserter != null) {
            List<SupabaseClient.TermDTO> reinsertedDTOs =
                    termInserter.batchInsertTermsForReset(uniqueLocalTerms);
            int reinserted = reinsertedDTOs.size();
            logger.info(String.format("Batch re-inserted %d local-only terms, skipped %d duplicates",
                    reinserted, skippedLocal));
            return reinserted;
        }

        return 0;
    }

    private void finalizeSync(SyncResult result, SyncContext context) {
        if (metadataManager != null) {
            metadataManager.updateLastSyncTime();
        }

        if (!context.queuedTermIds.isEmpty()) {
            logger.warning(String.format("%d local changes preserved - will sync when online",
                    context.queuedTermIds.size()));
            showQueuedOperationsWarning(context.queuedTermIds.size());
        }

        updateProgress("Sincronização concluída!", 100, 100);
        if (progressCallback != null) {
            progressCallback.onComplete();
        }

        updateSyncStatus(null);
        displaySyncSummary(result);
        processOfflineQueue(context.queuedTermIds);
    }

    private void displaySyncSummary(SyncResult result) {
        String msg = String.format("Reset concluído! %d termos sincronizados", result.imported);
        if (result.reinserted > 0) {
            msg += String.format(", %d locais preservados", result.reinserted);
        }
        if (result.queued > 0) {
            msg += String.format(" (%d pendentes)", result.queued);
        }

        if (statusUpdater != null) {
            statusUpdater.showStatusTemporarily(msg, 3000);
        }

        logger.info("═══════════════════════════════════");
        logger.info("DATE-RANGE SYNC COMPLETE");
        logger.info(String.format("Imported: %d terms (skipped %d)", result.imported, result.skipped));
        logger.info(String.format("Local-only preserved: %d terms", result.reinserted));
        logger.info(String.format("Queued for sync: %d operations", result.queued));
        logger.info("═══════════════════════════════════");
    }

    private void processOfflineQueue(Set<Integer> queuedTermIds) {
        if (!queuedTermIds.isEmpty() && queueProcessor != null) {
            logger.info("Processing offline queue after reset...");
            try {
                int processed = queueProcessor.processOfflineQueueBatch();
                logger.info(String.format("Offline queue processed: %d operations", processed));

                if (networkMonitor != null && processed > 0 && statusUpdater != null) {
                    statusUpdater.showStatusTemporarily(
                            String.format("Reset + %d operações sincronizadas", processed), 3000);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error processing offline queue", e);
            }
        }
    }

    private void handleSyncError(Exception e) {
        logger.log(Level.SEVERE, "Error during date-based sync", e);

        if (networkMonitor != null) {
            networkMonitor.setSyncing(null);
            networkMonitor.setSyncError("Erro no reset: " + e.getMessage());
        }
    }

    /**
     * Get IDs of terms that have queued operations.
     */
    private Set<Integer> getQueuedTermIds() {
        Set<Integer> ids = new HashSet<>();
        List<OfflineQueueManager.QueuedOperation> operations = offlineQueue.getPendingOperations();

        for (OfflineQueueManager.QueuedOperation op : operations) {
            if (("ADD".equals(op.operation) || "EDIT".equals(op.operation)) && op.termId > 0) {
                ids.add(op.termId);
            }
        }

        return ids;
    }

    /**
     * Get local terms that fall outside the specified date range.
     */
    private List<SupabaseClient.TermDTO> getLocalTermsOutsideRange(String startDate, String endDate,
                                                                   Set<Integer> excludeQueuedIds)
            throws SQLException {
        List<SupabaseClient.TermDTO> terms = new ArrayList<>();

        logger.info(String.format("Getting local terms outside range: %s to %s", startDate, endDate));

        String query = buildOutsideRangeQuery(startDate, endDate, excludeQueuedIds);

        try (PreparedStatement pstmt = localConnection.prepareStatement(query)) {
            setQueryParameters(pstmt, startDate, endDate, excludeQueuedIds);

            try (ResultSet rs = pstmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    terms.add(mapResultSetToTerm(rs));
                    count++;

                    if (count <= 3) {
                        logger.info(String.format("Protected term example: %s (%s)",
                                rs.getString("source_term"), rs.getString("date_added")));
                    }
                }
                logger.info(String.format("Found %d terms outside range to protect", count));
            }
        }

        return terms;
    }

    private String buildOutsideRangeQuery(String startDate, String endDate, Set<Integer> excludeIds) {
        StringBuilder query = new StringBuilder("SELECT * FROM terms WHERE ");

        if (startDate != null && endDate != null) {
            query.append("(date_added < ? OR date_added > ?)");
        } else if (startDate != null) {
            query.append("date_added < ?");
        } else if (endDate != null) {
            query.append("date_added > ?");
        } else {
            return null;
        }

        if (!excludeIds.isEmpty()) {
            query.append(" AND id NOT IN (");
            query.append(String.join(",", Collections.nCopies(excludeIds.size(), "?")));
            query.append(")");
        }

        query.append(" ORDER BY date_added ASC");

        return query.toString();
    }

    private void setQueryParameters(PreparedStatement pstmt, String startDate, String endDate,
                                    Set<Integer> excludeIds) throws SQLException {
        int paramIndex = 1;

        if (startDate != null && endDate != null) {
            pstmt.setString(paramIndex++, startDate);
            pstmt.setString(paramIndex++, endDate);
        } else if (startDate != null) {
            pstmt.setString(paramIndex++, startDate);
        } else if (endDate != null) {
            pstmt.setString(paramIndex++, endDate);
        }

        for (Integer id : excludeIds) {
            pstmt.setInt(paramIndex++, id);
        }
    }

    private SupabaseClient.TermDTO mapResultSetToTerm(ResultSet rs) throws SQLException {
        SupabaseClient.TermDTO term = new SupabaseClient.TermDTO();
        term.id = rs.getInt("id");
        term.source_term = rs.getString("source_term");
        term.source_language = rs.getString("source_language");
        term.target_term = rs.getString("target_term");
        term.target_language = rs.getString("target_language");
        term.context = rs.getString("context");
        term.contributor = rs.getString("contributor");
        term.notes = rs.getString("notes");
        term.date_added = rs.getString("date_added");
        term.verified_status = rs.getString("verified_status");
        term.content_hash = rs.getString("content_hash");
        term.owner = rs.getString("owner");
        return term;
    }

    /**
     * Debug date range query - print information about terms in/out of range.
     */
    private void debugDateRangeQuery(String startDate, String endDate) {
        logger.info("═══════════════════════════════════");
        logger.info("DATE RANGE QUERY DEBUG");
        logger.info(String.format("Start: %s, End: %s", startDate, endDate));

        debugTermsInRange(startDate, endDate);
        debugTermsOutsideRange(startDate, endDate);

        logger.info("═══════════════════════════════════");
    }

    private void debugTermsInRange(String startDate, String endDate) {
        String query = "SELECT COUNT(*) as count, MIN(date_added) as first, MAX(date_added) as last " +
                "FROM terms WHERE date_added >= ? AND date_added <= ?";

        try (PreparedStatement pstmt = localConnection.prepareStatement(query)) {
            pstmt.setString(1, startDate);
            pstmt.setString(2, endDate);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("count");
                    logger.info(String.format("Terms IN range (to be deleted): %d", count));

                    if (count > 0) {
                        logger.info(String.format("  First: %s, Last: %s",
                                rs.getString("first"), rs.getString("last")));
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error querying IN range", e);
        }
    }

    private void debugTermsOutsideRange(String startDate, String endDate) {
        String query = "SELECT COUNT(*) as count, MIN(date_added) as first, MAX(date_added) as last " +
                "FROM terms WHERE date_added < ? OR date_added > ?";

        try (PreparedStatement pstmt = localConnection.prepareStatement(query)) {
            pstmt.setString(1, startDate);
            pstmt.setString(2, endDate);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("count");
                    logger.info(String.format("Terms OUTSIDE range (to be preserved): %d", count));

                    if (count > 0) {
                        logger.info(String.format("  First: %s, Last: %s",
                                rs.getString("first"), rs.getString("last")));
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error querying OUTSIDE range", e);
        }
    }

    /**
     * Create backup of local database.
     */
    private void createDatabaseBackup(int newVersion) {
        try {
            String dbPath = localConnection.getMetaData().getURL();
            if (dbPath.startsWith("jdbc:sqlite:")) {
                dbPath = dbPath.substring(12);
            }

            File dbFile = new File(dbPath);
            if (!dbFile.exists()) {
                logger.warning("Database file not found: " + dbPath);
                return;
            }

            Path backupPath = getBackupPath(newVersion);
            Files.copy(dbFile.toPath(), backupPath, StandardCopyOption.REPLACE_EXISTING);

            logger.info("Backup created: " + backupPath.toAbsolutePath());

        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to create backup", e);
        }
    }

    private Path getBackupPath(int newVersion) throws IOException {
        String documentsPath = System.getProperty("user.home") + File.separator + "Documents";
        Path glossarioDir = Paths.get(documentsPath, ".glossariobuda");

        if (!Files.exists(glossarioDir)) {
            Files.createDirectories(glossarioDir);
        }

        String backupFileName = "database_backup_v" + (newVersion - 1) + ".db";
        return glossarioDir.resolve(backupFileName);
    }

    /**
     * Show dialog asking user to confirm full sync.
     */
    private boolean confirmFullSync(SupabaseClient.GlossaryMetadata cloudMetadata) {
        final CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Atualização do Glossário");
                alert.setHeaderText("Nova versão do glossário detectada");
                alert.setContentText(buildConfirmationMessage(cloudMetadata));

                alert.showAndWait();
                logger.info("User acknowledged glossary update");
            } finally {
                latch.countDown();
            }
        });

        try {
            if (!latch.await(USER_CONFIRMATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warning("Timeout waiting for user acknowledgment");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "Interrupted while waiting for user", e);
        }

        return true;
    }

    private String buildConfirmationMessage(SupabaseClient.GlossaryMetadata metadata) {
        return String.format(
                "O glossário foi atualizado no servidor.\n\n" +
                        "Versão do servidor: %d\n" +
                        "Motivo: %s\n" +
                        "Data: %s\n\n" +
                        "O que vai acontecer:\n" +
                        "1. Backup automático da sua base local\n" +
                        "2. Download do glossário atualizado\n" +
                        "3. Sincronização completa\n\n" +
                        "Clique OK para continuar.",
                metadata.version, metadata.lastResetReason, metadata.lastResetDate
        );
    }

    /**
     * Show warning about queued operations that were preserved.
     */
    private void showQueuedOperationsWarning(int count) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Alterações Locais Preservadas");
            alert.setHeaderText("Reset concluído com sucesso");
            alert.setContentText(String.format(
                    "O glossário foi atualizado, mas você tem %d alteração(ões) local(is) " +
                            "que ainda não foram sincronizadas.\n\n" +
                            "Estas alterações foram preservadas e serão enviadas automaticamente " +
                            "para o servidor assim que possível.\n\n" +
                            "Você pode verificar o status na barra de status.",
                    count
            ));

            alert.getDialogPane().setPrefWidth(500);
            alert.showAndWait();
        });
    }

    /**
     * Show dialog offering to restart application.
     */
    private void showRestartDialog(SyncResult result) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Reset Concluído");
            alert.setHeaderText("Glossário sincronizado com sucesso!");
            alert.setContentText(buildRestartMessage(result));

            alert.getDialogPane().setPrefWidth(500);
            alert.getDialogPane().setPrefHeight(300);

            alert.getButtonTypes().setAll(
                    new ButtonType("Reiniciar Agora", ButtonBar.ButtonData.YES),
                    new ButtonType("Depois", ButtonBar.ButtonData.NO)
            );

            alert.showAndWait().ifPresent(response -> {
                if (response.getButtonData() == ButtonBar.ButtonData.YES) {
                    restartApplication();
                } else {
                    logger.info("User declined restart - app will continue running");
                }
            });
        });
    }

    private String buildRestartMessage(SyncResult result) {
        StringBuilder msg = new StringBuilder();
        msg.append("Resumo da sincronização:\n\n");
        msg.append(String.format("✅ Termos importados: %d\n", result.imported));

        if (result.reinserted > 0) {
            msg.append(String.format("📌 Termos locais preservados: %d\n", result.reinserted));
        }
        if (result.queued > 0) {
            msg.append(String.format("⏳ Operações pendentes: %d\n", result.queued));
        }

        msg.append("\n");
        msg.append("Recomendamos reiniciar o aplicativo para garantir que\n");
        msg.append("todos os dados sejam carregados corretamente.\n\n");
        msg.append("Deseja reiniciar agora?");

        return msg.toString();
    }

    /**
     * Restart the application.
     */
    public void restartApplication() {
        try {
            logger.info("Restarting application...");
            restarter.restart();
        } catch (RestartException e) {
            logger.log(Level.SEVERE, "Failed to restart application", e);
            showRestartError();
        }
    }

    private void showRestartError() {
        Platform.runLater(() -> {
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("Erro ao Reiniciar");
            errorAlert.setHeaderText(null);
            errorAlert.setContentText(
                    "Não foi possível reiniciar automaticamente.\n\n" +
                            "Por favor, feche e abra o aplicativo manualmente."
            );
            errorAlert.getDialogPane().setPrefWidth(450);
            errorAlert.showAndWait();
        });
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void updateProgress(String message, int current, int total) {
        if (progressCallback != null) {
            progressCallback.onProgress(message, current, total);
        }
    }

    private void updateSyncStatus(String status) {
        if (networkMonitor != null) {
            networkMonitor.setSyncing(status);
        }
    }

    // Helper classes

    /**
     * Context object holding sync operation state.
     */
    private static class SyncContext {
        final Set<Integer> queuedTermIds;
        final List<SupabaseClient.TermDTO> protectedLocalTerms;

        SyncContext(Set<Integer> queuedTermIds, List<SupabaseClient.TermDTO> protectedLocalTerms) {
            this.queuedTermIds = queuedTermIds;
            this.protectedLocalTerms = protectedLocalTerms;
        }
    }

    /**
     * Result object holding sync operation results.
     */
    private static class SyncResult {
        final int imported;
        final int skipped;
        final int reinserted;
        final int queued;

        SyncResult(int imported, int skipped, int reinserted, int queued) {
            this.imported = imported;
            this.skipped = skipped;
            this.reinserted = reinserted;
            this.queued = queued;
        }
    }

    /**
     * Operating system detection enum.
     */
    enum OperatingSystem {
        WINDOWS, MAC, LINUX, OTHER;

        static OperatingSystem detect() {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) return WINDOWS;
            if (os.contains("mac")) return MAC;
            if (os.contains("linux")) return LINUX;
            return OTHER;
        }
    }

    /**
     * Exception thrown when application restart fails.
     */
    static class RestartException extends Exception {
        RestartException(String message) {
            super(message);
        }

        RestartException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Handles application restart logic with platform-specific implementations.
     */
    static class ApplicationRestarter {
        private static final Logger logger = Logger.getLogger(ApplicationRestarter.class.getName());
        private static final Map<OperatingSystem, List<String>> EXECUTABLE_PATHS = initializePaths();

        private static Map<OperatingSystem, List<String>> initializePaths() {
            Map<OperatingSystem, List<String>> paths = new EnumMap<>(OperatingSystem.class);
            String userHome = System.getProperty("user.home");

            paths.put(OperatingSystem.WINDOWS, Arrays.asList(
                    "C:\\Program Files\\GlossarioBUDA\\GlossarioBUDA.exe",
                    "C:\\Program Files (x86)\\GlossarioBUDA\\GlossarioBUDA.exe",
                    userHome + "\\AppData\\Local\\GlossarioBUDA\\GlossarioBUDA.exe",
                    userHome + "\\AppData\\Roaming\\GlossarioBUDA\\GlossarioBUDA.exe",
                    "./GlossarioBUDA.exe"
            ));

            paths.put(OperatingSystem.MAC, Arrays.asList(
                    "/Applications/GlossarioBUDA.app/Contents/MacOS/GlossarioBUDA",
                    userHome + "/Applications/GlossarioBUDA.app/Contents/MacOS/GlossarioBUDA",
                    "/Applications/GlossarioBUDA.app",
                    userHome + "/Applications/GlossarioBUDA.app",
                    "./GlossarioBUDA"
            ));

            paths.put(OperatingSystem.LINUX, Arrays.asList(
                    "/usr/local/bin/glossariobuda",
                    "/usr/bin/glossariobuda",
                    userHome + "/.local/bin/glossariobuda",
                    "/opt/glossariobuda/glossariobuda",
                    "./glossariobuda"
            ));

            return paths;
        }

        /**
         * Restart the application.
         */
        public void restart() throws RestartException {
            Optional<Path> executable = findExecutable();

            if (executable.isPresent()) {
                launchExecutable(executable.get());
            } else {
                Optional<Path> jar = findJarPath();
                if (jar.isPresent()) {
                    launchJar(jar.get());
                } else {
                    logger.info("Running from IDE - performing graceful shutdown");
                    gracefulShutdown();
                }
            }
        }

        /**
         * Find the application executable.
         */
        private Optional<Path> findExecutable() {
            OperatingSystem platform = OperatingSystem.detect();
            List<String> paths = EXECUTABLE_PATHS.getOrDefault(platform, Collections.emptyList());

            logger.info(String.format("Searching for executable on platform: %s", platform));

            for (String pathStr : paths) {
                try {
                    Path path = Paths.get(pathStr).toAbsolutePath().normalize();
                    if (isValidExecutable(path)) {
                        logger.info("Found executable at: " + path);
                        return Optional.of(path);
                    }
                } catch (Exception e) {
                    logger.fine("Could not resolve path: " + pathStr);
                }
            }

            logger.info("No executable found in standard locations");
            return Optional.empty();
        }

        /**
         * Find the JAR file path.
         */
        private Optional<Path> findJarPath() {
            try {
                Path jarPath = Paths.get(
                        GlossaryResetManager.class.getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI()
                ).toAbsolutePath().normalize();

                if (jarPath.toString().endsWith(".jar") && Files.isRegularFile(jarPath)) {
                    logger.info("Found JAR at: " + jarPath);
                    return Optional.of(jarPath);
                }
            } catch (Exception e) {
                logger.fine("Could not determine JAR path");
            }
            return Optional.empty();
        }

        /**
         * Validate that a path points to a valid executable.
         */
        private boolean isValidExecutable(Path path) {
            return Files.isRegularFile(path) && Files.isExecutable(path);
        }

        /**
         * Launch the application executable.
         */
        private void launchExecutable(Path executable) throws RestartException {
            try {
                ProcessBuilder pb = new ProcessBuilder(executable.toString());

                Path parentDir = executable.getParent();
                if (parentDir != null && Files.isDirectory(parentDir)) {
                    pb.directory(parentDir.toFile());
                }

                pb.inheritIO();
                Process process = pb.start();

                // Wait briefly to ensure process started successfully
                if (!waitForProcessStart(process)) {
                    throw new RestartException("Process failed to start within timeout");
                }

                logger.info("New process started successfully - exiting current instance");
                exitApplication();

            } catch (IOException e) {
                throw new RestartException("Failed to launch executable: " + executable, e);
            }
        }

        /**
         * Launch the application from JAR file.
         */
        private void launchJar(Path jarPath) throws RestartException {
            try {
                Path javaBin = getJavaBinary();

                ProcessBuilder pb = new ProcessBuilder(
                        javaBin.toString(),
                        "-jar",
                        jarPath.toString()
                );

                Path jarParent = jarPath.getParent();
                if (jarParent != null && Files.isDirectory(jarParent)) {
                    pb.directory(jarParent.toFile());
                }

                pb.inheritIO();
                Process process = pb.start();

                if (!waitForProcessStart(process)) {
                    throw new RestartException("JAR process failed to start within timeout");
                }

                logger.info("JAR process started successfully - exiting current instance");
                exitApplication();

            } catch (IOException e) {
                throw new RestartException("Failed to launch JAR: " + jarPath, e);
            }
        }

        /**
         * Get the path to the Java binary.
         */
        private Path getJavaBinary() throws RestartException {
            String javaHome = System.getProperty("java.home");
            if (javaHome == null) {
                throw new RestartException("java.home property not set");
            }

            Path javaBin = Paths.get(javaHome, "bin", "java").toAbsolutePath().normalize();

            // Windows requires .exe extension
            if (OperatingSystem.detect() == OperatingSystem.WINDOWS) {
                javaBin = Paths.get(javaHome, "bin", "java.exe").toAbsolutePath().normalize();
            }

            if (!isValidExecutable(javaBin)) {
                throw new RestartException("Java binary not found or not executable: " + javaBin);
            }

            return javaBin;
        }

        /**
         * Wait for process to start successfully.
         */
        private boolean waitForProcessStart(Process process) {
            try {
                // If process exits immediately, it failed
                boolean exited = process.waitFor(PROCESS_START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (exited) {
                    int exitCode = process.exitValue();
                    logger.warning("Process exited immediately with code: " + exitCode);
                    return false;
                }
                // Process still running after timeout means it started successfully
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("Interrupted while waiting for process start");
                return false;
            }
        }

        /**
         * Exit the current application instance.
         */
        private void exitApplication() {
            javafx.application.Platform.runLater(() -> {
                System.exit(0);
            });
        }

        /**
         * Perform graceful shutdown without restart.
         */
        private void gracefulShutdown() {
            javafx.application.Platform.runLater(() -> {
                try {
                    Set<Window> windows = new HashSet<>(Window.getWindows());
                    for (Window window : windows) {
                        if (window instanceof Stage) {
                            ((Stage) window).close();
                        }
                    }
                    logger.info("Graceful shutdown complete");
                    System.exit(0);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during shutdown", e);
                    System.exit(1);
                }
            });
        }
    }
}