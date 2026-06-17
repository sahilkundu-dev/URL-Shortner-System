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

> Transform long, unwieldy URLs into clean, shareable short links — with real-time click analytics, URL expiry, and production-grade input validation.
> Built with the **Cache-Aside pattern**, **Base62 encoding**, **click tracking**, **URL validation**, **per-link TTL expiry**, and a fully layered Spring Boot architecture.

<br/>

```
https://www.example.com/blog/how-to-build-a-scalable-url-shortener-using-java-spring-boot-and-redis
                                          ↓
                            http://localhost:8080/000001
```

<br/>

**13 unit tests · 2 DB tables · 7 REST endpoints · RFC 3986 URL validation · Real-time click analytics · Per-link TTL expiry**

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
- [URL Expiry](#-url-expiry)
- [Running Tests](#-running-tests)
- [Key Design Decisions](#-key-design-decisions)
- [Engineering Concepts Covered](#-engineering-concepts-covered)
- [What I Learned](#-what-i-learned)
- [Roadmap](#️-roadmap)

---

## 🌐 Overview

This is a **production-grade URL Shortener** built from scratch as part of my FAANG preparation journey. It is not a tutorial clone — every architectural decision is deliberate, documented, and explained.

The system currently handles **7 REST endpoints** across 3 functional areas:

| Area | Endpoint | Method | Description |
|---|---|---|---|
| **Core** | `/api/shorten` | POST | Validates + shortens a long URL, optional TTL, returns 6-char Base62 code |
| **Core** | `/{shortCode}` | GET | Looks up URL, checks expiry, records click, returns HTTP 302 or 410 |
| **Analytics** | `/api/analytics/{shortCode}` | GET | Total clicks + last 10 clicks with IP and user-agent |
| **Analytics** | `/api/analytics/{shortCode}/count` | GET | Click count as plain integer |
| **Error** | Any invalid shortCode | — | Clean JSON 404 with timestamp |
| **Error** | Any expired shortCode | — | Clean JSON 410 Gone with timestamp |
| **Error** | Any invalid URL input | — | Clean JSON 400 with descriptive message |

**Real-world problems this solves:**
- Long URLs are ugly, break in emails, and expose internal system structure
- Short links enable click analytics, expiry controls, and clean sharing
- Redis caching eliminates redundant DB reads — the majority of redirects never touch MySQL
- Every redirect is tracked with timestamp, IP, and user-agent for real analytics
- Per-link TTL means time-sensitive links (promos, one-time downloads) automatically expire
- Input validation blocks garbage data, XSS vectors, and unsupported schemes before they hit the DB

---

## ✅ Features Built

| # | Feature | Description | Status |
|---|---|---|---|
| 1 | **URL Shortening** | Base62 encoding of MySQL auto-increment ID, 6-char padded output | ✅ Done |
| 2 | **HTTP Redirect** | 302 redirect with `Location` header to original URL | ✅ Done |
| 3 | **Redis Caching** | Cache-Aside pattern, TTL-aligned to URL expiry, graceful MySQL fallback | ✅ Done |
| 4 | **Duplicate Detection** | Same long URL always returns same short code — zero duplicate rows | ✅ Done |
| 5 | **Click Analytics** | Per-redirect tracking — timestamp, IP address, user-agent | ✅ Done |
| 6 | **Analytics Endpoints** | Total count + recent 10 clicks per short code | ✅ Done |
| 7 | **URL Validation** | RFC 3986 URI parsing, scheme allowlist/blocklist, XSS prevention | ✅ Done |
| 8 | **URL Expiry** | Optional per-link TTL in hours, HTTP 410 Gone on expiry, `@Scheduled` nightly cleanup | ✅ Done |
| 9 | **Global Error Handling** | `@RestControllerAdvice` — clean JSON for 400, 404, 410, 500 | ✅ Done |
| 10 | **13 Unit Tests** | JUnit 5 + Mockito — all business logic paths covered, no infra needed | ✅ Done |
| 11 | **Docker Compose** | MySQL 8 + Redis 7 via containers, zero manual installation | ✅ Done |

---

## ⚙️ How It Works

### 1. Shorten Flow

```
Client  ──POST /api/shorten──▶  UrlController
         { "longUrl": "...",          │
           "ttlHours": 24 }    UrlServiceImpl
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
                                     │    Return existing short URL
                         Compute expiresAt
                         = NOW() + ttlHours (or null if no TTL)
                                     │
                         Save to MySQL (temp shortCode)
                         → auto-increment ID assigned
                                     │
                         Base62.encode(id) → "000003"
                                     │
                         Update shortCode in MySQL
                                     │
                         Cache in Redis
                         TTL = ttlHours (or 24h default)
                                     │
                         Return { shortUrl, longUrl }
```

### 2. Redirect + Expiry Check + Click Tracking Flow

```
Browser ──GET /000003──▶  UrlController
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
                   findByShortCode() → UrlEntity
                   (always needed: expiry check + FK for click)
                              │
                   ┌──────────▼──────────┐
                   │  expiresAt != null  │
                   │  AND NOW() > expiry?│
                   └──────────┬─────────┘
                     Yes ▼         No ▼
                 Evict Redis    Record ClickEntity
                 HTTP 410 Gone  { url_id, clickedAt, ip, ua }
                                     │
                              HTTP 302 redirect
                         Location: https://original-url.com
```

### 3. Analytics Flow

```
Client  ──GET /api/analytics/000003──▶  AnalyticsController
                                                │
                                    findByShortCode() → UrlEntity
                                                │
                              ┌─────────────────┴─────────────────┐
                              ▼                                    ▼
                  countByUrlEntity()              findTop10ByUrlEntityOrderByClickedAtDesc()
                  → totalClicks: N               → List<ClickEntity>
                              │                                    │
                              └────────────────┬───────────────────┘
                                               ▼
                                     Map to ClickResponse DTO
                                     { shortCode, longUrl, totalClicks, recentClicks[] }
```

### 4. Scheduled Cleanup Flow

```
@Scheduled(cron = "0 0 2 * * *")   ← Every day at 2:00 AM
UrlCleanupScheduler.cleanupExpiredUrls()
        │
        ▼
findByExpiresAtBeforeAndExpiresAtIsNotNull(NOW())
        │
        ▼  For each expired UrlEntity:
        ├─ redisTemplate.delete(shortCode)    ← Evict from cache
        ├─ clickRepository.deleteByUrlEntity() ← Delete child rows (FK constraint)
        └─ urlRepository.delete(entity)       ← Delete the URL mapping
        │
        ▼
Log: "Cleanup complete. Deleted N expired URL(s)."
```

---

## 🏗️ Architecture

### Layered Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                         REST API Layer                            │
│    UrlController               │    AnalyticsController           │
│    POST /api/shorten           │    GET /api/analytics/{code}     │
│    GET  /{shortCode}           │    GET /api/analytics/{code}/count│
│                                                                   │
│              GlobalExceptionHandler (@RestControllerAdvice)       │
│         400 (validation) · 404 (not found) · 410 (expired) · 500 │
└──────────────────┬──────────────────────────────┬───────────────┘
                   │                              │
┌──────────────────▼──────────────────────────────▼───────────────┐
│                        Service Layer                              │
│          UrlService (interface) → UrlServiceImpl                  │
│    - UrlValidatorUtil.validate()  — input safety gate             │
│    - Base62Util.encode()          — ID to short code              │
│    - Cache-Aside logic            — Redis read → MySQL fallback   │
│    - Expiry check                 — 410 + Redis eviction          │
│    - Duplicate detection          — findByLongUrl()               │
│    - Click recording              — ClickEntity per redirect      │
│                                                                   │
│          UrlCleanupScheduler (@Scheduled — 2AM daily)             │
│    - Finds expired rows → deletes clicks → deletes URL → evicts   │
└──────────────────┬───────────────────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────────────────┐
│                        Data Layer                                 │
│   UrlRepository        │ ClickRepository      │ RedisTemplate     │
│   findByShortCode()    │ countByUrlEntity()   │ opsForValue()     │
│   findByLongUrl()      │ findTop10By...()     │ get/set/delete    │
│   findByExpiresAt      │ deleteByUrlEntity()  │ TTL-aligned set() │
│   BeforeAnd...()       │                      │                   │
└──────────────────┬───────────────────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────────────────┐
│                    Infrastructure Layer                            │
│        MySQL 8.0 (Docker)              │   Redis 7.0 (Docker)     │
│   Table: url_mappings                  │   Key:   shortCode        │
│     id, long_url, short_code           │   Value: longUrl          │
│     created_at, expires_at             │   TTL:   aligned to expiry│
│                                        │          or 24h default   │
│   Table: url_clicks                    │                           │
│     id, url_id(FK), clicked_at         │                           │
│     ip_address, user_agent             │                           │
└──────────────────────────────────────────────────────────────────┘
```

### Database Schema

```
url_mappings                              url_clicks
─────────────────────────────────        ──────────────────────────────────
id           BIGINT  PK  AI              id           BIGINT  PK  AI
long_url     VARCHAR(2048) NOT NULL      url_id       BIGINT  FK  NOT NULL ──┐
short_code   VARCHAR  UNIQUE             clicked_at   DATETIME    NOT NULL   │
created_at   DATETIME  NOT NULL          ip_address   VARCHAR(45)            │
expires_at   DATETIME  NULL ← NEW        user_agent   VARCHAR(512)           │
                              │                                               │
                              └───────────── references url_mappings(id) ───┘

expires_at = NULL  →  URL never expires
expires_at = set   →  URL expires at that timestamp, 410 returned after
```

> Hibernate auto-creates and evolves both tables on startup via `spring.jpa.hibernate.ddl-auto=update`. The `expires_at` column was added automatically when `UrlEntity` was updated — zero manual SQL.

### Cache-Aside Pattern

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
                         aligned to expiry  └──────────┘

Redis TTL is always aligned to match URL expiry — if a URL expires in 6h,
Redis TTL is set to 6h. This prevents serving stale cache after expiry.
MySQL expiresAt is still the authoritative check on every redirect.
```

---

## 🛠️ Tech Stack

| Layer | Technology | Version | Why This Choice |
|---|---|---|---|
| **Language** | Java | 21 LTS | Latest LTS — virtual threads, pattern matching, records |
| **Framework** | Spring Boot | 3.5.14 | Auto-configuration, embedded Tomcat, DI container, `@Scheduled` support |
| **Database** | MySQL | 8.0 | ACID-compliant — persistent URL mappings, click events, expiry timestamps |
| **Cache** | Redis | 7.0 | In-memory key-value — sub-millisecond lookups, native TTL support |
| **ORM** | Spring Data JPA + Hibernate | Boot-managed | Zero-boilerplate SQL, derived queries, DDL auto-evolution |
| **Testing** | JUnit 5 + Mockito | Boot-managed | Industry standard — mock injection, no infra needed for unit tests |
| **Build** | Maven | 3.9 | Dependency management, reproducible builds |
| **Containers** | Docker + Docker Compose | Latest | Run MySQL + Redis locally — mirrors production environment |

---

## 📁 Project Structure

```
url-shortener/
├── src/
│   ├── main/
│   │   ├── java/com/sahil/url_shortener/
│   │   │   ├── config/
│   │   │   │   └── RedisConfig.java              # RedisTemplate<String,String> bean
│   │   │   │                                     # StringRedisSerializer — human-readable keys
│   │   │   ├── controller/
│   │   │   │   ├── UrlController.java             # POST /api/shorten (accepts ttlHours)
│   │   │   │   │                                  # GET  /{shortCode} → redirect or 410
│   │   │   │   └── AnalyticsController.java       # GET /api/analytics/{code}[/count]
│   │   │   ├── dto/
│   │   │   │   ├── ShortenRequest.java            # { "longUrl": "...", "ttlHours": 24 }
│   │   │   │   ├── ShortenResponse.java           # { "shortUrl": "...", "longUrl": "..." }
│   │   │   │   └── ClickResponse.java             # { shortCode, longUrl,
│   │   │   │                                      #   totalClicks, recentClicks[] }
│   │   │   ├── entity/
│   │   │   │   ├── UrlEntity.java                 # url_mappings table
│   │   │   │   │                                  # Fields: id, longUrl, shortCode,
│   │   │   │   │                                  #         createdAt, expiresAt (nullable)
│   │   │   │   └── ClickEntity.java               # url_clicks table
│   │   │   │                                      # @ManyToOne(LAZY) → UrlEntity
│   │   │   ├── exception/
│   │   │   │   ├── UrlNotFoundException.java      # 404 — shortCode not in DB
│   │   │   │   ├── UrlExpiredException.java       # 410 — URL exists but past expiresAt
│   │   │   │   ├── UrlValidationException.java    # 400 — input URL failed validation
│   │   │   │   └── GlobalExceptionHandler.java    # @RestControllerAdvice
│   │   │   │                                      # 400 · 404 · 410 · 500 → clean JSON
│   │   │   ├── repository/
│   │   │   │   ├── UrlRepository.java             # findByShortCode, findByLongUrl
│   │   │   │   │                                  # findByExpiresAtBeforeAndExpiresAtIsNotNull
│   │   │   │   └── ClickRepository.java           # countByUrlEntity
│   │   │   │                                      # findTop10ByUrlEntityOrderByClickedAtDesc
│   │   │   │                                      # deleteByUrlEntity (cleanup job)
│   │   │   ├── service/
│   │   │   │   ├── UrlService.java                # Interface: shortenUrl(url, ttlHours)
│   │   │   │   │                                  #            getLongUrl(code, ip, ua)
│   │   │   │   ├── UrlServiceImpl.java            # Business logic: validate → cache →
│   │   │   │   │                                  # DB → encode → expiry check → click
│   │   │   │   └── UrlCleanupScheduler.java       # @Scheduled(cron = "0 0 2 * * *")
│   │   │   │                                      # Nightly cleanup of expired URL rows
│   │   │   ├── util/
│   │   │   │   ├── Base62Util.java                # encode(long id) → 6-char padded string
│   │   │   │   │                                  # 62^6 = 56.8 billion unique codes
│   │   │   │   └── UrlValidatorUtil.java          # 7-rule RFC 3986 validation
│   │   │   │                                      # null · length · scheme · URI parse · host
│   │   │   └── UrlShortenerApplication.java       # @SpringBootApplication @EnableScheduling
│   │   └── resources/
│   │       └── application.properties             # datasource · redis · app.base-url
│   └── test/
│       └── java/com/sahil/url_shortener/
│           └── UrlServiceImplTest.java             # 13 unit tests — JUnit 5 + Mockito
├── docker-compose.yml                              # MySQL 8.0 + Redis 7.0 containers
├── pom.xml                                         # Java 21 · Spring Boot 3.5.14
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
This pulls mysql:8.0 and redis:7.0 images and starts both containers. First run takes ~2 minutes to download images. Subsequent starts are instant.

Verify both containers are running:
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
```
Hibernate: create table url_mappings (...)   ← auto-created on first run
Hibernate: create table url_clicks (...)     ← auto-created on first run
Tomcat started on port 8080 (http)
Started UrlShortenerApplication in X.XXX seconds
```

**5. Stop everything when done**
```bash
# Stop the Spring Boot app — Ctrl+C in terminal, or red ■ stop button in IntelliJ

# Stop Docker containers
docker compose down

# Next time you work — just restart both:
docker compose up -d
mvn spring-boot:run
# then run the Spring Boot app
```

---

## 📡 API Reference

### `POST /api/shorten` — Shorten a URL

Validates the URL and returns a 6-character Base62 short code. Optionally accepts a TTL in hours — after which the short URL returns 410 Gone.

**Request (no expiry)**
```bash
curl -X POST http://localhost:8080/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://www.example.com/some/very/long/url/path"}'
```

**Request (with 24-hour expiry)**
```bash
curl -X POST http://localhost:8080/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://www.example.com/some/very/long/url/path", "ttlHours": 24}'
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

Checks expiry, records a click, and returns HTTP 302. Returns 410 if the URL has expired.

**Request**
```bash
curl -v http://localhost:8080/000001
```

**Response `302 Found`** (URL active)
```
HTTP/1.1 302
Location: https://www.example.com/some/very/long/url/path
Content-Length: 0
```

**Response `410 Gone`** (URL expired)
```
HTTP/1.1 410
Content-Type: application/json

{"error":"Short URL has expired: 000001","status":410,"timestamp":"2026-06-15T18:59:52.512619"}
```

---

### `GET /api/analytics/{shortCode}` — Full Analytics

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

---

### `GET /api/analytics/{shortCode}/count` — Click Count

```bash
curl http://localhost:8080/api/analytics/000001/count
# Response: 3
```

---

### Error Responses

| Status | Trigger | Example Response |
|---|---|---|
| `400` | Invalid input URL | `{"error":"URL must not be empty","status":400,"timestamp":"..."}` |
| `404` | Unknown short code | `{"error":"No URL found for short code: zzzzz","status":404,"timestamp":"..."}` |
| `410` | Expired short URL | `{"error":"Short URL has expired: 000001","status":410,"timestamp":"..."}` |
| `500` | Unhandled server error | `{"error":"Something went wrong","status":500,"timestamp":"..."}` |

---

## 🛡️ Input Validation

All incoming URLs pass through `UrlValidatorUtil.validate()` — a 7-rule validation gate that runs before any DB or cache operation.

| Rule | Bad Input Example | Error Message |
|---|---|---|
| Not null or blank | `""` or `null` | `URL must not be empty` |
| Max 2048 characters | URL > 2048 chars | `URL exceeds maximum length of 2048 characters` |
| Blocked scheme: `javascript:` | `javascript:alert(1)` | `URL scheme not allowed. Only http and https are supported` |
| Blocked scheme: `data:` | `data:text/html,<h1>hi</h1>` | `URL scheme not allowed. Only http and https are supported` |
| Blocked scheme: `ftp:` | `ftp://files.server.com` | `URL scheme not allowed. Only http and https are supported` |
| RFC 3986 parse failure | `not-a-url` | `Invalid URL format. Must be a valid http or https URL` |
| No scheme present | `www.google.com` | `Invalid URL format. Must be a valid http or https URL` |
| No host present | `https://` | `Invalid URL format. Must be a valid http or https URL` |

**Why RFC 3986 parsing over regex?**

A URL regex that correctly handles all edge cases (IPv6 hosts, punycode domains, auth credentials, query strings, fragments) becomes hundreds of characters and unmaintainable. Java's `java.net.URI` implements RFC 3986 natively — using the specification itself rather than reimplementing it.

---

## ⏰ URL Expiry

URL expiry is an optional feature — existing behaviour is unchanged if `ttlHours` is not provided.

### How it works

**Creating an expiring URL:**
```bash
curl -X POST http://localhost:8080/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://www.github.com", "ttlHours": 1}'
```

This sets `expiresAt = NOW() + 1 hour` in MySQL and aligns the Redis TTL to 1 hour.

**After expiry:**
```bash
curl -v http://localhost:8080/000003
# HTTP/1.1 410 Gone
# {"error":"Short URL has expired: 000003","status":410,...}
```

### HTTP 410 vs 404 — Why It Matters

| Status | Meaning | When used |
|---|---|---|
| **404 Not Found** | Server has no knowledge of this resource | Unknown short code |
| **410 Gone** | Resource existed but is intentionally gone | Expired short URL |

410 signals to browsers and search engine crawlers: *"This existed and is permanently gone — remove it from your index."* 404 says *"I've never heard of this."* Using 410 for expired URLs is the correct HTTP semantics.

### Nightly Cleanup Job

`UrlCleanupScheduler` runs every day at 2:00 AM via Spring's `@Scheduled`:

```java
@Scheduled(cron = "0 0 2 * * *")
@Transactional
public void cleanupExpiredUrls() {
    // 1. Find all rows where expires_at < NOW() AND expires_at IS NOT NULL
    // 2. For each: evict Redis → delete click rows → delete URL row
    // 3. Log results
}
```

The cleanup order matters — `url_clicks` has a foreign key pointing to `url_mappings`, so click rows must be deleted before the URL row. Deleting in reverse order would violate the FK constraint.

### Redis TTL Alignment

When a URL is shortened with `ttlHours`, the Redis TTL is set to exactly that many hours — not the default 24h. This ensures:
- Cache entries expire in sync with the URL itself
- No stale cache serving after MySQL expiry
- The MySQL `expiresAt` remains the authoritative check on every redirect

---

## 🧪 Running Tests

```bash
mvn test
```

**13 Unit Tests (`UrlServiceImplTest.java`) — all mocked, no DB/Redis required**

| # | Test | What It Proves |
|---|---|---|
| 1 | `shortenUrl_newUrl_savesAndCaches` | New URL → 2 DB saves + Redis write with aligned TTL |
| 2 | `shortenUrl_duplicateUrl_returnsExisting` | Same URL twice → zero new DB rows, same short code |
| 3 | `getLongUrl_cacheHit_returnsFromRedis` | Cache hit → Redis returned, DB still queried (for expiry + click) |
| 4 | `getLongUrl_cacheMiss_fetchesFromDbAndCaches` | Cache miss → MySQL queried, Redis repopulated |
| 5 | `getLongUrl_notFound_throwsException` | Unknown code → `UrlNotFoundException` with code in message |
| 6 | `shortenUrl_newUrl_setsCacheTtlTo24Hours` | No TTL → Redis TTL is exactly `24L, TimeUnit.HOURS` |
| 7 | `getLongUrl_recordsClickOnEveryRedirect` | Every redirect → `clickRepository.save()` called exactly once |
| 8 | `shortenUrl_nullUrl_throwsValidationException` | `null` → `UrlValidationException` with "empty" in message |
| 9 | `shortenUrl_blankUrl_throwsValidationException` | `"   "` → `UrlValidationException` with "empty" in message |
| 10 | `shortenUrl_javascriptUrl_throwsValidationException` | XSS attempt → `UrlValidationException` "scheme not allowed" |
| 11 | `shortenUrl_malformedUrl_throwsValidationException` | `"not-a-url"` → `UrlValidationException` "Invalid URL format" |
| 12 | `shortenUrl_withTtl_setsRedisExpiryToTtlHours` | TTL=6 → Redis TTL is `6L, TimeUnit.HOURS` not 24 |
| 13 | `getLongUrl_expiredUrl_throwsExceptionAndEvictsRedis` | Expired URL → `UrlExpiredException` + Redis eviction + no click saved |

```
✅ 13 tests passed — 13 tests total, 1 sec 99ms
Process finished with exit code 0
```

---

## 🧠 Key Design Decisions

### 1. Base62 Encoding over Hashing
MD5/SHA risks collisions and requires resolution logic. Base62 encoding the auto-incremented MySQL primary key is **collision-free by guarantee** — two rows can never share the same ID, so they can never share the same short code. Left-padded to 6 characters for consistent length. `62⁶ = 56,800,235,584` unique codes.

### 2. HTTP 302 over 301
301 is cached by browsers permanently — kills analytics. 302 re-requests every time — every click reaches your server and is tracked. For a URL shortener that cares about data, 302 is always correct.

### 3. HTTP 410 over 404 for Expired URLs
404 = "I've never heard of this." 410 = "This existed and is intentionally gone." Semantically correct, and tells crawlers to permanently de-index the URL.

### 4. Cache-Aside over Write-Through
Redis is an optimization, not a dependency. Cache-Aside means: if Redis dies, the app falls back to MySQL on every request — zero downtime. Write-Through ties write availability to Redis uptime.

### 5. Redis TTL Aligned to URL Expiry
When a URL has `ttlHours = 6`, the Redis TTL is set to exactly 6 hours — matching the MySQL `expiresAt`. This prevents the cache from serving a URL after it has expired. MySQL `expiresAt` is still the authoritative check on every redirect, but alignment prevents unnecessary DB hits for already-expired entries.

### 6. Always-Query MySQL for Expiry Check + Click Recording
Even on a Redis cache hit, MySQL is always queried to get the `UrlEntity`. This enables: (a) authoritative expiry check — Redis TTL is approximate, MySQL is truth; (b) `@ManyToOne` FK for `ClickEntity`. The cost is one extra DB read per redirect — an intentional tradeoff for correctness.

### 7. `@Scheduled` Cleanup with FK-Ordered Deletion
The cleanup job deletes `url_clicks` rows before `url_mappings` rows. Reversing this order would violate the foreign key constraint. `@Transactional` on the scheduler method wraps each URL's cleanup in a transaction — if anything fails, no partial deletion occurs.

### 8. `expiresAt = NULL` Means Never Expires
No magic values like `9999-12-31`. `null` is semantically clear: this URL has no expiry. The cleanup query explicitly checks `expiresAt IS NOT NULL` so non-expiring URLs are never touched by the scheduler.

### 9. Interface-Based Service Layer
`UrlService` interface → `UrlServiceImpl`. Enables Mockito to mock the interface, controllers to depend on abstractions, and implementations to be swapped without touching callers. Dependency Inversion Principle.

### 10. Constructor Injection over Field Injection
`@RequiredArgsConstructor` on all `@Service` and `@RestController` classes. All injected fields are `final` — immutable, thread-safe, fails fast on startup if missing, works naturally with `@InjectMocks`.

---

## 📐 Engineering Concepts Covered

| Concept | Where in Code | Interview Context |
|---|---|---|
| **Cache-Aside pattern** | `UrlServiceImpl` | System design: caching strategies |
| **Redis TTL alignment** | `shortenUrl()` — TTL computed + set | System design: cache expiry |
| **Base62 encoding** | `Base62Util` | System design: URL shortener deep-dive |
| **HTTP 301 vs 302 vs 410** | `UrlController` + `GlobalExceptionHandler` | HTTP semantics |
| **Per-entity expiry** | `expiresAt` field + expiry check | DB modeling: time-based data |
| **@Scheduled cron jobs** | `UrlCleanupScheduler` | Background jobs, Spring scheduler |
| **FK-ordered deletion** | Cleanup: clicks → URLs | DB: referential integrity |
| **@Transactional on scheduler** | `cleanupExpiredUrls()` | ACID: all-or-nothing cleanup |
| **RFC 3986 URI parsing** | `UrlValidatorUtil` | Security: input validation |
| **XSS scheme injection** | Blocklist in `UrlValidatorUtil` | Security: attack surface |
| **Foreign keys + referential integrity** | `url_clicks.url_id → url_mappings.id` | DB design |
| **N+1 query prevention** | `FetchType.LAZY` on `@ManyToOne` | ORM performance |
| **Derived query methods** | `findTop10ByUrlEntityOrderByClickedAtDesc` | Spring Data JPA |
| **Dependency Inversion** | `UrlService` interface | SOLID principles |
| **Constructor injection** | `@RequiredArgsConstructor` | Spring best practices |
| **@RestControllerAdvice** | `GlobalExceptionHandler` | API: consistent error contracts |
| **Mockito strict mode** | `UnnecessaryStubbingException` fix | Testing best practices |
| **ReflectionTestUtils** | Injecting `@Value` fields in tests | Testability |
| **Docker Compose networking** | `docker-compose.yml` | DevOps: containerized infra |
| **Hibernate DDL evolution** | `ddl-auto=update` + new columns | JPA: schema management |

---

## 📚 What I Learned

**Caching**
- Cache-Aside vs Write-Through: failure modes, when each applies
- Redis must never be the source of truth for expiry decisions
- TTL alignment: why Redis TTL should match URL expiry, not a fixed default
- Cache eviction on expiry: immediately delete Redis entry when 410 is served

**Data Modeling**
- `NULL` as a sentinel value: `expiresAt = null` meaning "never expires"
- Time-based data: storing `expiresAt` vs computing it on read
- FK-ordered deletion: child rows before parent rows
- Hibernate DDL auto-evolution: adding a nullable column with zero migration scripts

**Background Jobs**
- Spring `@Scheduled` cron expression format: `second minute hour day month weekday`
- `@EnableScheduling` is required on the application class — without it, `@Scheduled` is silently ignored
- `@Transactional` on scheduled jobs: ensures all-or-nothing cleanup per URL
- Logging scheduled jobs: `@Slf4j` + structured log messages for observability

**HTTP Semantics**
- 302 vs 301: analytics implications
- 410 Gone vs 404 Not Found: semantic difference, SEO impact
- When to use each 4xx status: 400 (bad input), 404 (not found), 410 (intentionally gone)

**Security**
- URL scheme injection: `javascript:`, `data:`, `vbscript:` as XSS vectors
- Defense in depth: explicit blocklist + structural RFC 3986 validation
- Why regex fails for URLs and the specification itself is the right parser

**Testing**
- 13 unit tests covering all paths: happy path, cache hit/miss, expiry, validation, TTL alignment
- Test 13 verifies: expired URL → exception thrown + Redis evicted + click NOT recorded
- Mockito `verify(redisTemplate).delete(shortCode)` — behavioral assertion on cache eviction

---

## 🗺️ Roadmap

### Completed
- [x] URL shortening with Base62 encoding (6-char padded, 56B+ capacity)
- [x] Redis Cache-Aside pattern with TTL aligned to URL expiry
- [x] HTTP 302 redirect
- [x] Duplicate URL detection — same URL always returns same short code
- [x] Click analytics — per-redirect tracking with timestamp, IP, user-agent
- [x] Analytics endpoints — total count + recent 10 clicks
- [x] URL validation — RFC 3986, scheme blocklist, XSS prevention
- [x] URL expiry — per-link TTL, HTTP 410 Gone, `@Scheduled` nightly cleanup
- [x] Global error handling — clean JSON for 400, 404, 410, 500
- [x] 13 JUnit 5 + Mockito unit tests
- [x] Docker Compose infrastructure

### Planned
- [ ] **Rate limiting** — per-IP throttling on POST /api/shorten, Bucket4j + Redis counter, HTTP 429
- [ ] **Custom alias** — optional user-defined short codes, conflict detection, reserved word blocklist
- [ ] **Swagger / OpenAPI** — `springdoc-openapi`, interactive `/swagger-ui.html`
- [ ] **Actuator health endpoints** — `/actuator/health`, custom Redis + MySQL checks
- [ ] **Async click tracking** — decouple redirect from analytics write with `@Async`
- [ ] **User authentication** — Spring Security + JWT, register/login, per-user URL ownership
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