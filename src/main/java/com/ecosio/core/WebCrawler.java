package com.ecosio.core;

import com.ecosio.model.CrawledLink;
import com.ecosio.network.PageFetcher;
import com.ecosio.parser.LinkExtractor;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;

public final class WebCrawler {

    private final PageFetcher pageFetcher;
    private final LinkExtractor linkExtractor;
    private final int maxPages;
    private final Semaphore semaphore;

    public WebCrawler(PageFetcher pageFetcher, LinkExtractor linkExtractor, int maxPages, int maxConcurrentRequests) {
        this.pageFetcher = requireNonNull(pageFetcher, "pageFetcher must not be null");
        this.linkExtractor = requireNonNull(linkExtractor, "linkExtractor must not be null");
        if (maxPages <= 0) {
            throw new IllegalArgumentException("maxPages must be positive");
        }
        if (maxConcurrentRequests <= 0) {
            throw new IllegalArgumentException("maxConcurrentRequests must be positive");
        }
        this.maxPages = maxPages;
        this.semaphore = new Semaphore(maxConcurrentRequests);
    }

    public List<CrawledLink> crawl(URI seedUrl) {
        requireNonNull(seedUrl, "seedUrl must not be null");

        final var visitedUrls = new HashSet<URI>();
        final var discoveredLinks = new HashSet<CrawledLink>();
        var currentBatch = new HashSet<URI>();

        visitedUrls.add(seedUrl);
        currentBatch.add(seedUrl);

        try (final var executor = newVirtualThreadPerTaskExecutor()) {
            while (!currentBatch.isEmpty()) {
                final var futures = currentBatch.stream()
                        .map(url -> supplyAsync(() -> fetchLinksFromPage(url), executor))
                        .toList();
                final var nextBatch = new HashSet<URI>();
                for (final var future : futures) {
                    final var linksOnPage = future.join();
                    discoveredLinks.addAll(linksOnPage);

                    for (final var link : linksOnPage) {
                        if (visitedUrls.size() < maxPages && visitedUrls.add(link.url())) {
                            nextBatch.add(link.url());
                        }
                    }
                }
                currentBatch = nextBatch;
            }
        }
        return discoveredLinks.stream().sorted().toList();
    }

    private Set<CrawledLink> fetchLinksFromPage(URI url) {
        try {
            semaphore.acquire();
            try {
                return pageFetcher.fetchHtml(url)
                        .map(html -> linkExtractor.extractLinks(url, html))
                        .orElse(Set.of());
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException e) {
            currentThread().interrupt();
            return Set.of();
        }
    }
}
