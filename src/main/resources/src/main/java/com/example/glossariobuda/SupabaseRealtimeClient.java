package com.example.glossariobuda;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Supabase Realtime WebSocket Client
 * Implements Phoenix Channels protocol for real-time database subscriptions
 */
public class SupabaseRealtimeClient {

    private static final String SUPABASE_URL = "ctbufeavhuypjdbqmwzb.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImN0YnVmZWF2aHV5cGpkYnFtd3piIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjAyODQ2MzYsImV4cCI6MjA3NTg2MDYzNn0.lkaQWS8u2QisnRENtOXKDAMbPwhRV1jS4euBDc1TUDg";

    private WebSocketClient wsClient;
    private final Gson gson = new Gson();
    private final AtomicInteger refCounter = new AtomicInteger(1);
    private Timer heartbeatTimer;
    private Timer reconnectTimer;
    private RealtimeEventCallback callback;
    private boolean isConnected = false;
    private boolean shouldReconnect = true;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    // Channel and subscription tracking
    private String channelTopic = "realtime:public:terms";

    /**
     * Callback interface for realtime events
     */
    public interface RealtimeEventCallback {
        void onInsert(SupabaseClient.TermDTO newRecord);
        void onUpdate(SupabaseClient.TermDTO oldRecord, SupabaseClient.TermDTO newRecord);
        void onDelete(SupabaseClient.TermDTO oldRecord);
        void onError(String error);
    }

    public SupabaseRealtimeClient(RealtimeEventCallback callback) {
        this.callback = callback;
    }

