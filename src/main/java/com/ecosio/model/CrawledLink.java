package com.ecosio.model;

import java.net.URI;
import java.util.Comparator;

import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Objects.requireNonNull;

public record CrawledLink(String label, URI url) implements Comparable<CrawledLink> {

    private static final Comparator<CrawledLink> COMPARATOR = Comparator
            .comparing(CrawledLink::label, CASE_INSENSITIVE_ORDER)
            .thenComparing(CrawledLink::label)
            .thenComparing(link -> link.url().toString());

    public CrawledLink {
        requireNonNull(label, "label must not be null");
        requireNonNull(url, "url must not be null");
        label = label.strip();
    }

    @Override
    public int compareTo(CrawledLink other) {
        return COMPARATOR.compare(this, other);
    }
}
