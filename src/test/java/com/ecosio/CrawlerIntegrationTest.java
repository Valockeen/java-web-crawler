package com.ecosio;

import com.ecosio.core.WebCrawler;
import com.ecosio.model.CrawledLink;
import com.ecosio.network.HttpPageFetcher;
import com.ecosio.parser.LinkExtractor;
import com.ecosio.url.UrlNormalizer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CrawlerIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private HttpServer server;
    private URI baseUri;
    private Map<String, AtomicInteger> requestCounts;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(newVirtualThreadPerTaskExecutor());
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        requestCounts = new ConcurrentHashMap<>();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void crawlsMultiPageWebsiteEndToEndOverHttp() {
        registerHtmlRoute("/", """
                <html>
                  <body>
                    <!-- <a href="/commented">Commented</a> -->
                    <script>const x = '<a href="/in-script">Script</a>';</script>
                    <a href="/en/about/">About Us</a>
                    <a href="en/blog#top">Blog</a>
                    <a href="/old-careers">Careers</a>
                    <a href="/brochure.pdf">Brochure</a>
                    <a href="/missing">Missing</a>
                    <a href="https://example.com/external">External</a>
                    <a href="mailto:info@ecosio.com">Email</a>
                    <a href="#section">Jump</a>
                  </body>
                </html>
                """);

        registerHtmlRoute("/en/about", """
                <html>
                  <body>
                    <a href="/">Home</a>
                    <a href="/en/team?dept=eng#lead">Engineering Team</a>
                    <a href="/en/blog">Blog</a>
                  </body>
                </html>
                """);

        registerHtmlRoute("/en/blog", """
                <html>
                  <body>
                    <a href="/en/blog/post-1"><span>EDI &amp; API</span> Post</a>
                    <a href="about">About Us</a>
                  </body>
                </html>
                """);

        registerRedirectRoute("/old-careers", "/en/careers");

        registerHtmlRoute("/en/careers", """
                <html>
                  <body>
                    <a data-tip="Apply > now" href=/en/contact>Contact HR</a>
                    <a href="/">Home</a>
                  </body>
                </html>
                """);

        registerHtmlRoute("/en/team", "<html><body>Team</body></html>");
        registerHtmlRoute("/en/blog/post-1", """
                <html>
                  <body>
                    <a href="../blog">Blog</a>
                  </body>
                </html>
                """);
        registerHtmlRoute("/en/contact", "<html><body>Contact</body></html>");

        registerRoute("/brochure.pdf", 200, "application/pdf", "%PDF-1.4");
        registerRoute("/missing", 404, "text/html", "<html><body>Not Found</body></html>");

        var seedUrl = baseUri.resolve("/");
        var urlNormalizer = new UrlNormalizer(seedUrl);
        var pageFetcher = new HttpPageFetcher(TIMEOUT, TIMEOUT);
        var linkExtractor = new LinkExtractor(urlNormalizer);
        var crawler = new WebCrawler(pageFetcher, linkExtractor, 20, 4);

        var result = crawler.crawl(seedUrl);

        assertEquals(List.of(
                new CrawledLink("About Us", baseUri.resolve("/en/about")),
                new CrawledLink("Blog", baseUri.resolve("/en/blog")),
                new CrawledLink("Brochure", baseUri.resolve("/brochure.pdf")),
                new CrawledLink("Careers", baseUri.resolve("/old-careers")),
                new CrawledLink("Contact HR", baseUri.resolve("/en/contact")),
                new CrawledLink("EDI & API Post", baseUri.resolve("/en/blog/post-1")),
                new CrawledLink("Engineering Team", baseUri.resolve("/en/team?dept=eng")),
                new CrawledLink("Home", seedUrl),
                new CrawledLink("Missing", baseUri.resolve("/missing"))
        ), result);

        assertEquals(1, requestCounts.get("/").get());
        assertEquals(1, requestCounts.get("/en/about").get());
        assertEquals(1, requestCounts.get("/en/blog").get());
        assertEquals(1, requestCounts.get("/old-careers").get());
        assertEquals(1, requestCounts.get("/en/careers").get());
        assertEquals(1, requestCounts.get("/en/team").get());
        assertEquals(1, requestCounts.get("/en/blog/post-1").get());
        assertEquals(1, requestCounts.get("/en/contact").get());
        assertEquals(1, requestCounts.get("/brochure.pdf").get());
        assertEquals(1, requestCounts.get("/missing").get());
        assertNull(requestCounts.get("/commented"));
        assertNull(requestCounts.get("/in-script"));
    }

    private void registerHtmlRoute(String path, String html) {
        registerRoute(path, 200, "text/html; charset=utf-8", html);
    }

    private void registerRoute(String path, int statusCode, String contentType, String body) {
        server.createContext(path, exchange -> {
            recordRequest(exchange);
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
    }

    private void registerRedirectRoute(String path, String locationPath) {
        server.createContext(path, exchange -> {
            recordRequest(exchange);
            exchange.getResponseHeaders().set("Location", baseUri.resolve(locationPath).toString());
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
    }

    private void recordRequest(HttpExchange exchange) {
        requestCounts
                .computeIfAbsent(exchange.getRequestURI().getPath(), _ -> new AtomicInteger())
                .incrementAndGet();
    }
}