    /**
     * Connect to Supabase Realtime
     */
    public void connect() {
        try {
            String wsUrl = String.format("wss://%s/realtime/v1/websocket?apikey=%s&vsn=1.0.0",
                    SUPABASE_URL, SUPABASE_KEY);

            System.out.println("[Realtime] Connecting to: " + wsUrl.replace(SUPABASE_KEY, "***"));

            // Create headers map for authentication
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + SUPABASE_KEY);
            headers.put("apikey", SUPABASE_KEY);

            wsClient = new WebSocketClient(new URI(wsUrl), headers) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    System.out.println("[Realtime] ✅ WebSocket connected");
                    isConnected = true;
                    reconnectAttempts = 0; // Reset reconnection counter on success
                    startHeartbeat();
                    subscribeToChannel();
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("[Realtime] ❌ WebSocket closed: " + reason + " (code: " + code + ")");
                    isConnected = false;
                    stopHeartbeat();

                    // Reconnect on connection loss (including timeouts)
                    if (shouldReconnect && (remote || code == 1006)) {
                        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                            reconnectAttempts++;
                            int delaySeconds = Math.min(5 * reconnectAttempts, 30); // Exponential backoff, max 30s
                            System.out.println("[Realtime] 🔄 Reconnection attempt " + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + " in " + delaySeconds + " seconds...");
                            scheduleReconnect(delaySeconds);
                        } else {
                            System.err.println("[Realtime] ❌ Max reconnection attempts reached. Please restart the application.");
                        }
                    }
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("[Realtime] ❌ WebSocket error: " + ex.getMessage());
                    if (callback != null) {
                        callback.onError(ex.getMessage());
                    }
                }
            };

            wsClient.connect();

        } catch (Exception e) {
            System.err.println("[Realtime] Failed to connect: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Disconnect from Supabase Realtime
     */
    public void disconnect() {
        shouldReconnect = false;
        stopHeartbeat();
        stopReconnectTimer();

        if (wsClient != null && isConnected) {
            System.out.println("[Realtime] Disconnecting...");
            wsClient.close();
        }
    }

    /**
     * Subscribe to the terms table channel
     */
    private void subscribeToChannel() {
        int channelRef = refCounter.getAndIncrement();

        // Subscribe to postgres_changes for INSERT, UPDATE, DELETE
        Map<String, Object> postgresChanges = new HashMap<>();
        postgresChanges.put("event", "*");  // Listen to INSERT, UPDATE, DELETE
        postgresChanges.put("schema", "public");
        postgresChanges.put("table", "glossario_terms");  // Changed from "terms" for exclusive access

        Map<String, Object> config = new HashMap<>();
        config.put("broadcast", Map.of("self", false));
        config.put("presence", Map.of("key", ""));
        config.put("postgres_changes", new Object[]{postgresChanges});

        Map<String, Object> payload = new HashMap<>();
        payload.put("config", config);

        Map<String, Object> message = new HashMap<>();
        message.put("topic", channelTopic);
        message.put("event", "phx_join");
        message.put("payload", payload);
        message.put("ref", String.valueOf(channelRef));

        String json = gson.toJson(message);
        System.out.println("[Realtime] Subscribing with config: " + json);
        sendMessage(json);
    }

    /**
     * Start heartbeat (required by Phoenix Channels)
     */
    private void startHeartbeat() {
        heartbeatTimer = new Timer("Realtime-Heartbeat", true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isConnected) {
                    sendHeartbeat();
                }
            }
        }, 30000, 30000);  // Every 30 seconds
    }

    /**
     * Stop heartbeat timer
     */
    private void stopHeartbeat() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
    }

    /**
     * Stop reconnect timer
     */
    private void stopReconnectTimer() {
        if (reconnectTimer != null) {
            reconnectTimer.cancel();
            reconnectTimer = null;
        }
    }

    /**
     * Send heartbeat message
     */
    private void sendHeartbeat() {
        try {
            // Double-check connection before sending
            if (!isConnected || wsClient == null || wsClient.isClosed()) {
                System.out.println("[Realtime] ⚠️ Skipping heartbeat - connection not ready");
                return;
            }

            int ref = refCounter.getAndIncrement();
            Map<String, Object> message = new HashMap<>();
            message.put("topic", "phoenix");
            message.put("event", "heartbeat");
            message.put("payload", Map.of());
            message.put("ref", String.valueOf(ref));

            sendMessage(gson.toJson(message));
        } catch (Exception e) {
            System.err.println("[Realtime] ⚠️ Heartbeat failed: " + e.getMessage());
            // Connection likely dropped, will be handled by onClose
        }
    }

    /**
     * Handle incoming WebSocket messages
     */
    private void handleMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String event = json.has("event") ? json.get("event").getAsString() : "";
            String topic = json.has("topic") ? json.get("topic").getAsString() : "";

            // DEBUG: Log all non-heartbeat messages
            if (!"heartbeat".equals(topic) && !"presence_state".equals(event) && !"presence_diff".equals(event)) {
                System.out.println("[Realtime] 📨 Message received: event=" + event + ", topic=" + topic);
            }

            // Handle different event types
            switch (event) {
                case "phx_reply":
                    handlePhxReply(json);
                    break;

                case "postgres_changes":
                    handlePostgresChanges(json);
                    break;

                case "presence_state":
                case "presence_diff":
                    // Presence tracking events - silently ignore
                    break;

                case "system":
                    // System messages (channel status, etc.)
                    System.out.println("[Realtime] System message: " + message);
                    break;

                default:
                    // Ignore heartbeat replies and other messages
                    if (!"heartbeat".equals(topic)) {
                        System.out.println("[Realtime] Unhandled event: " + event);
                    }
            }

        } catch (Exception e) {
            System.err.println("[Realtime] Error parsing message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle Phoenix reply messages
     */
    private void handlePhxReply(JsonObject json) {
        JsonObject payload = json.has("payload") ? json.getAsJsonObject("payload") : null;
        if (payload != null && payload.has("status")) {
            String status = payload.get("status").getAsString();

            if ("ok".equals(status)) {
                System.out.println("[Realtime] ✅ Channel joined successfully");
            } else {
                System.err.println("[Realtime] ❌ Channel join failed: " + payload);
                if (callback != null) {
                    callback.onError("Failed to join channel: " + status);
                }
            }
        }
    }

    /**
     * Handle postgres_changes events (INSERT, UPDATE, DELETE)
     */
    private void handlePostgresChanges(JsonObject json) {
        try {
            JsonObject payload = json.getAsJsonObject("payload");
            JsonObject data = payload.getAsJsonObject("data");

            String type = data.get("type").getAsString();

            System.out.println("[Realtime] 📥 Received " + type + " event");

            switch (type) {
                case "INSERT":
                    handleInsert(data);
                    break;

                case "UPDATE":
                    handleUpdate(data);
                    break;

                case "DELETE":
                    handleDelete(data);
                    break;

                default:
                    System.out.println("[Realtime] Unknown type: " + type);
            }

        } catch (Exception e) {
            System.err.println("[Realtime] Error handling postgres_changes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle INSERT event
     */
    private void handleInsert(JsonObject data) {
        if (data.has("record")) {
            JsonObject record = data.getAsJsonObject("record");
            SupabaseClient.TermDTO newTerm = gson.fromJson(record, SupabaseClient.TermDTO.class);

            System.out.println("[Realtime] INSERT: " + newTerm.source_term + " → " + newTerm.target_term);

            if (callback != null) {
                callback.onInsert(newTerm);
            }
        }
    }

    /**
     * Handle UPDATE event
     *
     * NOTE: With RLS enabled, old_record may only contain primary key (id).
     * The new record should contain full data after update.
     */
    private void handleUpdate(JsonObject data) {
        JsonObject oldRecord = data.has("old_record") ? data.getAsJsonObject("old_record") : null;
        JsonObject newRecord = data.has("record") ? data.getAsJsonObject("record") : null;

        if (newRecord != null) {
            SupabaseClient.TermDTO newTerm = gson.fromJson(newRecord, SupabaseClient.TermDTO.class);
            SupabaseClient.TermDTO oldTerm = oldRecord != null ?
                    gson.fromJson(oldRecord, SupabaseClient.TermDTO.class) : null;

            // DEBUG: Log what we received
            System.out.println("[Realtime] UPDATE: " + newTerm.source_term + " (owner: " + newTerm.owner + ")");
            if (oldTerm != null && oldTerm.id != null) {
                System.out.println("[Realtime] 🔍 DEBUG: old_record id=" + oldTerm.id +
                                 ", old hash=" + (oldTerm.content_hash != null ? oldTerm.content_hash : "NULL"));
            }
            System.out.println("[Realtime] 🔍 DEBUG: new_record id=" + newTerm.id +
                             ", new hash=" + (newTerm.content_hash != null ? newTerm.content_hash : "NULL"));

            if (callback != null) {
                callback.onUpdate(oldTerm, newTerm);
            }
        }
    }

    /**
     * Handle DELETE event
     *
     * NOTE: When RLS is enabled, old_record only contains the primary key (id).
     * This is a Supabase security feature to prevent data leakage.
     * See: https://github.com/orgs/supabase/discussions/12471
     */
    private void handleDelete(JsonObject data) {
        if (data.has("old_record")) {
            JsonObject oldRecord = data.getAsJsonObject("old_record");
            SupabaseClient.TermDTO deletedTerm = gson.fromJson(oldRecord, SupabaseClient.TermDTO.class);

            String termInfo = deletedTerm.id != null ? "id=" + deletedTerm.id : "unknown";
            if (deletedTerm.source_term != null) {
                termInfo = deletedTerm.source_term;
            }

            System.out.println("[Realtime] DELETE: " + termInfo);

            if (callback != null) {
                callback.onDelete(deletedTerm);
            }
        }
    }

    /**
     * Send message through WebSocket
     */
    private void sendMessage(String message) {
        try {
            if (wsClient != null && isConnected && !wsClient.isClosed()) {
                wsClient.send(message);
            } else {
                System.out.println("[Realtime] ⚠️ Cannot send message - WebSocket not connected");
            }
        } catch (org.java_websocket.exceptions.WebsocketNotConnectedException e) {
            System.err.println("[Realtime] ⚠️ WebSocket disconnected, message not sent");
            isConnected = false;
            // The onClose handler will trigger reconnection
        } catch (Exception e) {
            System.err.println("[Realtime] ⚠️ Error sending message: " + e.getMessage());
        }
    }

    /**
     * Schedule reconnection attempt with delay
     */
    private void scheduleReconnect(int delaySeconds) {
        // Cancel any existing reconnect timer
        stopReconnectTimer();

        reconnectTimer = new Timer("Realtime-Reconnect", true);
        reconnectTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (shouldReconnect && !isConnected) {
                    System.out.println("[Realtime] 🔌 Reconnecting...");
                    connect();
                }
            }
        }, delaySeconds * 1000L);
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return isConnected && wsClient != null && !wsClient.isClosed();
    }
}
