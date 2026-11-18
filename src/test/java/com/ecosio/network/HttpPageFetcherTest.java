package com.ecosio.network;

import com.ecosio.testutil.MockHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpPageFetcherTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final URI TARGET_URI = URI.create("https://ecosio.com/en/blog");
    private static final String EXPECTED_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    @Test
    void constructorThrowsWhenConnectionTimeoutIsNull() {
        var exception = assertThrows(NullPointerException.class, () -> new HttpPageFetcher((Duration) null, TIMEOUT));
        assertEquals("connectionTimeout must not be null", exception.getMessage());
    }

    @Test
    void constructorThrowsWhenHttpClientIsNull() {
        var exception = assertThrows(NullPointerException.class, () -> new HttpPageFetcher((HttpClient) null, TIMEOUT));
        assertEquals("httpClient must not be null", exception.getMessage());
    }

    @Test
    void constructorThrowsWhenRequestTimeoutIsNull() {
        var mockClient = new MockHttpClient();
        var exception = assertThrows(NullPointerException.class, () -> new HttpPageFetcher(mockClient, null));
        assertEquals("requestTimeout must not be null", exception.getMessage());
    }

    @Test
    void returnsEmptyWithoutCallingClientWhenUrlIsNull() {
        var mockClient = new MockHttpClient();
        var fetcher = new HttpPageFetcher(mockClient, TIMEOUT);

        var result = fetcher.fetchHtml(null);

        assertTrue(result.isEmpty());
        assertNull(mockClient.lastRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "text/html",
            "text/html; charset=utf-8",
            "TEXT/HTML; charset=ISO-8859-1",
            "application/xhtml+xml; charset=utf-8"
    })
    void fetchesBodyForSuccessfulHtmlContentTypesAndSendsExpectedHeaders(String contentType) {
        var expectedBody = "<html><body><h1>Hello</h1></body></html>";
        var mockClient = new MockHttpClient()
                .returning(200, Map.of("Content-Type", List.of(contentType)), expectedBody);
        var fetcher = new HttpPageFetcher(mockClient, TIMEOUT);

        var result = fetcher.fetchHtml(TARGET_URI);

        assertEquals(Optional.of(expectedBody), result);
        var lastRequest = mockClient.lastRequest();
        assertNotNull(lastRequest);
        assertEquals(TARGET_URI, lastRequest.uri());
        assertEquals("GET", lastRequest.method());
        assertEquals(Optional.of(TIMEOUT), lastRequest.timeout());
        assertEquals(Optional.of(EXPECTED_USER_AGENT), lastRequest.headers().firstValue("User-Agent"));
        assertEquals(Optional.of("text/html,application/xhtml+xml"), lastRequest.headers().firstValue("Accept"));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 299})
    void returnsBodyForAll2xxStatusCodes(int statusCode) {
        var expectedBody = "<html><body>OK</body></html>";
        var mockClient = new MockHttpClient()
                .returning(statusCode, Map.of("Content-Type", List.of("text/html")), expectedBody);
        var fetcher = new HttpPageFetcher(mockClient, TIMEOUT);

        var result = fetcher.fetchHtml(TARGET_URI);

        assertEquals(Optional.of(expectedBody), result);
    }

    @Test
    void returnsEmptyWhenResponseBodyIsNull() {
        var mockClient = new MockHttpClient()
                .returning(200, Map.of("Content-Type", List.of("text/html")), null);
        var fetcher = new HttpPageFetcher(mockClient, TIMEOUT);

        var result = fetcher.fetchHtml(TARGET_URI);

        assertTrue(result.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {199, 300, 304, 400, 404, 500, 503})
    void returnsEmptyForNon2xxStatusCodes(int statusCode) {
        var mockClient = new MockHttpClient()
                .returning(statusCode, Map.of("Content-Type", List.of("text/html")), "<html></html>");
        var fetcher = new HttpPageFetcher(mockClient, TIMEOUT);

        var result = fetcher.fetchHtml(TARGET_URI);

        assertTrue(result.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "application/json",
            "text/plain",
            "application/pdf",
            "image/png"
    })
    void returnsEmptyForNonHtmlContentTypes(String contentType) {
        var mockClient = new MockHttpClient()
                .returning(200, Map.of("Content-Type", List.of(contentType)), "{}");
        var fetcher = new HttpPageFetcher(mockClient, TIMEOUT);

        var result = fetcher.fetchHtml(TARGET_URI);

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyWhenContentTypeHeaderIsMissing() {
        var mockClient = new MockHttpClient()
                .returning(200, Map.of(), "<html></html>");
        var fetcher = new HttpPageFetcher(mockClient, TIMEOUT);

        var result = fetcher.fetchHtml(TARGET_URI);

        assertTrue(result.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/relative/path", "ftp://ecosio.com/file.html"})
    void returnsEmptyForNonHttpOrRelativeUriWithoutCallingClient(String invalidUri) {
        var mockClient = new MockHttpClient();
        var fetcher = new HttpPageFetcher(mockClient, TIMEOUT);

        var result = fetcher.fetchHtml(URI.create(invalidUri));

        assertTrue(result.isEmpty());
        assertNull(mockClient.lastRequest());
    }

    @Test
    void returnsEmptyWhenClientThrowsIOException() {
        var mockClient = new MockHttpClient().throwing(new IOException("Connection refused"));
        var fetcher = new HttpPageFetcher(mockClient, TIMEOUT);

        var result = fetcher.fetchHtml(TARGET_URI);

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyWhenClientThrowsIllegalArgumentException() {
        var mockClient = new MockHttpClient().throwing(new IllegalArgumentException("Invalid request"));
        var fetcher = new HttpPageFetcher(mockClient, TIMEOUT);

        var result = fetcher.fetchHtml(TARGET_URI);

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyAndRestoresInterruptFlagWhenClientThrowsInterruptedException() {
        var mockClient = new MockHttpClient().throwing(new InterruptedException("Interrupted"));
        var fetcher = new HttpPageFetcher(mockClient, TIMEOUT);

        try {
            var result = fetcher.fetchHtml(TARGET_URI);

            assertTrue(result.isEmpty());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}