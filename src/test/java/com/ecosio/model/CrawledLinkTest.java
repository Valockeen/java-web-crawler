package com.ecosio.model;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrawledLinkTest {

    @Test
    void constructsAValidCrawledLink() {
        var label = "ecosio";
        var url = URI.create("https://ecosio.com");

        var crawledLink = new CrawledLink(label, url);

        assertEquals(label, crawledLink.label());
        assertEquals(url, crawledLink.url());
    }

    @Test
    void throwsWhenLabelIsNull() {
        String label = null;
        var url = URI.create("https://ecosio.com");

        assertThrows(NullPointerException.class, () -> new CrawledLink(label, url), "label must not be null");
    }

    @Test
    void throwsWhenUrlIsNull() {
        var label = "ecosio";
        URI url = null;

        assertThrows(NullPointerException.class, () -> new CrawledLink(label, url), "url must not be null");
    }

    @Test
    void trimsLabel() {
        var label = " ecosio    ";
        var expectedLabel = label.trim();
        var url = URI.create("https://ecosio.com");

        var crawledLink = new CrawledLink(label, url);

        assertEquals(expectedLabel, crawledLink.label());
    }
}