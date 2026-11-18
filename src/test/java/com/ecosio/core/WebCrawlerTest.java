package com.ecosio.core;

import com.ecosio.model.CrawledLink;
import com.ecosio.network.PageFetcher;
import com.ecosio.parser.LinkExtractor;
import com.ecosio.url.UrlNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Optional.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebCrawlerTest {

    private static final URI SEED_URI = URI.create("https://ecosio.com/");

    private final LinkExtractor linkExtractor = new LinkExtractor(new UrlNormalizer(SEED_URI));

    @Test
    void constructorThrowsWhenPageFetcherIsNull() {
        var exception = assertThrows(NullPointerException.class,
                () -> new WebCrawler(null, linkExtractor, 10, 2));
        assertEquals("pageFetcher must not be null", exception.getMessage());
    }

    @Test
    void constructorThrowsWhenLinkExtractorIsNull() {
        PageFetcher fetcher = _ -> empty();
        var exception = assertThrows(NullPointerException.class,
                () -> new WebCrawler(fetcher, null, 10, 2));
        assertEquals("linkExtractor must not be null", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    void constructorThrowsWhenMaxPagesIsNotPositive(int maxPages) {
        PageFetcher fetcher = _ -> empty();
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new WebCrawler(fetcher, linkExtractor, maxPages, 2));
        assertEquals("maxPages must be positive", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -5})
    void constructorThrowsWhenMaxConcurrentRequestsIsNotPositive(int maxConcurrentRequests) {
        PageFetcher fetcher = _ -> empty();
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new WebCrawler(fetcher, linkExtractor, 10, maxConcurrentRequests));
        assertEquals("maxConcurrentRequests must be positive", exception.getMessage());
    }

    @Test
    void crawlThrowsWhenSeedUrlIsNull() {
        var crawler = new WebCrawler(_ -> empty(), linkExtractor, 10, 2);

        var exception = assertThrows(NullPointerException.class, () -> crawler.crawl(null));
        assertEquals("seedUrl must not be null", exception.getMessage());
    }

    @Test
    void returnsEmptyListWhenSeedPageCannotBeFetched() {
        var crawler = new WebCrawler(_ -> empty(), linkExtractor, 10, 2);

        var result = crawler.crawl(SEED_URI);

        assertTrue(result.isEmpty());
    }

    @Test
    void crawlsMultiLevelGraphHandlesCyclesDeduplicatesAndSortsResults() {
        var pageA = URI.create("https://ecosio.com/a");
        var pageB = URI.create("https://ecosio.com/b");
        var pageC = URI.create("https://ecosio.com/c");

        var pages = Map.of(
                SEED_URI, """
                        <a href="/b">zeta</a>
                        <a href="/a">Alpha</a>
                        <a href="https://external.example.com/out">External</a>
                        """,
                pageA, """
                        <a href="/">Home</a>
                        <a href="/c">beta</a>
                        <a href="/b">zeta</a>
                        """,
                pageB, """
                        <a href="/c">beta</a>
                        <a href="/missing">Missing Page</a>
                        """,
                pageC, """
                        <a href="/a">Alpha</a>
                        """
        );

        var fetchCounts = new ConcurrentHashMap<URI, AtomicInteger>();
        PageFetcher fetcher = uri -> {
            fetchCounts.computeIfAbsent(uri, _ -> new AtomicInteger()).incrementAndGet();
            return Optional.ofNullable(pages.get(uri));
        };

        var crawler = new WebCrawler(fetcher, linkExtractor, 10, 2);

        var result = crawler.crawl(SEED_URI);

        assertEquals(List.of(
                new CrawledLink("Alpha", pageA),
                new CrawledLink("beta", pageC),
                new CrawledLink("Home", SEED_URI),
                new CrawledLink("Missing Page", URI.create("https://ecosio.com/missing")),
                new CrawledLink("zeta", pageB)
        ), result);

        assertEquals(1, fetchCounts.get(SEED_URI).get());
        assertEquals(1, fetchCounts.get(pageA).get());
        assertEquals(1, fetchCounts.get(pageB).get());
        assertEquals(1, fetchCounts.get(pageC).get());
        assertEquals(1, fetchCounts.get(URI.create("https://ecosio.com/missing")).get());
        assertFalse(fetchCounts.containsKey(URI.create("https://external.example.com/out")));
    }

    @Test
    void stopsSchedulingNewPagesOnceMaxPagesIsReached() {
        var page1 = URI.create("https://ecosio.com/page1");
        var page2 = URI.create("https://ecosio.com/page2");
        var page3 = URI.create("https://ecosio.com/page3");

        var pages = Map.of(
                SEED_URI, "<a href=\"/page1\">Page 1</a><a href=\"/page2\">Page 2</a>",
                page1, "<a href=\"/page3\">Page 3</a>",
                page2, "<a href=\"/page3\">Page 3</a>",
                page3, "<a href=\"/unreachable\">Unreachable</a>"
        );

        var fetchCount = new AtomicInteger();
        var fetchedUris = ConcurrentHashMap.<URI>newKeySet();
        PageFetcher fetcher = uri -> {
            fetchCount.incrementAndGet();
            fetchedUris.add(uri);
            return Optional.ofNullable(pages.get(uri));
        };

        var crawler = new WebCrawler(fetcher, linkExtractor, 2, 2);

        var result = crawler.crawl(SEED_URI);

        assertEquals(2, fetchCount.get());
        assertTrue(fetchedUris.contains(SEED_URI));
        assertFalse(fetchedUris.contains(page3));
        assertTrue(result.contains(new CrawledLink("Page 1", page1)));
        assertTrue(result.contains(new CrawledLink("Page 2", page2)));
        assertTrue(result.contains(new CrawledLink("Page 3", page3)));
        assertFalse(result.contains(new CrawledLink("Unreachable", URI.create("https://ecosio.com/unreachable"))));
    }

    @Test
    void respectsMaxPagesOfOneWithoutFetchingChildPages() {
        var child = URI.create("https://ecosio.com/child");
        var fetchedUris = ConcurrentHashMap.<URI>newKeySet();
        PageFetcher fetcher = uri -> {
            fetchedUris.add(uri);
            if (uri.equals(SEED_URI)) {
                return Optional.of("<a href=\"/child\">Child</a>");
            }
            return Optional.of("<a href=\"/grandchild\">Grandchild</a>");
        };

        var crawler = new WebCrawler(fetcher, linkExtractor, 1, 2);

        var result = crawler.crawl(SEED_URI);

        assertEquals(List.of(new CrawledLink("Child", child)), result);
        assertEquals(java.util.Set.of(SEED_URI), fetchedUris);
    }

    @Test
    void limitsConcurrentRequestsUsingMaxConcurrentRequests() {
        var childLinksHtml = new StringBuilder();
        var pages = new ConcurrentHashMap<URI, String>();
        for (int i = 1; i <= 6; i++) {
            var path = "/p" + i;
            childLinksHtml.append("<a href=\"").append(path).append("\">P").append(i).append("</a>");
            pages.put(URI.create("https://ecosio.com" + path), "");
        }
        pages.put(SEED_URI, childLinksHtml.toString());

        var activeRequests = new AtomicInteger();
        var maxObservedConcurrency = new AtomicInteger();

        PageFetcher fetcher = uri -> {
            int current = activeRequests.incrementAndGet();
            maxObservedConcurrency.accumulateAndGet(current, Math::max);
            try {
                if (!uri.equals(SEED_URI)) {
                    Thread.sleep(40);
                }
                return Optional.ofNullable(pages.get(uri));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return empty();
            } finally {
                activeRequests.decrementAndGet();
            }
        };

        var crawler = new WebCrawler(fetcher, linkExtractor, 10, 2);

        var result = crawler.crawl(SEED_URI);

        assertEquals(6, result.size());
        assertTrue(maxObservedConcurrency.get() <= 2,
                "Expected concurrency <= 2, but observed " + maxObservedConcurrency.get());
        assertEquals(2, maxObservedConcurrency.get(),
                "Expected concurrent execution up to limit 2");
    }

    @Test
    void returnsUnmodifiableList() {
        var crawler = new WebCrawler(_ -> Optional.of("<a href=\"/a\">A</a>"), linkExtractor, 5, 1);

        var result = crawler.crawl(SEED_URI);

        assertThrows(UnsupportedOperationException.class,
                () -> result.add(new CrawledLink("B", URI.create("https://ecosio.com/b"))));
    }
}
