package com.ecosio.url;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;

public final class UrlNormalizer {

    private final String allowedDomain;

    public UrlNormalizer(URI seedUrl) {
        requireNonNull(seedUrl, "seedUrl must not be null");
        final var host = seedUrl.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("seedUrl must have a valid host: " + seedUrl);
        }
        this.allowedDomain = stripWww(host.toLowerCase(Locale.ROOT));
    }

    public Optional<URI> normalizeAndFilter(URI currentPageUri, String rawHref) {
        if (currentPageUri == null || rawHref == null) {
            return empty();
        }
        final var trimmedHref = rawHref.strip();
        if (trimmedHref.isEmpty() || trimmedHref.startsWith("#")) {
            return empty();
        }
        try {
            final var resolvedUri = currentPageUri.resolve(trimmedHref).normalize();
            final var scheme = resolvedUri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return empty();
            }

            final var host = resolvedUri.getHost();
            if (host == null || !isSameDomain(host)) {
                return empty();
            }

            var path = resolvedUri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            } else if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            final var normalizedUrl = new URI(
                    scheme.toLowerCase(Locale.ROOT),
                    null,
                    host.toLowerCase(Locale.ROOT),
                    resolvedUri.getPort(),
                    path,
                    resolvedUri.getQuery(),
                    null
            );
            return Optional.of(normalizedUrl);
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return empty();
        }
    }

    private boolean isSameDomain(String host) {
        final var normalizedHost = stripWww(host);
        return normalizedHost.equalsIgnoreCase(allowedDomain);
    }

    private String stripWww(String host) {
        return host.startsWith("www.") ? host.substring(4) : host;
    }
}
