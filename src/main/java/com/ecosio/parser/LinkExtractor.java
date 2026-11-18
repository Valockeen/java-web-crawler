package com.ecosio.parser;

import com.ecosio.model.CrawledLink;
import com.ecosio.url.UrlNormalizer;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.DOTALL;

public class LinkExtractor {

    private static final Pattern NOISE_PATTERN = Pattern.compile(
            "<!--.*?-->|<script\\b[^>]*>.*?</script>",
            CASE_INSENSITIVE | DOTALL
    );
    private static final Pattern ANCHOR_TAG_PATTERN = Pattern.compile(
            "<a\\b((?:[^\"'>]|\"[^\"]*\"|'[^']*')*)>(.*?)</a>",
            CASE_INSENSITIVE | DOTALL
    );
    private static final Pattern HREF_ATTR_PATTERN = Pattern.compile(
            "(?<![\\w-])href\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'<>`]+))",
            CASE_INSENSITIVE
    );
    private static final Pattern INNER_TAGS_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final UrlNormalizer urlNormalizer;

    public LinkExtractor(UrlNormalizer urlNormalizer) {
        this.urlNormalizer = requireNonNull(urlNormalizer, "urlNormalizer must not be null");
    }

    public Set<CrawledLink> extractLinks(URI currentPageUrl, String rawHtml) {
        if (currentPageUrl == null || rawHtml == null || rawHtml.isBlank()) {
            return Set.of();
        }

        final var sanitizedHtml = NOISE_PATTERN.matcher(rawHtml).replaceAll("");
        final var anchorMatcher = ANCHOR_TAG_PATTERN.matcher(sanitizedHtml);

        final var discoveredLinks = new HashSet<CrawledLink>();

        while (anchorMatcher.find()) {
            final var attributes = anchorMatcher.group(1);
            final var innerHtml = anchorMatcher.group(2);

            final var hrefMatcher = HREF_ATTR_PATTERN.matcher(attributes);
            if (!hrefMatcher.find()) {
                continue;
            }

            final var rawHref = extractHrefValue(hrefMatcher);
            urlNormalizer.normalizeAndFilter(currentPageUrl, rawHref)
                    .ifPresent(normalizedUrl -> {
                        final var cleanLabel = cleanLabel(innerHtml);
                        discoveredLinks.add(new CrawledLink(cleanLabel, normalizedUrl));
                    });
        }

        return Set.copyOf(discoveredLinks);
    }

    private String extractHrefValue(Matcher hrefMatcher) {
        if (hrefMatcher.group(1) != null) {
            return hrefMatcher.group(1);
        }
        if (hrefMatcher.group(2) != null) {
            return hrefMatcher.group(2);
        }
        return hrefMatcher.group(3);
    }

    private String cleanLabel(String innerHtml) {
        if (innerHtml == null || innerHtml.isBlank()) {
            return "";
        }
        String withoutTags = INNER_TAGS_PATTERN.matcher(innerHtml).replaceAll(" ");
        String decoded = decodeBasicEntities(withoutTags);
        return WHITESPACE_PATTERN.matcher(decoded).replaceAll(" ").strip();
    }

    private String decodeBasicEntities(String text) {
        return text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }
}
