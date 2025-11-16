package com.example.glossariobuda;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Monitors network connectivity and sync status
 * Provides real-time updates to the UI about connection state
 */
public class NetworkStatusMonitor {

    public enum ConnectionState {
        ONLINE("🟢 Online"),
        OFFLINE("🔴 Offline"),
        CHECKING("🟡 Verificando..."),
        SYNCING("🔄 Sincronizando..."),
        SYNC_SUCCESS("✅ Sincronizado"),
        SYNC_ERROR("❌ Erro de Sincronização"),
        SYNC_QUEUED("📋 Operações em fila");

        private final String displayText;

        ConnectionState(String displayText) {
            this.displayText = displayText;
        }

        public String getDisplayText() {
            return displayText;
        }
    }
    private Runnable onlineCallback; // Callback quando voltar online
    private final ScheduledExecutorService scheduler;
    private ConnectionState currentState;
    private String operationMessage;
    private DualStatusCallback statusUpdateCallback;
    private boolean isMonitoring;
    private volatile boolean isCurrentlyOnline = true;
    private volatile boolean isCheckingConnection = false;
    private volatile long lastConnectionCheckTime = 0;
    private volatile ScheduledFuture<?> monitoringTask = null;
    private volatile ScheduledFuture<?> periodicCheckTask = null;

    /**
     * Callback interface for dual status updates
     */
    public interface DualStatusCallback {
        void onStatusUpdate(String connectionStatus, String operationStatus);
    }

    /**
     * Set callback to be executed when connection is restored
     */
    public void setOnlineCallback(Runnable callback) {
        this.onlineCallback = callback;
    }

    /**
     * Callback interface for connection restoration (offline -> online)
     */
    public interface ConnectionRestoredCallback {
        void onConnectionRestored();
    }

    private ConnectionRestoredCallback connectionRestoredCallback;

    // Supabase URL for connection testing
    private static final String SUPABASE_URL = "https://ctbufeavhuypjdbqmwzb.supabase.co";
    private static final int CONNECTION_TIMEOUT_MS = 5000;

    public NetworkStatusMonitor() {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.currentState = ConnectionState.CHECKING;
        this.operationMessage = "";
        this.isMonitoring = false;
    }

    /**
     * Set callback for when connection is restored (offline -> online transition)
     */
    public void setConnectionRestoredCallback(ConnectionRestoredCallback callback) {
        this.connectionRestoredCallback = callback;
    }

    /**
     * Start monitoring network status
     * Only checks connection when offline (to detect reconnection)
     * When online, trusts the connection until an operation fails
     */
    public void startMonitoring(DualStatusCallback callback) {
        this.statusUpdateCallback = callback;
        this.isMonitoring = true;

        // Initial check to determine if we're online or offline
        checkConnectionStatus();

        // Don't start periodic checks - they will be started automatically
        // when we detect offline state (see updateState method)
        System.out.println("[NetworkStatusMonitor] Monitoring initialized. Will check periodically only when offline.");
    }

    /**
     * Stop monitoring
     */
    public void stopMonitoring() {
        isMonitoring = false;

        // Cancel periodic check task
        if (periodicCheckTask != null) {
            periodicCheckTask.cancel(false);
            periodicCheckTask = null;
        }

        stopConnectionMonitoring();
        scheduler.shutdownNow();
    }

    /**
     * Check connection status with caching
     */
    private void checkConnectionStatus() {
        if (!isMonitoring) {
            return;
        }

        Thread checkThread = new Thread(() -> {
            boolean isConnected = testConnection();

            Platform.runLater(() -> {
                ConnectionState newState = isConnected ? ConnectionState.ONLINE : ConnectionState.OFFLINE;
                // Don't pass message - connection state is shown separately
                updateState(newState, "");
            });
        });

        checkThread.setDaemon(true);
        checkThread.start();
    }

