package dev.grimstats.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Small helpers for writing JSON/text responses and reading requests over the JDK HttpServer. */
public final class Http {

    public static final Gson GSON = new GsonBuilder().serializeNulls().create();

    /** Default body cap for the small JSON payloads the admin/API endpoints accept (1 MiB). */
    public static final int DEFAULT_MAX_BODY_BYTES = 1 << 20;

    private Http() {
    }

    /** Signals a request body that exceeds the caller's cap; handlers turn this into a 413. */
    public static final class BodyTooLargeException extends IOException {
        public BodyTooLargeException() {
            super("request body too large");
        }
    }

    public static void json(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void error(HttpExchange exchange, int status, String message) throws IOException {
        json(exchange, status, new ErrorBody(message));
    }

    public static void noContent(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    public static String readBody(HttpExchange exchange) throws IOException {
        return readBody(exchange, DEFAULT_MAX_BODY_BYTES);
    }

    /**
     * Reads the request body as UTF-8, refusing anything larger than {@code maxBytes}. A declared
     * {@code Content-Length} over the cap is rejected before reading, and the stream itself is read
     * through a hard cap so a lying or chunked request cannot buffer unbounded data into memory.
     *
     * @throws BodyTooLargeException if the body exceeds {@code maxBytes}
     */
    public static String readBody(HttpExchange exchange, int maxBytes) throws IOException {
        String declared = exchange.getRequestHeaders().getFirst("Content-Length");
        if (declared != null) {
            try {
                if (Long.parseLong(declared.trim()) > maxBytes) {
                    throw new BodyTooLargeException();
                }
            } catch (NumberFormatException ignored) {
                // Malformed length header; the streaming cap below still bounds the read.
            }
        }
        InputStream in = exchange.getRequestBody();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new BodyTooLargeException();
            }
            out.write(buf, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /**
     * Applies CORS headers.
     *
     * <p>When {@code allowAll} is true (the default), any origin is permitted via a wildcard. This is
     * safe here because the API authenticates with a bearer token in the {@code Authorization} header
     * rather than cookies, so there are no ambient credentials for a malicious site to ride on, and
     * data visibility is already governed by the dashboard's own auth/visibility config. This lets
     * third-party tools and separately-hosted dashboards talk to the API from anywhere.
     *
     * <p>When {@code allowAll} is false, only origins in {@code allowedOrigins} are reflected, and
     * credentialed requests are permitted for those origins.
     */
    public static void applyCors(HttpExchange exchange, boolean allowAll, List<String> allowedOrigins) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null) {
            return; // not a cross-origin browser request
        }
        var headers = exchange.getResponseHeaders();
        if (allowAll) {
            // Wildcard origin cannot be combined with Allow-Credentials; tokens are header-based so
            // credentials mode is not needed.
            headers.set("Access-Control-Allow-Origin", "*");
        } else if (allowedOrigins.contains(origin)) {
            headers.set("Access-Control-Allow-Origin", origin);
            headers.set("Vary", "Origin");
            headers.set("Access-Control-Allow-Credentials", "true");
        } else {
            return; // origin not allowed
        }
        headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
        headers.set("Access-Control-Allow-Methods", "GET, PUT, POST, OPTIONS");
        headers.set("Access-Control-Max-Age", "86400");
    }

    public record ErrorBody(String error) {
    }
}
