package com.ecosio;

import com.ecosio.core.WebCrawler;
import com.ecosio.model.CrawledLink;
import com.ecosio.network.HttpPageFetcher;
import com.ecosio.parser.LinkExtractor;
import com.ecosio.url.UrlNormalizer;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Scanner;

import static java.lang.System.currentTimeMillis;

public final class Main {

    private static final String DEFAULT_TARGET_URL = "https://ecosio.com/en/";
    private static final int DEFAULT_MAX_PAGES = 100;
    private static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 10;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public static void main(String[] args) {
        final var scanner = new Scanner(System.in);

        System.out.println();
        System.out.println("Please type in the following information or press \"Enter\" to use defaults within the [brackets]");
        final var rawUrl = promptString(scanner, "Target website URL", DEFAULT_TARGET_URL);
        final var maxPages = promptInt(scanner, "Max pages to crawl", DEFAULT_MAX_PAGES);
        final var maxConcurrentRequests = promptInt(scanner, "Max concurrent requests", DEFAULT_MAX_CONCURRENT_REQUESTS);

        final var initialUrl = parseSeedUri(rawUrl);
        final var urlNormalizer = new UrlNormalizer(initialUrl);
        final var seedUrl = urlNormalizer.normalizeAndFilter(initialUrl, "").orElse(initialUrl);

        final var pageFetcher = new HttpPageFetcher(TIMEOUT, TIMEOUT);
        final var linkExtractor = new LinkExtractor(urlNormalizer);
        final var crawler = new WebCrawler(pageFetcher, linkExtractor, maxPages, maxConcurrentRequests);

        System.out.println();
        System.out.println("Crawling " + seedUrl + " - Please wait...");
        final var startMs = currentTimeMillis();
        final var links = crawler.crawl(seedUrl);
        final var elapsedMs = currentTimeMillis() - startMs;

        printResults(links, elapsedMs);
    }

    private static String promptString(Scanner scanner, String label, String defaultValue) {
        System.out.printf("%s [%s]: ", label, defaultValue);
        if (!scanner.hasNextLine()) {
            return defaultValue;
        }
        final var input = scanner.nextLine().strip();
        return input.isEmpty() ? defaultValue : input;
    }

    private static int promptInt(Scanner scanner, String label, int defaultValue) {
        System.out.printf("%s [%d]: ", label, defaultValue);
        if (!scanner.hasNextLine()) {
            return defaultValue;
        }
        final var input = scanner.nextLine().strip();
        if (input.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            System.out.printf("Invalid number, using default (%d).%n", defaultValue);
            return defaultValue;
        }
    }

    private static URI parseSeedUri(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Target URL must not be blank");
        }
        final var stripped = rawUrl.strip();
        final var urlWithScheme = (stripped.startsWith("http://") || stripped.startsWith("https://")) ? stripped : "https://" + stripped;
        return URI.create(urlWithScheme);
    }

    private static void printResults(List<CrawledLink> links, long elapsedMs) {
        for (final var link : links) {
            final var label = link.label().isEmpty() ? "[no label]" : link.label();
            System.out.println(label + " -> " + link.url());
        }
        System.out.println("Finished in " + elapsedMs + "ms. Total unique links found: " + links.size());
    }
}