    /**
     * Test connection to Supabase
     */
    private boolean testConnection() {
        try {
            URL url = new URL(SUPABASE_URL + "/rest/v1/");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            connection.setReadTimeout(CONNECTION_TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            return (responseCode >= 200 && responseCode < 300) || responseCode == 401;

        } catch (IOException e) {
            return false;
        }
    }


    /**
     * Notify callback with current status
     */
    private void notifyStatusUpdate() {
        if (statusUpdateCallback != null) {
            String connectionStatus = currentState.getDisplayText();
            statusUpdateCallback.onStatusUpdate(connectionStatus, operationMessage);
        }
    }

    /**
     * Manually set state (for sync operations)
     */
    public void setState(ConnectionState state, String message) {
        Platform.runLater(() -> updateState(state, message));
    }

    /**
     * Set syncing message (shows in operation status)
     * If message is null, clears the operation message
     */
    public void setSyncing(String message) {
        Platform.runLater(() -> {
            if (message == null || message.isEmpty()) {
                clearOperationMessage();
            } else {
                setOperationMessage("🔄 " + message);
            }
        });
    }

    /**
     * Temporarily disable monitoring (prevents auto-checks)
     */
    public void pauseMonitoring(long durationMs) {
        System.out.println("[NetworkStatusMonitor] Pausing monitoring for " + durationMs + "ms");
        isMonitoring = false;

        scheduler.schedule(() -> {
            System.out.println("[NetworkStatusMonitor] Resuming monitoring");
            isMonitoring = true;
        }, durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Set sync success message (temporary, clears after delay)
     */
    public void setSyncSuccess(String message) {
        Platform.runLater(() -> {
            setOperationMessage("✅ " + message);

            // Clear message after 5 seconds
            scheduler.schedule(() -> {
                Platform.runLater(() -> clearOperationMessage());
            }, 5, TimeUnit.SECONDS);
        });
    }

    /**
     * Set sync error message (temporary, clears after delay)
     */
    public void setSyncError(String message) {
        Platform.runLater(() -> {
            setOperationMessage("❌ " + message);

            // Clear message after 10 seconds
            scheduler.schedule(() -> {
                Platform.runLater(() -> clearOperationMessage());
            }, 10, TimeUnit.SECONDS);
        });
    }

    /**
     * Set queued operations state
     * Also transitions to OFFLINE state to trigger connection monitoring
     */
    public void setQueued(int count) {
        Platform.runLater(() -> {
            String message = count == 1
                    ? "1 operação aguardando conexão"
                    : count + " operações aguardando conexão";
            // Set to OFFLINE state so connection monitoring starts
            updateState(ConnectionState.OFFLINE, message);
        });
    }

    /**
     * Set operation message without changing connection state
     */
    public void setOperationMessage(String message) {
        this.operationMessage = message;
        notifyStatusUpdate();
    }

    /**
     * Clear operation message
     */
    public void clearOperationMessage() {
        this.operationMessage = "";
        notifyStatusUpdate();
    }

    /**
     * Get current connection state
     */
    public ConnectionState getCurrentState() {
        return currentState;
    }

    /**
     * Check if currently online
     */
    public boolean isOnline() {
        return currentState == ConnectionState.ONLINE
                || currentState == ConnectionState.SYNCING
                || currentState == ConnectionState.SYNC_SUCCESS;
    }

    /**
     * Force immediate connection check
     */
    public void forceCheck() {
        if (isMonitoring) {
            lastConnectionCheckTime = 0;
            checkConnectionStatus();
        }
    }

    /**
     * Start monitoring connection (checks every 10 seconds when offline)
     */
    private synchronized void startConnectionMonitoring() {
        if (isCheckingConnection) {
            return;
        }

        isCheckingConnection = true;
        System.out.println("[NetworkStatusMonitor] Starting connection monitoring (every 10s)");

        monitoringTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                lastConnectionCheckTime = 0;
                boolean online = testConnection();

                if (online) {
                    System.out.println("[NetworkStatusMonitor] Connection restored!");
                    Platform.runLater(() -> {
                        stopConnectionMonitoring();
                        updateState(ConnectionState.ONLINE, "");
                    });
                }
            } catch (Exception e) {
                System.err.println("[NetworkStatusMonitor] Error checking connection: " + e.getMessage());
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    /**
     * Update state and notify callback
     */
    private void updateState(ConnectionState state, String message) {
        ConnectionState previousState = currentState;
        String previousMessage = this.operationMessage;
        boolean stateChanged = currentState != state;
        this.currentState = state;

        // Only update operation message if provided
        boolean messageChanged = false;
        if (message != null && !message.isEmpty()) {
            messageChanged = !message.equals(previousMessage);
            this.operationMessage = message;
        }

        // Update online status based on actual connection state
        boolean wasOffline = previousState == ConnectionState.OFFLINE;
        if (state == ConnectionState.ONLINE) {
            isCurrentlyOnline = true;

            // Stop periodic checking when online
            stopConnectionMonitoring();

            // Notify if we transitioned from OFFLINE to ONLINE
            if (wasOffline && connectionRestoredCallback != null) {
                System.out.println("[NetworkStatusMonitor] 🔄 Connection restored! Triggering callback...");
                connectionRestoredCallback.onConnectionRestored();
            }

            // ALSO call onlineCallback if set (for backward compatibility)
            if (wasOffline && onlineCallback != null) {
                System.out.println("[NetworkStatusMonitor] 🔄 Executing online callback...");
                new Thread(onlineCallback).start();
            }
        } else if (state == ConnectionState.OFFLINE) {
            isCurrentlyOnline = false;

            // Start periodic checking to detect when we're back online
            startConnectionMonitoring();
        }
        // Temporary states (SYNCING, SYNC_SUCCESS, SYNC_ERROR) don't change isCurrentlyOnline

        // Only notify if state or message actually changed
        if (stateChanged || messageChanged) {
            notifyStatusUpdate();
        }
    }

    /**
     * Stop connection monitoring
     */
    private synchronized void stopConnectionMonitoring() {
        if (!isCheckingConnection) {
            return;
        }

        if (monitoringTask != null) {
            monitoringTask.cancel(false);
            monitoringTask = null;
        }

        isCheckingConnection = false;
        System.out.println("[NetworkStatusMonitor] Stopped connection monitoring");
    }
}