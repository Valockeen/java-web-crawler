package com.ecosio.testutil;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class MockHttpClient extends HttpClient {

    private final List<HttpRequest> requests = new ArrayList<>();
    private HttpResponse<?> response;
    private Exception exceptionToThrow;

    public <T> MockHttpClient returning(int statusCode, Map<String, List<String>> headers, T body) {
        this.response = new MockHttpResponse<>(statusCode, headers, body);
        return this;
    }

    public MockHttpClient returning(HttpResponse<?> response) {
        this.response = response;
        return this;
    }

    public MockHttpClient throwing(Exception exceptionToThrow) {
        this.exceptionToThrow = exceptionToThrow;
        return this;
    }

    public HttpRequest lastRequest() {
        return requests.isEmpty() ? null : requests.getLast();
    }

    public List<HttpRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
        this.requests.add(request);
        if (exceptionToThrow instanceof IOException ioException) {
            throw ioException;
        }
        if (exceptionToThrow instanceof InterruptedException interruptedException) {
            throw interruptedException;
        }
        if (exceptionToThrow instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (response instanceof MockHttpResponse<?> mockResponse && mockResponse.request() == null) {
            return (HttpResponse<T>) mockResponse.withRequest(request);
        }
        return (HttpResponse<T>) response;
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return Optional.empty();
    }

    @Override
    public Redirect followRedirects() {
        return Redirect.NORMAL;
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
        return null;
    }

    @Override
    public SSLParameters sslParameters() {
        return null;
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return Optional.empty();
    }

    @Override
    public Version version() {
        return Version.HTTP_2;
    }

    @Override
    public Optional<Executor> executor() {
        return Optional.empty();
    }
}
