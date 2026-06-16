<div align="center">

# 🔗 URL Shortener

### A production-grade URL Shortening Service built with Java 21 & Spring Boot 3.5

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.14-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.0-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://redis.io/)
[![Maven](https://img.shields.io/badge/Maven-3.9-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white)](https://maven.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![JUnit5](https://img.shields.io/badge/JUnit-5-25A162?style=for-the-badge&logo=junit5&logoColor=white)](https://junit.org/junit5/)
[![Mockito](https://img.shields.io/badge/Mockito-5-C5D9C8?style=for-the-badge)](https://site.mockito.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](LICENSE)

<br/>

> Transform long, unwieldy URLs into clean, shareable short links — with real-time click analytics and production-grade input validation.
> Built with the **Cache-Aside pattern**, **Base62 encoding**, **click tracking**, **URL validation**, and a fully layered Spring Boot architecture.

<br/>

```
https://medium.com/@j2eeexpert2015/maven-for-java-developers-a-step-by-step-guide-to-setting-up-and-building-projects-59152d09f00c
                                          ↓
                            http://localhost:8080/000004
```

<br/>

**11 unit tests · 3 DB tables · 6 REST endpoints · RFC 3986 URL validation · Real-time click analytics**

</div>

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Features Built](#-features-built)
- [How It Works](#️-how-it-works)
- [Architecture](#️-architecture)
- [Tech Stack](#️-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [API Reference](#-api-reference)
- [Input Validation](#-input-validation)
- [Running Tests](#-running-tests)
- [Key Design Decisions](#-key-design-decisions)
- [Engineering Concepts Covered](#-engineering-concepts-covered)
- [What I Learned](#-what-i-learned)
- [Roadmap](#️-roadmap)

---

## 🌐 Overview

This is a **production-grade URL Shortener** built from scratch as part of my FAANG preparation journey. It is not a tutorial clone — every architectural decision is deliberate, documented, and explained.

The system currently handles **6 REST endpoints** across 3 functional areas:

| Area | Endpoint | Method | Description |
|---|---|---|---|
| **Core** | `/api/shorten` | POST | Validates + shortens a long URL, returns 6-char Base62 code |
| **Core** | `/{shortCode}` | GET | Looks up URL, records click, returns HTTP 302 redirect |
| **Analytics** | `/api/analytics/{shortCode}` | GET | Total clicks + last 10 clicks with IP and user-agent |
| **Analytics** | `/api/analytics/{shortCode}/count` | GET | Click count as plain integer |
| **Error** | Any invalid shortCode | GET | Clean JSON 404 with timestamp |
| **Error** | Any invalid URL input | POST | Clean JSON 400 with descriptive message |

**Real-world problems this solves:**
- Long URLs are ugly, break in emails, and expose internal system structure
- Short links enable click analytics, expiry controls, and clean sharing
- Redis caching eliminates redundant DB reads — majority of redirects never touch MySQL
- Every redirect is tracked with timestamp, IP, and user-agent for real analytics
- Input validation blocks garbage data, XSS vectors, and unsupported schemes before they reach the database

---

## ✅ Features Built

| # | Feature | Description | Status |
|---|---|---|---|
| 1 | **URL Shortening** | Base62 encoding of MySQL auto-increment ID, 6-char padded output | ✅ Done |
| 2 | **HTTP Redirect** | 302 redirect with `Location` header to original URL | ✅ Done |
| 3 | **Redis Caching** | Cache-Aside pattern, 24h TTL, graceful MySQL fallback | ✅ Done |
| 4 | **Duplicate Detection** | Same long URL always returns same short code | ✅ Done |
| 5 | **Click Analytics** | Per-redirect tracking — timestamp, IP, user-agent | ✅ Done |
| 6 | **Analytics Endpoints** | Total count + recent 10 clicks per short code | ✅ Done |
| 7 | **URL Validation** | RFC 3986 URI parsing, scheme allowlist, XSS blocking | ✅ Done |
| 8 | **Global Error Handling** | `@RestControllerAdvice` — clean JSON for 400, 404, 500 | ✅ Done |
| 9 | **11 Unit Tests** | JUnit 5 + Mockito — all business logic paths covered | ✅ Done |
| 10 | **Docker Compose** | MySQL 8 + Redis 7 via containers, zero manual install | ✅ Done |

---

## ⚙️ How It Works

### 1. Shorten Flow

```
Client  ──POST /api/shorten──▶  UrlController
         { "longUrl": "..." }         │
                               UrlServiceImpl
                                      │
                         ┌────────────▼────────────┐
                         │   UrlValidatorUtil       │
                         │   - Not null/blank?      │
                         │   - Length ≤ 2048?       │
                         │   - Safe scheme?         │
                         │   - Valid URI structure? │
                         └────────────┬────────────┘
                              Valid ▼      Invalid ▼
                                     │         400 Bad Request
                         ┌───────────▼──────────┐
                         │  Already shortened?   │
                         │  findByLongUrl()      │
                         └───────────┬──────────┘
                              No ▼        Yes ▼
                         Save to MySQL  Return existing
                         (auto-incr ID) short URL
                              │
                         Base62.encode(id)
                         e.g. ID 5 → "000005"
                              │
                         Update shortCode in MySQL
                              │
                         Cache in Redis (TTL: 24h)
                              │
                         Return { shortUrl, longUrl }
```

### 2. Redirect + Click Tracking Flow

```
Browser ──GET /000005──▶  UrlController
                                │
                        UrlServiceImpl
                                │
                   ┌────────────▼────────────┐
                   │   Redis cache hit?       │
                   └────────────┬────────────┘
                     Yes ▼           No ▼
                 Use cached       MySQL lookup
                 longUrl          + repopulate Redis
                     │                │
                     └────────┬───────┘
                              ▼
                   Always: findByShortCode() for UrlEntity
                   (needed for FK in ClickEntity)
                              │
                   Record ClickEntity in url_clicks
                   { url_id, clickedAt, ipAddress, userAgent }
                              │
                   HTTP 302 ← Location: https://original-url.com
```

### 3. Analytics Flow

```
Client  ──GET /api/analytics/000005──▶  AnalyticsController
                                                │
                                    findByShortCode() → UrlEntity
                                                │
                              ┌─────────────────┴─────────────────┐
                              ▼                                    ▼
                  countByUrlEntity()              findTop10ByUrlEntityOrderByClickedAtDesc()
                  → totalClicks: N               → List<ClickEntity> (last 10)
                              │                                    │
                              └────────────────┬───────────────────┘
                                               ▼
                                     Map to ClickResponse DTO
                                     { shortCode, longUrl, totalClicks, recentClicks[] }
```

### 4. Validation Flow

```
Input URL  ──▶  UrlValidatorUtil.validate(url)
                        │
          ┌─────────────┼─────────────────────────┐
          ▼             ▼             ▼            ▼
     null/blank?   length>2048?  bad scheme?   URI parse fails?
          │             │             │            │
          ▼             ▼             ▼            ▼
       400 + msg     400 + msg    400 + msg    400 + msg
       "must not     "exceeds     "scheme not  "Invalid URL
        be empty"    max length"  allowed"     format"
          │             │             │            │
          └─────────────┴─────────────┴────────────┘
                                │
                         All rules pass ▼
                      Proceed to shortenUrl logic
```

---

## 🏗️ Architecture

### Layered Architecture (Bottom-Up)

```
┌──────────────────────────────────────────────────────────────────┐
│                         REST API Layer                            │
│    UrlController               │    AnalyticsController           │
│    POST /api/shorten           │    GET /api/analytics/{code}     │
│    GET  /{shortCode}           │    GET /api/analytics/{code}/count│
│                                │                                  │
│              GlobalExceptionHandler (@RestControllerAdvice)       │
│              400 (validation) · 404 (not found) · 500 (generic)  │
└──────────────────┬──────────────────────────────┬───────────────┘
                   │                              │
┌──────────────────▼──────────────────────────────▼───────────────┐
│                        Service Layer                              │
│          UrlService (interface) → UrlServiceImpl                  │
│    - UrlValidatorUtil.validate() — input safety gate              │
│    - Base62Util.encode() — ID → short code                        │
│    - Cache-Aside logic — Redis read → MySQL fallback              │
│    - Duplicate detection — findByLongUrl()                        │
│    - Click recording — ClickEntity persisted per redirect         │
└──────────────────┬───────────────────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────────────────┐
│                        Data Layer                                 │
│   UrlRepository        │ ClickRepository      │ RedisTemplate     │
│   findByShortCode()    │ countByUrlEntity()   │ opsForValue()     │
│   findByLongUrl()      │ findTop10By          │ get/set with TTL  │
│                        │ UrlEntityOrderBy...  │                   │
└──────────────────┬───────────────────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────────────────┐
│                    Infrastructure Layer                            │
│        MySQL 8.0 (Docker)              │   Redis 7.0 (Docker)     │
│   Table: url_mappings                  │   Key:   shortCode        │
│     id BIGINT PK AUTO_INCREMENT        │   Value: longUrl          │
│     long_url VARCHAR(2048)             │   TTL:   24 hours         │
│     short_code VARCHAR UNIQUE          │                           │
│     created_at DATETIME                │   StringRedisSerializer   │
│                                        │   (human-readable keys)   │
│   Table: url_clicks                    │                           │
│     id BIGINT PK AUTO_INCREMENT        │                           │
│     url_id BIGINT FK → url_mappings    │                           │
│     clicked_at DATETIME NOT NULL       │                           │
│     ip_address VARCHAR(45)             │                           │
│     user_agent VARCHAR(512)            │                           │
└──────────────────────────────────────────────────────────────────┘
```

### Database Schema

```
url_mappings                              url_clicks
─────────────────────────────            ──────────────────────────────────
id           BIGINT  PK  AI  ◄──┐        id           BIGINT  PK  AI
long_url     VARCHAR(2048)      │        url_id       BIGINT  FK  NOT NULL ──┘
short_code   VARCHAR    UNIQUE  │        clicked_at   DATETIME    NOT NULL
created_at   DATETIME           │        ip_address   VARCHAR(45)
                                         user_agent   VARCHAR(512)

Relationship: ONE url_mappings → MANY url_clicks
JPA: @ManyToOne(fetch = FetchType.LAZY) + @JoinColumn(name = "url_id")
DB:  ALTER TABLE url_clicks ADD CONSTRAINT FK... FOREIGN KEY (url_id) REFERENCES url_mappings(id)
```

> Hibernate auto-creates and evolves both tables on startup via `spring.jpa.hibernate.ddl-auto=update`. Zero manual SQL required.

### Cache-Aside Pattern (Visual)

```
        ┌──────────┐    1. GET shortCode    ┌──────────┐
        │  Spring  │──────────────────────▶ │  Redis   │
        │   App    │◀──────────────────────  │  Cache   │
        │          │    2a. HIT ✅ (~0.1ms)  └──────────┘
        │          │
        │          │    2b. MISS ❌
        │          │──────────────────────▶ ┌──────────┐
        │          │◀──────────────────────  │  MySQL   │
        │          │    3. Result (~10ms)    │    DB    │
        │          │                        └──────────┘
        │          │──────────────────────▶ ┌──────────┐
        └──────────┘  4. Write-back + TTL   │  Redis   │
                                            └──────────┘

Redis is NEVER the source of truth. MySQL always is.
If Redis is down → app falls back to MySQL on every request, zero downtime.
```

---

## 🛠️ Tech Stack

| Layer | Technology | Version | Why This Choice |
|---|---|---|---|
| **Language** | Java | 21 LTS | Latest LTS — virtual threads (Project Loom), pattern matching, records |
| **Framework** | Spring Boot | 3.5.14 | Auto-configuration, embedded Tomcat, DI container, production-ready defaults |
| **Database** | MySQL | 8.0 | ACID-compliant relational store — persistent URL mappings + click events |
| **Cache** | Redis | 7.0 | In-memory key-value store — sub-millisecond lookups, built-in TTL support |
| **ORM** | Spring Data JPA + Hibernate | (Boot-managed) | Zero-boilerplate SQL, derived query methods, DDL auto-creation |
| **Testing** | JUnit 5 + Mockito | (Boot-managed) | Industry standard unit testing stack — mock injection, no infra needed |
| **Build** | Maven | 3.9 | Dependency management, reproducible builds, lifecycle management |
| **Containers** | Docker + Docker Compose | Latest | Run MySQL + Redis locally without installing them — mirrors production |

---

## 📁 Project Structure

```
url-shortener/
├── src/
│   ├── main/
│   │   ├── java/com/sahil/url_shortener/
│   │   │   │
│   │   │   ├── config/
│   │   │   │   └── RedisConfig.java              # Defines RedisTemplate<String,String> bean
│   │   │   │                                     # Uses StringRedisSerializer — human-readable keys
│   │   │   │
│   │   │   ├── controller/
│   │   │   │   ├── UrlController.java             # POST /api/shorten → shorten
│   │   │   │   │                                  # GET /{shortCode}  → redirect + click record
│   │   │   │   └── AnalyticsController.java       # GET /api/analytics/{code}       → full stats
│   │   │   │                                      # GET /api/analytics/{code}/count → count only
│   │   │   │
│   │   │   ├── dto/
│   │   │   │   ├── ShortenRequest.java            # Input  DTO: { "longUrl": "https://..." }
│   │   │   │   ├── ShortenResponse.java           # Output DTO: { "shortUrl", "longUrl" }
│   │   │   │   └── ClickResponse.java             # Output DTO: { shortCode, longUrl,
│   │   │   │                                      #   totalClicks, recentClicks: [{ clickedAt,
│   │   │   │                                      #   ipAddress, userAgent }] }
│   │   │   │
│   │   │   ├── entity/
│   │   │   │   ├── UrlEntity.java                 # @Entity → url_mappings table
│   │   │   │   │                                  # @PrePersist sets createdAt automatically
│   │   │   │   └── ClickEntity.java               # @Entity → url_clicks table
│   │   │   │                                      # @ManyToOne(LAZY) → UrlEntity
│   │   │   │
│   │   │   ├── exception/
│   │   │   │   ├── UrlNotFoundException.java      # extends RuntimeException (unchecked)
│   │   │   │   │                                  # Thrown when shortCode not found in DB
│   │   │   │   ├── UrlValidationException.java    # extends RuntimeException (unchecked)
│   │   │   │   │                                  # Thrown when input URL fails validation
│   │   │   │   └── GlobalExceptionHandler.java    # @RestControllerAdvice
│   │   │   │                                      # Maps exceptions → clean JSON responses
│   │   │   │                                      # 400 (validation) 404 (not found) 500 (generic)
│   │   │   │
│   │   │   ├── repository/
│   │   │   │   ├── UrlRepository.java             # extends JpaRepository<UrlEntity, Long>
│   │   │   │   │                                  # findByShortCode(String) → Optional<UrlEntity>
│   │   │   │   │                                  # findByLongUrl(String)   → Optional<UrlEntity>
│   │   │   │   └── ClickRepository.java           # extends JpaRepository<ClickEntity, Long>
│   │   │   │                                      # countByUrlEntity(UrlEntity) → long
│   │   │   │                                      # findTop10ByUrlEntityOrderByClickedAtDesc()
│   │   │   │
│   │   │   ├── service/
│   │   │   │   ├── UrlService.java                # Interface — Dependency Inversion Principle
│   │   │   │   │                                  # shortenUrl(String longUrl): String
│   │   │   │   │                                  # getLongUrl(String shortCode, ip, ua): String
│   │   │   │   └── UrlServiceImpl.java            # @Service @RequiredArgsConstructor
│   │   │   │                                      # Orchestrates: validate → cache → DB → encode
│   │   │   │
│   │   │   ├── util/
│   │   │   │   ├── Base62Util.java                # encode(long id) → 6-char padded string
│   │   │   │   │                                  # Alphabet: 0-9 A-Z a-z (62 chars)
│   │   │   │   │                                  # 62^6 = 56.8 billion unique codes
│   │   │   │   └── UrlValidatorUtil.java          # validate(String url) — 7 rules
│   │   │   │                                      # null/blank · length · scheme blocklist
│   │   │   │                                      # RFC 3986 URI parse · host check
│   │   │   │
│   │   │   └── UrlShortenerApplication.java       # @SpringBootApplication entry point
│   │   │
│   │   └── resources/
│   │       └── application.properties             # spring.datasource.* · spring.data.redis.*
│   │                                              # app.base-url · spring.jpa.ddl-auto=update
│   │
│   └── test/
│       └── java/com/sahil/url_shortener/
│           └── UrlServiceImplTest.java             # 11 unit tests — JUnit 5 + Mockito
│                                                   # No DB/Redis needed — all mocked
│
├── docker-compose.yml                              # MySQL 8.0 (port 3306) + Redis 7.0 (port 6379)
│                                                   # Persistent MySQL volume (mysql_data)
├── pom.xml                                         # Java 21 · Spring Boot 3.5.14 · all deps
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

| Tool | Version | Check | Install |
|---|---|---|---|
| Java JDK | 21+ | `java -version` | [Download](https://www.oracle.com/java/technologies/downloads/) |
| Maven | 3.9+ | `mvn -version` | `brew install maven` |
| Docker Desktop | Latest | `docker --version` | [Download](https://www.docker.com/products/docker-desktop/) |

### Step-by-Step Setup

**1. Clone the repository**
```bash
git clone https://github.com/sahilkundu-dev/URL-Shortner-System.git
cd URL-Shortner-System
```

**2. Start MySQL + Redis via Docker**
```bash
docker compose up -d
```

This pulls `mysql:8.0` and `redis:7.0` images and starts both containers. First run takes ~2 minutes to download images. Subsequent starts are instant.

```bash
docker ps
# Expected output:
# urlshortener-mysql   mysql:8.0   Up   0.0.0.0:3306->3306/tcp
# urlshortener-redis   redis:7.0   Up   0.0.0.0:6379->6379/tcp
```

**3. Run the Spring Boot application**
```bash
mvn spring-boot:run
```

Or open `UrlShortenerApplication.java` in IntelliJ IDEA and click the ▶ green play button next to `main()`.

**4. Confirm successful startup**

Watch the console for these lines:
```
Hibernate: create table url_mappings (...)   ← auto-created on first run
Hibernate: create table url_clicks (...)     ← auto-created on first run
Tomcat started on port 8080 (http)
Started UrlShortenerApplication in X.XXX seconds
```

Your app is live at `http://localhost:8080`.

**5. Stop everything when done**
```bash
# Stop the Spring Boot app — Ctrl+C in terminal, or red ■ stop button in IntelliJ

# Stop Docker containers
docker compose down

# Next time you work — just restart both:
docker compose up -d
# then run the Spring Boot app
```

---

## 📡 API Reference

### `POST /api/shorten` — Shorten a URL

Validates the input URL and returns a 6-character Base62 short code. If the same long URL is submitted again, returns the existing short code — no duplicate rows created.

**Request**
```bash
curl -X POST http://localhost:8080/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://www.example.com/some/very/long/url/path"}'
```

**Response `200 OK`**
```json
{
  "shortUrl": "http://localhost:8080/000001",
  "longUrl": "https://www.example.com/some/very/long/url/path"
}
```

---

### `GET /{shortCode}` — Redirect to Original URL

Looks up the short code in Redis (fast path) or MySQL (fallback), records a click entry, and returns an HTTP 302 redirect.

**Request**
```bash
curl -v http://localhost:8080/000001
```

**Response `302 Found`**
```
HTTP/1.1 302
Location: https://www.example.com/some/very/long/url/path
Content-Length: 0
```

> The browser automatically follows the `Location` header and loads the original URL. Every call to this endpoint — from any client — records one row in `url_clicks`.

---

### `GET /api/analytics/{shortCode}` — Full Analytics

Returns the total click count and the 10 most recent clicks with full metadata.

**Request**
```bash
curl http://localhost:8080/api/analytics/000001
```

**Response `200 OK`**
```json
{
  "shortCode": "000001",
  "longUrl": "https://www.example.com/some/very/long/url/path",
  "totalClicks": 3,
  "recentClicks": [
    {
      "clickedAt": "2026-06-14T12:23:34.284472",
      "ipAddress": "0:0:0:0:0:0:0:1",
      "userAgent": "curl/8.7.1"
    },
    {
      "clickedAt": "2026-06-14T12:23:34.261188",
      "ipAddress": "0:0:0:0:0:0:0:1",
      "userAgent": "curl/8.7.1"
    },
    {
      "clickedAt": "2026-06-14T12:23:34.226883",
      "ipAddress": "0:0:0:0:0:0:0:1",
      "userAgent": "curl/8.7.1"
    }
  ]
}
```

> `0:0:0:0:0:0:0:1` is the IPv6 loopback address — the correct representation of `127.0.0.1` in dual-stack IPv6 environments. In production with real clients, you'd see actual public IP addresses.

---

### `GET /api/analytics/{shortCode}/count` — Click Count Only

Lightweight endpoint for dashboards that only need a number.

**Request**
```bash
curl http://localhost:8080/api/analytics/000001/count
```

**Response `200 OK`**
```
3
```

---

### Error Responses

All errors return consistent JSON with `error`, `status`, and `timestamp` fields.

**`400 Bad Request`** — Invalid URL input (validation failure)
```bash
curl -X POST http://localhost:8080/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "javascript:alert(1)"}'
```
```json
{
  "error": "URL scheme not allowed. Only http and https are supported",
  "status": 400,
  "timestamp": "2026-06-14T19:58:49.929692"
}
```

**`404 Not Found`** — Short code doesn't exist in the database
```bash
curl http://localhost:8080/zzzzz
```
```json
{
  "error": "No URL found for short code: zzzzz",
  "status": 404,
  "timestamp": "2026-06-14T12:24:10.864851"
}
```

**`500 Internal Server Error`** — Unexpected server error (never exposes stack trace)
```json
{
  "error": "Something went wrong",
  "status": 500,
  "timestamp": "2026-06-14T12:24:10.864851"
}
```

**`410 Gone`** — Resource existed but has been permanently removed
```json
{
  "error": "Short URL has expired: 000006",
  "status": 410,
  "timestamp": "2026-06-14T20:00:00.000000"
}
```

---

## 🛡️ Input Validation

All incoming URLs pass through `UrlValidatorUtil.validate()` before any database or cache operation. The validator applies 7 rules in sequence, failing fast on the first violation.

| Rule | Input Example | Response |
|---|---|---|
| Not null or blank | `""` or `null` | `400 — URL must not be empty` |
| Max 2048 characters | URL > 2048 chars | `400 — URL exceeds maximum length` |
| Blocked scheme: `javascript:` | `javascript:alert(1)` | `400 — URL scheme not allowed` |
| Blocked scheme: `data:` | `data:text/html,<h1>XSS</h1>` | `400 — URL scheme not allowed` |
| Blocked scheme: `ftp:` | `ftp://files.server.com` | `400 — URL scheme not allowed` |
| RFC 3986 URI parse failure | `not-a-url` | `400 — Invalid URL format` |
| No scheme present | `www.google.com` | `400 — Invalid URL format` |
| No host present | `https://` | `400 — Invalid URL format` |
| Only `http` and `https` allowed | `ssh://server.com` | `400 — URL scheme not allowed` |

**Why RFC 3986 over regex?**

A regex that correctly handles all URL edge cases (IPv6 hosts, punycode domains, auth credentials, query strings, fragments) becomes hundreds of characters and unmaintainable. Java's `java.net.URI` implements RFC 3986 natively — the actual internet standard for URLs. It handles every edge case correctly because it's the specification itself.

---

## 🧪 Running Tests

```bash
mvn test
```

**Test Suite — 11 Unit Tests (`UrlServiceImplTest.java`)**

All tests use Mockito mocks. No database, Redis instance, or Docker required. Average runtime: under 500ms.

| # | Test Method | What It Proves |
|---|---|---|
| 1 | `shortenUrl_newUrl_savesAndCaches` | New URL → 2 DB saves (temp + real code) + Redis cache write |
| 2 | `shortenUrl_duplicateUrl_returnsExisting` | Same URL submitted twice → zero new DB rows, same short code |
| 3 | `getLongUrl_cacheHit_returnsFromRedis` | Cache hit → longUrl returned, Redis NOT re-written, DB NOT queried for cache path |
| 4 | `getLongUrl_cacheMiss_fetchesFromDbAndCaches` | Cache miss → MySQL queried, Redis repopulated with TTL |
| 5 | `getLongUrl_notFound_throwsException` | Unknown code → `UrlNotFoundException` thrown with short code in message |
| 6 | `shortenUrl_newUrl_setsCacheTtlTo24Hours` | Redis TTL is exactly `24L, TimeUnit.HOURS` — not a minute more or less |
| 7 | `getLongUrl_recordsClickOnEveryRedirect` | Every redirect → `clickRepository.save()` called exactly once |
| 8 | `shortenUrl_nullUrl_throwsValidationException` | `null` input → `UrlValidationException` with "empty" in message |
| 9 | `shortenUrl_blankUrl_throwsValidationException` | `"   "` input → `UrlValidationException` with "empty" in message |
| 10 | `shortenUrl_javascriptUrl_throwsValidationException` | XSS attempt → `UrlValidationException` with "scheme not allowed" |
| 11 | `shortenUrl_malformedUrl_throwsValidationException` | `"not-a-url"` → `UrlValidationException` with "Invalid URL format" |
| 12 | `shortenUrl_withTtl_setsRedisExpiryToTtlHours` | `` → `UrlExpiredException` with "URL Expiry" | 
| 13 | `getLongUrl_expiredUrl_throwsExceptionAndEvictsRedis` | `UrlExpiredException` → with "URL Not Expired"

```
✅ 11 tests passed — 11 tests total
Process finished with exit code 0
```

---

## 🧠 Key Design Decisions

Every decision here has a reason. Understanding the *why* matters as much as the *what*.

### 1. Base62 Encoding over Hashing

MD5/SHA produces fixed-length strings but has collision risk and requires collision-resolution logic. Base62 encoding the auto-incremented MySQL primary key is **collision-free by mathematical guarantee** — two DB rows can never share the same ID, therefore they can never share the same short code.

```
Alphabet: 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz
Capacity: 62^6 = 56,800,235,584 unique 6-character codes
Padding:  ID 1 → "000001", ID 5 → "000005" (consistent 6-char length)
```

Twitter's URL shortener `t.co` has never exceeded this capacity. Neither has Bitly.

### 2. HTTP 302 over 301

| Status | Browser Behaviour | Effect on Analytics |
|---|---|---|
| 301 Permanent | Caches redirect forever | Kills analytics — future clicks never reach your server |
| 302 Temporary | Re-requests every time | Every click hits your server, can be tracked and counted |

For any URL shortener that cares about analytics, **302 is the only correct choice**. 301 is a premature optimization that destroys observability.

### 3. Cache-Aside over Write-Through

Cache-Aside means: read cache first → on miss, read DB → write to cache. Write-Through means: write cache and DB simultaneously on every write.

Cache-Aside wins here because:
- Redis is an **optimization**, not a requirement — if Redis dies, the app keeps working via MySQL
- Write-Through ties your write path to Redis availability — a Redis failure blocks URL creation
- Cache-Aside enables natural TTL expiry without additional cleanup logic

### 4. Always-Query MySQL for Click Recording

Even on a Redis cache hit, `getLongUrl()` still queries MySQL to get the `UrlEntity` object needed for the `@ManyToOne` foreign key in `ClickEntity`. This is an intentional tradeoff:

- **Adds:** 1 extra DB read per redirect (~10ms)
- **Enables:** Proper relational integrity — clicks are linked to their parent URL at DB level
- **Alternative avoided:** Storing `url_id` in Redis alongside `longUrl` — this couples the cache schema to the DB schema and creates consistency risks

In a high-traffic production system, this would be decoupled using async event publishing (see Roadmap).

### 5. Interface-Based Service Layer

```java
// Controller depends on the interface, not the implementation
private final UrlService urlService;  // NOT UrlServiceImpl

// Enables:
@Mock UrlService urlService;          // Mockito mocks interfaces trivially
```

This is the **Dependency Inversion Principle** (the D in SOLID). High-level modules (controllers) depend on abstractions (interfaces), not concretions (implementations). Swapping `UrlServiceImpl` for a different implementation requires zero controller changes.

### 6. Constructor Injection over Field Injection

```java
// ❌ Field injection — mutable, hidden dependencies, can't use final
@Autowired private UrlRepository urlRepository;

// ✅ Constructor injection via Lombok @RequiredArgsConstructor
private final UrlRepository urlRepository;  // immutable, explicit, testable
```

Constructor injection makes dependencies **explicit, immutable, and visible**. It also fails fast — if a bean is missing, the app won't start. Field injection hides dependencies and makes the class harder to test without a Spring context.

### 7. `FetchType.LAZY` on `@ManyToOne`

```java
@ManyToOne(fetch = FetchType.LAZY)   // Don't load UrlEntity unless accessed
@JoinColumn(name = "url_id")
private UrlEntity urlEntity;
```

Without `LAZY`, fetching 10 `ClickEntity` objects would fire 10 additional `SELECT` queries to load the parent `UrlEntity` for each click — the classic **N+1 query problem**. `LAZY` means JPA only loads the parent entity if you explicitly call `.getUrlEntity()`.

### 8. RFC 3986 URI Parsing over Regex

URL validation regex that handles all real-world cases (IPv6, punycode, auth credentials, encoded chars, fragments) runs into hundreds of characters. Java's `java.net.URI` implements the RFC 3986 specification natively. Using the standard is always preferable to reimplementing it.

---

## 📐 Engineering Concepts Covered

This project touches concepts that appear in FAANG system design and backend engineering interviews. Each one is implemented, not just theorized.

| Concept | Where It Appears | Interview Relevance |
|---|---|---|
| **Cache-Aside pattern** | `UrlServiceImpl` — Redis before MySQL | System design: caching strategies |
| **Base62 encoding** | `Base62Util.encode()` | System design: URL shortener deep-dive |
| **HTTP 301 vs 302** | `UrlController.redirect()` | Web fundamentals + analytics tradeoffs |
| **Foreign keys + referential integrity** | `url_clicks.url_id → url_mappings.id` | DB design: relationships |
| **N+1 query prevention** | `FetchType.LAZY` on `@ManyToOne` | DB performance: ORM pitfalls |
| **Derived query methods** | `findTop10ByUrlEntityOrderByClickedAtDesc()` | Spring Data JPA internals |
| **RFC 3986 URI parsing** | `UrlValidatorUtil` | Security: input validation |
| **XSS / scheme injection prevention** | Scheme blocklist in `UrlValidatorUtil` | Security: attack surface |
| **Dependency Inversion Principle** | `UrlService` interface | SOLID principles |
| **Constructor injection** | `@RequiredArgsConstructor` on all `@Service` / `@RestController` | Spring best practices |
| **@RestControllerAdvice** | `GlobalExceptionHandler` | API design: consistent error contracts |
| **Mock injection** | `@Mock`, `@InjectMocks`, `@ExtendWith(MockitoExtension.class)` | Testing: unit isolation |
| **UnnecessaryStubbingException** | Fixed in test refactor | Mockito strict mode best practices |
| **TTL-based cache expiry** | Redis `set(key, value, 24, TimeUnit.HOURS)` | Caching: expiry strategies |
| **Docker Compose networking** | `docker-compose.yml` service definitions | DevOps: containerized infra |
| **Hibernate DDL** | `ddl-auto=update` in `application.properties` | JPA: schema management |
| **`@PrePersist`** | `UrlEntity.prePersist()` — auto-sets `createdAt` | JPA lifecycle callbacks |

---

## 📚 What I Learned

Building this project from scratch — no tutorials, no scaffold code — taught me:

**Caching**
- Cache-Aside pattern vs Write-Through: tradeoffs, failure modes, when each applies
- Why Redis must never be the source of truth
- TTL design: 24h for short URL cache, implications for stale data

**Data Modeling**
- One-to-many JPA relationships with `@ManyToOne` + `@JoinColumn`
- `FetchType.LAZY` and why EAGER loading causes N+1 query problems
- When to denormalize vs maintain relational integrity
- Foreign key constraints enforced at DB level via Hibernate

**API Design**
- HTTP 301 vs 302 — and why the wrong choice destroys analytics
- Consistent error contracts: every error has `status`, `error`, `timestamp`
- `@RestControllerAdvice` for centralized exception mapping
- HTTP `HttpServletRequest` for extracting client IP and User-Agent

**Security**
- URL scheme injection (`javascript:`, `data:`, `vbscript:`) as XSS attack vectors
- Defense in depth: explicit blocklist + structural validation
- Why regex fails for URL validation and RFC 3986 parsing is the right tool
- Input validation belongs in the service layer, not the controller

**Testing**
- Mockito `@Mock`, `@InjectMocks`, `@ExtendWith` — full unit isolation
- `ReflectionTestUtils.setField()` for injecting `@Value` fields in tests
- `UnnecessaryStubbingException` — what causes it and how to fix it
- `verify()`, `never()`, `times(N)` — behavioral assertions beyond just return values
- Arrange / Act / Assert — universal test structure

**Spring Boot internals**
- `@Value` vs constants — why injected config is testable, constants aren't
- `@RequiredArgsConstructor` — Lombok constructor injection, `final` field immutability
- Spring Data JPA derived queries — method name as query specification
- `@PrePersist` — JPA lifecycle callbacks for automatic field population
- `ddl-auto=update` — Hibernate reads `@Entity` classes, auto-evolves schema

**DevOps**
- Docker Compose: service definitions, port mapping, named volumes for persistence
- Killing native processes occupying ports (`lsof -i :3306`, `kill PID`)
- `docker exec` for running SQL directly in a MySQL container

---

## 🗺️ Roadmap

### Completed
- [x] URL shortening with Base62 encoding (6-char padded, 56B+ capacity)
- [x] Redis Cache-Aside pattern with 24h TTL
- [x] HTTP 302 redirect
- [x] Duplicate URL detection — same long URL always returns same short code
- [x] Click analytics — per-redirect tracking with timestamp, IP, user-agent
- [x] Analytics endpoints — total count + recent 10 clicks
- [x] URL validation — RFC 3986, scheme blocklist, XSS prevention, null/blank/length checks
- [x] URL expiry** — per-link TTL set at creation time, `@Scheduled` cleanup of expired rows, HTTP 410 Gone for expired codes
- [x] Global error handling — clean JSON for 400, 404, 500
- [x] 11 JUnit 5 + Mockito unit tests
- [x] Docker Compose infrastructure

### In Progress / Planned
- [ ] **Custom alias** — optional user-defined short code (e.g. `/my-link`), conflict detection, reserved word blocklist
- [ ] **Rate limiting** — per-IP request throttling on `POST /api/shorten`, Bucket4j or Redis counter, HTTP 429 Too Many Requests
- [ ] **Swagger / OpenAPI** — `springdoc-openapi`, interactive try-it-out UI at `/swagger-ui.html`
- [ ] **Actuator health endpoints** — `/actuator/health`, `/actuator/metrics`, custom Redis + MySQL health checks
- [ ] **Async click tracking** — decouple redirect response from analytics write using Spring `@Async` or application events
- [ ] **User authentication** — Spring Security + JWT, register/login, per-user URL ownership
- [ ] **Paginated URL listing** — `GET /api/urls` with Spring Data `Pageable`, sort by `createdAt`
- [ ] **Deploy to Railway/Render** — `Dockerfile`, managed MySQL + Redis, real short domain

---

## 👤 Author

**Sahil Kundu**
Associate Software Engineer → targeting FAANG / Product-Based Companies

Building in public as part of an 18-month structured FAANG preparation journey — mastering DSA, system design, and production-grade backend engineering.

[![GitHub](https://img.shields.io/badge/GitHub-sahilkundu--dev-181717?style=flat&logo=github)](https://github.com/sahilkundu-dev)

---

<div align="center">

**If this project helped you understand how to build something production-grade from scratch, give it a ⭐**

*Every line of code in this repo was written intentionally — no copy-paste, no magic.*

</div>
