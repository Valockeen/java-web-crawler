# Crawler

[![Java CI](https://github.com/Valockeen/java-web-crawler/actions/workflows/ci.yml/badge.svg)](https://github.com/Valockeen/java-web-crawler/actions/workflows/ci.yml)
![Java 25](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-Kotlin_DSL-02303A?logo=gradle&logoColor=white)

## Requirements

- Java 25

## Run

```bash
# Linux / macOS
./gradlew run --console=plain

# Windows
.\gradlew.bat run --console=plain
```

Press **Enter** at each prompt to use the defaults (`https://ecosio.com/en/`, max `100` pages, `10` concurrent
requests).

## Test

```bash
./gradlew test
```

## Improvements

Crawl results are only printed to the console and are not persisted due to time constraints. A future improvement
would be to store them in a database or at least write them to a file so the results are not lost.

Some sites such as reddi.com have anti-bot technology which could be bypassed but once again due to time constraints I
did not implement that.

Website redirects are also not handled. For example ecosio.com redirects to https://ecosio.com/en/. This could also be
handled.
