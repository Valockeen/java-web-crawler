package com.ecosio.url;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlNormalizerTest {

    private final URI seedUri = URI.create("https://ecosio.com");
    private final UrlNormalizer normalizer = new UrlNormalizer(seedUri);

    @Test
    void constructorThrowsWhenSeedUrlIsNull() {
        var exception = assertThrows(NullPointerException.class, () -> new UrlNormalizer(null));
        assertEquals("seedUrl must not be null", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/relative/path", "mailto:info@ecosio.com", "https:///no-host", "https://invalid_host/path"})
    void constructorThrowsWhenSeedUrlHasNoValidHost(String invalidSeed) {
        var uri = URI.create(invalidSeed);

        assertThrows(IllegalArgumentException.class, () -> new UrlNormalizer(uri));
    }

    @Test
    void resolvesRelativeUrlAndStripsFragmentAndTrailingSlash() {
        var currentPage = URI.create("https://ecosio.com/en/blog");

        var result = normalizer.normalizeAndFilter(currentPage, "/en/careers/#open-positions");

        assertEquals(Optional.of(URI.create("https://ecosio.com/en/careers")), result);
    }

    @Test
    void resolvesRelativePathWithoutLeadingSlash() {
        var currentPage = URI.create("https://ecosio.com/en/blog/");

        var result = normalizer.normalizeAndFilter(currentPage, "post-1");

        assertEquals(Optional.of(URI.create("https://ecosio.com/en/blog/post-1")), result);
    }

    @Test
    void resolvesDotSegmentsInRelativePath() {
        var currentPage = URI.create("https://ecosio.com/en/blog/2026/");

        var result = normalizer.normalizeAndFilter(currentPage, ".././about/");

        assertEquals(Optional.of(URI.create("https://ecosio.com/en/blog/about")), result);
    }

    @Test
    void resolvesProtocolRelativeUrl() {
        var currentPage = URI.create("https://ecosio.com/en/blog");

        var result = normalizer.normalizeAndFilter(currentPage, "//ecosio.com/en/contact/");

        assertEquals(Optional.of(URI.create("https://ecosio.com/en/contact")), result);
    }

    @Test
    void stripsLeadingAndTrailingWhitespaceFromHref() {
        var currentPage = URI.create("https://ecosio.com/en/blog");

        var result = normalizer.normalizeAndFilter(currentPage, "   /en/contact   ");

        assertEquals(Optional.of(URI.create("https://ecosio.com/en/contact")), result);
    }

    @Test
    void normalizesEmptyPathToRootSlash() {
        var currentPage = URI.create("https://ecosio.com/en/blog");

        var result = normalizer.normalizeAndFilter(currentPage, "https://ecosio.com");

        assertEquals(Optional.of(URI.create("https://ecosio.com/")), result);
    }

    @Test
    void keepsRootPathSlash() {
        var currentPage = URI.create("https://ecosio.com/en/blog");

        var result = normalizer.normalizeAndFilter(currentPage, "/");

        assertEquals(Optional.of(URI.create("https://ecosio.com/")), result);
    }

    @Test
    void preservesQueryParametersAndStripsFragment() {
        var currentPage = URI.create("https://ecosio.com/en");

        var result = normalizer.normalizeAndFilter(currentPage, "/search?q=edi&page=2#top");

        assertEquals(Optional.of(URI.create("https://ecosio.com/search?q=edi&page=2")), result);
    }

    @Test
    void preservesExplicitPortAndStripsUserInfo() {
        var currentPage = URI.create("https://ecosio.com/");

        var result = normalizer.normalizeAndFilter(currentPage, "https://user:secret@ecosio.com:8443/api/");

        assertEquals(Optional.of(URI.create("https://ecosio.com:8443/api")), result);
    }

    @Test
    void lowercasesSchemeAndHostWhilePreservingPathCase() {
        var currentPage = URI.create("https://ecosio.com/");

        var result = normalizer.normalizeAndFilter(currentPage, "HTTP://ECOSIO.COM/En/Blog/");

        assertEquals(Optional.of(URI.create("http://ecosio.com/En/Blog")), result);
    }

    @Test
    void allowsWwwVariantWhenSeedHasNoWww() {
        var currentPage = URI.create("https://ecosio.com/");

        var result = normalizer.normalizeAndFilter(currentPage, "https://www.ecosio.com/en/platform/");

        assertEquals(Optional.of(URI.create("https://www.ecosio.com/en/platform")), result);
    }

    @Test
    void allowsBareDomainWhenSeedHasWww() {
        var wwwNormalizer = new UrlNormalizer(URI.create("https://www.ecosio.com"));
        var currentPage = URI.create("https://www.ecosio.com/");

        var result = wwwNormalizer.normalizeAndFilter(currentPage, "https://ecosio.com/en/platform/");

        assertEquals(Optional.of(URI.create("https://ecosio.com/en/platform")), result);
    }

    @Test
    void returnsEmptyWhenCurrentPageUriIsNull() {
        var result = normalizer.normalizeAndFilter(null, "https://ecosio.com/en");

        assertTrue(result.isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n", "#section", "  #open-positions  "})
    void returnsEmptyForNullBlankOrFragmentOnlyHref(String rawHref) {
        var currentPage = URI.create("https://ecosio.com/en/blog");

        var result = normalizer.normalizeAndFilter(currentPage, rawHref);

        assertTrue(result.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "mailto:info@ecosio.com",
            "tel:+431234567",
            "javascript:void(0)",
            "ftp://ecosio.com/files/doc.pdf",
            "file:///etc/passwd"
    })
    void returnsEmptyForNonHttpSchemes(String rawHref) {
        var currentPage = URI.create("https://ecosio.com/en/blog");

        var result = normalizer.normalizeAndFilter(currentPage, rawHref);

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyWhenResolvedUriHasNoScheme() {
        var relativeCurrentPage = URI.create("/en/blog");

        var result = normalizer.normalizeAndFilter(relativeCurrentPage, "/en/careers");

        assertTrue(result.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/en",
            "https://blog.ecosio.com/post",
            "https://app.ecosio.com/",
            "https://myecosio.com/",
            "https://ecosio.com.evil.com/login",
            "https:///missing-host"
    })
    void returnsEmptyForExternalSubdomainOrMissingHost(String rawHref) {
        var currentPage = URI.create("https://ecosio.com/en/blog");

        var result = normalizer.normalizeAndFilter(currentPage, rawHref);

        assertTrue(result.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://ecosio.com/path with spaces",
            "://missing-scheme",
            "http://[invalid-ipv6"
    })
    void returnsEmptyForMalformedHref(String malformedHref) {
        var currentPage = URI.create("https://ecosio.com/en/blog");

        var result = normalizer.normalizeAndFilter(currentPage, malformedHref);

        assertTrue(result.isEmpty());
    }
}