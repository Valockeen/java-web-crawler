package com.ecosio.parser;

import com.ecosio.model.CrawledLink;
import com.ecosio.url.UrlNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkExtractorTest {

    private static final URI SEED_URI = URI.create("https://ecosio.com");
    private static final URI CURRENT_PAGE_URI = URI.create("https://ecosio.com/en/blog/");

    private final UrlNormalizer urlNormalizer = new UrlNormalizer(SEED_URI);
    private final LinkExtractor extractor = new LinkExtractor(urlNormalizer);

    @Test
    void constructorThrowsWhenUrlNormalizerIsNull() {
        var exception = assertThrows(NullPointerException.class, () -> new LinkExtractor(null));
        assertEquals("urlNormalizer must not be null", exception.getMessage());
    }

    @Test
    void returnsEmptySetWhenCurrentPageUrlIsNull() {
        var html = "<a href=\"/en/about\">About</a>";

        var result = extractor.extractLinks(null, html);

        assertTrue(result.isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n\r"})
    void returnsEmptySetWhenRawHtmlIsNullOrBlank(String rawHtml) {
        var result = extractor.extractLinks(CURRENT_PAGE_URI, rawHtml);

        assertTrue(result.isEmpty());
    }

    @Test
    void extractsDoubleSingleAndUnquotedLinksAndResolvesRelativeUrls() {
        var html = """
                <html>
                  <body>
                    <a href="/en/careers/#positions">Careers</a>
                    <a href='post-1'>First Post</a>
                    <a href=/en/partners>Partners</a>
                    <a href=https://ecosio.com/search?q=edi class="search">Search</a>
                    <A class="cta" HREF = "https://www.ecosio.com/en/contact/" target="_blank">Contact</A>
                  </body>
                </html>
                """;

        var result = extractor.extractLinks(CURRENT_PAGE_URI, html);

        assertEquals(Set.of(
                new CrawledLink("Careers", URI.create("https://ecosio.com/en/careers")),
                new CrawledLink("First Post", URI.create("https://ecosio.com/en/blog/post-1")),
                new CrawledLink("Partners", URI.create("https://ecosio.com/en/partners")),
                new CrawledLink("Search", URI.create("https://ecosio.com/search?q=edi")),
                new CrawledLink("Contact", URI.create("https://www.ecosio.com/en/contact"))
        ), result);
    }

    @Test
    void skipsClosingAngleBracketsInsideQuotedAttributes() {
        var html = """
                <a data-tooltip="Click > here" href="/en/double-quoted">Double Quoted</a>
                <a data-tooltip='Go > now' href='/en/single-quoted'>Single Quoted</a>
                <a href="https://ecosio.com/en/path?expr=a%3Eb" title="1 > 0">With Query</a>
                """;

        var result = extractor.extractLinks(CURRENT_PAGE_URI, html);

        assertEquals(Set.of(
                new CrawledLink("Double Quoted", URI.create("https://ecosio.com/en/double-quoted")),
                new CrawledLink("Single Quoted", URI.create("https://ecosio.com/en/single-quoted")),
                new CrawledLink("With Query", URI.create("https://ecosio.com/en/path?expr=a%3Eb"))
        ), result);
    }

    @Test
    void ignoresAnchorsWithoutHrefAndAttributesLikeDataHref() {
        var html = """
                <a>Missing Href</a>
                <a class="button" id="cta">No Href With Attrs</a>
                <a href=>Empty Unquoted Href</a>
                <a data-href="/en/ignored">Data Href Only</a>
                <a data-href="/en/ignored" href="/en/actual">Actual Href</a>
                <abbr title="a > b">Abbreviation</abbr>
                """;

        var result = extractor.extractLinks(CURRENT_PAGE_URI, html);

        assertEquals(Set.of(
                new CrawledLink("Actual Href", URI.create("https://ecosio.com/en/actual"))
        ), result);
    }

    @Test
    void ignoresLinksInsideHtmlCommentsAndScriptTags() {
        var html = """
                <!-- <a href="/commented-single-line">Ignored Comment</a> -->
                <!--
                  multiline comment
                  <a href="/commented-multiline">Ignored Multiline</a>
                -->
                <script>
                  const link = '<a href="/in-script">Ignored Script</a>';
                </script>
                <SCRIPT type="text/javascript">
                  const another = "<a href='/in-uppercase-script'>Ignored</a>";
                </SCRIPT>
                <a href="/en/valid">Valid Link</a>
                """;

        var result = extractor.extractLinks(CURRENT_PAGE_URI, html);

        assertEquals(Set.of(
                new CrawledLink("Valid Link", URI.create("https://ecosio.com/en/valid"))
        ), result);
    }

    @Test
    void stripsInnerHtmlTagsAndCollapsesWhitespaceInLabel() {
        var html = """
                <a href="/en/platform">
                  <span>ecosio</span>
                  <strong>Platform</strong>
                </a>
                <a href="/en/logo"><img src="/logo.png" alt="ecosio"></a>
                <a href="/en/empty">   </a>
                """;

        var result = extractor.extractLinks(CURRENT_PAGE_URI, html);

        assertEquals(Set.of(
                new CrawledLink("ecosio Platform", URI.create("https://ecosio.com/en/platform")),
                new CrawledLink("", URI.create("https://ecosio.com/en/logo")),
                new CrawledLink("", URI.create("https://ecosio.com/en/empty"))
        ), result);
    }

    @Test
    void decodesBasicHtmlEntitiesInLabel() {
        var html = """
                <a href="/en/docs">EDI&nbsp;&amp;&nbsp;API &lt;Docs&gt; &quot;v2&quot; &#39;Guide&#39;</a>
                """;

        var result = extractor.extractLinks(CURRENT_PAGE_URI, html);

        assertEquals(Set.of(
                new CrawledLink("EDI & API <Docs> \"v2\" 'Guide'", URI.create("https://ecosio.com/en/docs"))
        ), result);
    }

    @Test
    void filtersOutExternalNonHttpAndFragmentOnlyLinks() {
        var html = """
                <a href="https://example.com/external">External</a>
                <a href="https://blog.ecosio.com/post">Other Subdomain</a>
                <a href="mailto:info@ecosio.com">Email</a>
                <a href="javascript:void(0)">JS</a>
                <a href="#top">Back to top</a>
                <a href="/en/allowed">Allowed</a>
                """;

        var result = extractor.extractLinks(CURRENT_PAGE_URI, html);

        assertEquals(Set.of(
                new CrawledLink("Allowed", URI.create("https://ecosio.com/en/allowed"))
        ), result);
    }

    @Test
    void deduplicatesIdenticalLinksAndKeepsDifferentLabelsForSameUrl() {
        var html = """
                <a href="/en/about/">About Us</a>
                <a href="https://ecosio.com/en/about#team">About Us</a>
                <a href="/en/about">Learn More</a>
                """;

        var result = extractor.extractLinks(CURRENT_PAGE_URI, html);

        assertEquals(Set.of(
                new CrawledLink("About Us", URI.create("https://ecosio.com/en/about")),
                new CrawledLink("Learn More", URI.create("https://ecosio.com/en/about"))
        ), result);
    }

    @Test
    void returnsUnmodifiableSet() {
        var html = "<a href=\"/en/about\">About</a>";

        var result = extractor.extractLinks(CURRENT_PAGE_URI, html);

        assertThrows(UnsupportedOperationException.class,
                () -> result.add(new CrawledLink("Other", URI.create("https://ecosio.com/other"))));
    }
}