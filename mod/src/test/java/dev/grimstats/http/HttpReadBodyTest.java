package dev.grimstats.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpReadBodyTest {

    @Test
    void readsSmallBodyWithinCap() throws Exception {
        String s = "{\"ok\":true}";
        assertEquals(s, Http.readBody(exchange(s, s.length()), 1024));
    }

    @Test
    void rejectsWhenDeclaredContentLengthExceedsCap() {
        // A huge declared length is refused before any of the (potentially huge) body is read.
        StubExchange ex = exchange("x".repeat(50), 10_000_000L);
        assertThrows(Http.BodyTooLargeException.class, () -> Http.readBody(ex, 1024));
    }

    @Test
    void rejectsWhenActualBodyExceedsCapDespiteLyingHeader() {
        // Content-Length lies (small), but the real stream is larger than the cap: still refused,
        // and never fully buffered.
        StubExchange ex = exchange("y".repeat(5000), 10L);
        assertThrows(Http.BodyTooLargeException.class, () -> Http.readBody(ex, 1024));
    }

    @Test
    void readsUpToExactlyCap() throws Exception {
        String s = "z".repeat(1024);
        assertEquals(s, Http.readBody(exchange(s, s.length()), 1024));
    }

    private static StubExchange exchange(String body, long declaredLength) {
        Headers h = new Headers();
        h.set("Content-Length", Long.toString(declaredLength));
        return new StubExchange(h, new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    /** Minimal HttpExchange exposing only request headers and body; everything else is unused here. */
    private static final class StubExchange extends HttpExchange {
        private final Headers requestHeaders;
        private final InputStream body;

        StubExchange(Headers requestHeaders, InputStream body) {
            this.requestHeaders = requestHeaders;
            this.body = body;
        }

        @Override public Headers getRequestHeaders() { return requestHeaders; }
        @Override public InputStream getRequestBody() { return body; }

        @Override public Headers getResponseHeaders() { throw new UnsupportedOperationException(); }
        @Override public URI getRequestURI() { throw new UnsupportedOperationException(); }
        @Override public String getRequestMethod() { return "POST"; }
        @Override public HttpContext getHttpContext() { throw new UnsupportedOperationException(); }
        @Override public void close() { }
        @Override public OutputStream getResponseBody() { throw new UnsupportedOperationException(); }
        @Override public void sendResponseHeaders(int rCode, long responseLength) { }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public int getResponseCode() { return -1; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) { }
        @Override public void setStreams(InputStream i, OutputStream o) { }
        @Override public HttpPrincipal getPrincipal() { return null; }
    }
}
