package com.ecosio.network;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import static java.lang.Thread.currentThread;
import static java.net.http.HttpClient.Redirect.NORMAL;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;

public class HttpPageFetcher implements PageFetcher {

    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public HttpPageFetcher(Duration connectionTimeout, Duration requestTimeout) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(requireNonNull(connectionTimeout, "connectionTimeout must not be null"))
                        .followRedirects(NORMAL)
                        .build(),
                requestTimeout
        );
    }

    HttpPageFetcher(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = requireNonNull(httpClient, "httpClient must not be null");
        this.requestTimeout = requireNonNull(requestTimeout, "requestTimeout must not be null");
    }

    @Override
    public Optional<String> fetchHtml(URI url) {
        if (url == null) {
            return empty();
        }

        try {
            final var request = HttpRequest.newBuilder()
                    .uri(url)
                    .timeout(requestTimeout)
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .headers("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();
            final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return empty();
            }
            if (!isHtmlContent(response)) {
                return empty();
            }

            return Optional.ofNullable(response.body());
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("An error has occurred when making a request: " + e.getMessage());
            return empty();
        } catch (InterruptedException e) {
            System.err.println("An error has occurred when making a request: " + e.getMessage());
            currentThread().interrupt();
            return empty();
        }
    }

    private boolean isHtmlContent(HttpResponse<String> response) {
        return response.headers()
                .firstValue("Content-Type")
                .map(type -> {
                    final var lowerCaseType = type.toLowerCase(Locale.ROOT);
                    return lowerCaseType.contains("text/html") || lowerCaseType.contains("application/xhtml+xml");
                })
                .orElse(false);
    }

}
