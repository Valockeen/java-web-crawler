package com.ecosio.network;

import java.net.URI;
import java.util.Optional;

@FunctionalInterface
public interface PageFetcher {

    Optional<String> fetchHtml(URI url);
}
