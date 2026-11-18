package com.ecosio.testutil;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record MockHttpResponse<T>(
        int statusCode,
        HttpHeaders headers,
        T body,
        HttpRequest request
) implements HttpResponse<T> {

    public MockHttpResponse(int statusCode, Map<String, List<String>> headers, T body) {
        this(statusCode, HttpHeaders.of(headers, (_, _) -> true), body, null);
    }

    public MockHttpResponse<T> withRequest(HttpRequest request) {
        return new MockHttpResponse<>(statusCode, headers, body, request);
    }

    @Override
    public Optional<HttpResponse<T>> previousResponse() {
        return Optional.empty();
    }

    @Override
    public Optional<SSLSession> sslSession() {
        return Optional.empty();
    }

    @Override
    public URI uri() {
        return request != null ? request.uri() : null;
    }

    @Override
    public HttpClient.Version version() {
        return HttpClient.Version.HTTP_2;
    }
}
