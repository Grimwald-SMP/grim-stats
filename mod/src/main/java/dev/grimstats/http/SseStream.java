package dev.grimstats.http;

import com.sun.net.httpserver.HttpExchange;
import dev.grimstats.data.SnapshotProvider;
import dev.grimstats.data.model.StatsSnapshot;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * Server-Sent Events stream that pushes a (visibility-filtered) snapshot whenever a new one is
 * published, plus a periodic comment heartbeat to keep proxies from closing the connection.
 *
 * <p>The handler thread blocks here for the connection lifetime; the web server uses a cached
 * thread pool so long-lived streams never starve the regular request handlers.
 */
final class SseStream {

    private static final long POLL_MS = 1000L;
    private static final long HEARTBEAT_MS = 20_000L;

    private final SnapshotProvider provider;
    private final Supplier<StatsSnapshot> filteredView;

    SseStream(SnapshotProvider provider, Supplier<StatsSnapshot> filteredView) {
        this.provider = provider;
        this.filteredView = filteredView;
    }

    void run(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-transform");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        OutputStream os = exchange.getResponseBody();
        long lastSent = -1L;
        long lastHeartbeat = System.currentTimeMillis();
        try {
            // Send an initial snapshot immediately.
            lastSent = send(os, filteredView.get());
            while (!Thread.currentThread().isInterrupted()) {
                long current = provider.current().generatedAtEpochMs();
                long now = System.currentTimeMillis();
                if (current != lastSent) {
                    lastSent = send(os, filteredView.get());
                    lastHeartbeat = now;
                } else if (now - lastHeartbeat >= HEARTBEAT_MS) {
                    os.write(":hb\n\n".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    lastHeartbeat = now;
                }
                sleep();
            }
        } catch (IOException disconnected) {
            // Client went away; end the stream quietly.
        } finally {
            try {
                os.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }

    private long send(OutputStream os, StatsSnapshot snapshot) throws IOException {
        String data = "event: snapshot\ndata: " + Http.GSON.toJson(snapshot) + "\n\n";
        os.write(data.getBytes(StandardCharsets.UTF_8));
        os.flush();
        return snapshot.generatedAtEpochMs();
    }

    private void sleep() {
        try {
            Thread.sleep(POLL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
