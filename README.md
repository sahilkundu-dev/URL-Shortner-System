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
[![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](LICENSE)

<br/>

> Transform long, unwieldy URLs into clean, shareable short links.  
> Built with the **Cache-Aside pattern**, **Base62 encoding**, and a fully layered Spring Boot architecture.

<br/>

```
https://www.example.com/blog/how-to-build-a-scalable-url-shortener  →  http://localhost:8080/000001
```

</div>

---

## 📋 Table of Contents

- [Overview](#-overview)
- [How It Works](#-how-it-works)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [API Reference](#-api-reference)
- [Running Tests](#-running-tests)
- [Key Design Decisions](#-key-design-decisions)
- [What I Learned](#-what-i-learned)

---

## 🌐 Overview

This is a **production-grade URL Shortener** built from scratch as part of my FAANG preparation journey. It is not a tutorial clone — every architectural decision is deliberate and documented.

The system handles two core operations:

| Operation | Endpoint | Description |
|---|---|---|
| **Shorten** | `POST /api/shorten` | Accepts a long URL, returns a 6-character Base62 short code |
| **Redirect** | `GET /{shortCode}` | Looks up the original URL and returns HTTP 302 redirect |

**Real-world problems this solves:**
- Long URLs are ugly, break in emails, and reveal internal system structure
- Short links enable click analytics, expiry, and shareable links
- Redis caching reduces database load by serving 99% of redirects from memory

---

## ⚙️ How It Works

### Shorten Flow

```
Client  ──POST /api/shorten──▶  UrlController
                                      │
                              UrlServiceImpl
                                      │
                         ┌────────────▼────────────┐
                         │  Already shortened?      │
                         │  findByLongUrl()         │
                         └────────────┬────────────┘
                              No ▼         Yes ▼
                         Save to MySQL   Return existing
                         (get auto ID)   short URL
                              │
                         Base62.encode(id)
                         e.g. 1 → "000001"
                              │
                         Update shortCode in MySQL
                              │
                         Cache in Redis (TTL: 24h)
                              │
                         Return short URL ◀──────────
```

### Redirect Flow

```
Browser ──GET /000001──▶  UrlController
                                │
                        UrlServiceImpl
                                │
                   ┌────────────▼────────────┐
                   │   Redis cache hit?       │
                   └────────────┬────────────┘
                     Yes ▼           No ▼
                 Return longUrl   MySQL lookup
                                      │
                                 Repopulate Redis
                                      │
                         HTTP 302 ◀───┘
                    Location: https://original-url.com
```

---

## 🏗️ Architecture

### Layered Architecture (Bottom-Up)

```
┌─────────────────────────────────────────────────┐
│                  REST API Layer                  │
│              UrlController.java                  │
│         POST /api/shorten  │  GET /{code}        │
└─────────────────┬───────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────┐
│                 Service Layer                    │
│   UrlService (interface) → UrlServiceImpl        │
│   Business logic: encode, cache, dedup           │
└─────────────────┬───────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────┐
│                  Data Layer                      │
│    UrlRepository (JPA) │ RedisTemplate           │
│    findByShortCode()   │ opsForValue().get/set   │
└─────────────────┬───────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────┐
│               Infrastructure Layer              │
│        MySQL 8.0          │      Redis 7.0       │
│    Table: url_mappings    │   Key: shortCode     │
│    id, longUrl, shortCode │   Value: longUrl     │
│    createdAt              │   TTL: 24 hours      │
└─────────────────────────────────────────────────┘
```

### Cache-Aside Pattern

```
        ┌──────────┐    1. GET shortCode    ┌──────────┐
        │   App    │──────────────────────▶ │  Redis   │
        │          │◀──────────────────────  │  Cache   │
        │          │    2a. Cache HIT ✅     └──────────┘
        │          │
        │          │    2b. Cache MISS ❌
        │          │──────────────────────▶ ┌──────────┐
        │          │◀──────────────────────  │  MySQL   │
        │          │    3. DB result         │    DB    │
        │          │                        └──────────┘
        │          │──────────────────────▶ ┌──────────┐
        └──────────┘    4. Repopulate cache  │  Redis   │
                                            └──────────┘
```

---

## 🛠️ Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| **Language** | Java 21 (LTS) | Latest stable LTS — virtual threads, pattern matching |
| **Framework** | Spring Boot 3.5.14 | Auto-configuration, embedded Tomcat, DI container |
| **Database** | MySQL 8.0 | Persistent URL mappings, ACID-compliant |
| **Cache** | Redis 7.0 | In-memory Cache-Aside, sub-millisecond lookups |
| **ORM** | Spring Data JPA + Hibernate | Zero-SQL CRUD, derived query methods |
| **Testing** | JUnit 5 + Mockito | Unit tests with mock injection, no DB needed |
| **Build** | Maven 3.9 | Dependency management, lifecycle |
| **Containers** | Docker + Docker Compose | MySQL + Redis local infra, no manual install |

---

## 📁 Project Structure

```
url-shortener/
├── src/
│   ├── main/
│   │   ├── java/com/sahil/url_shortener/
│   │   │   ├── config/
│   │   │   │   └── RedisConfig.java          # RedisTemplate<String,String> bean
│   │   │   ├── controller/
│   │   │   │   └── UrlController.java         # POST /api/shorten, GET /{shortCode}
│   │   │   ├── dto/
│   │   │   │   ├── ShortenRequest.java        # { "longUrl": "..." }
│   │   │   │   └── ShortenResponse.java       # { "shortUrl": "...", "longUrl": "..." }
│   │   │   ├── entity/
│   │   │   │   └── UrlEntity.java             # @Entity → url_mappings table
│   │   │   ├── exception/
│   │   │   │   ├── UrlNotFoundException.java  # Custom RuntimeException
│   │   │   │   └── GlobalExceptionHandler.java # @RestControllerAdvice → clean JSON errors
│   │   │   ├── repository/
│   │   │   │   └── UrlRepository.java         # JpaRepository + findByShortCode/LongUrl
│   │   │   ├── service/
│   │   │   │   ├── UrlService.java            # Interface (DIP)
│   │   │   │   └── UrlServiceImpl.java        # Cache-Aside + Base62 + dedup logic
│   │   │   ├── util/
│   │   │   │   └── Base62Util.java            # encode(long id) → 6-char string
│   │   │   └── UrlShortenerApplication.java   # @SpringBootApplication entry point
│   │   └── resources/
│   │       └── application.properties         # DB + Redis + base URL config
│   └── test/
│       └── java/com/sahil/url_shortener/
│           └── UrlServiceImplTest.java         # 6 unit tests, JUnit 5 + Mockito
├── docker-compose.yml                          # MySQL 8 + Redis 7 containers
├── pom.xml                                     # Dependencies + Java 21
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

| Tool | Version | Install |
|---|---|---|
| Java JDK | 21+ | [Download](https://www.oracle.com/java/technologies/downloads/) |
| Maven | 3.9+ | `brew install maven` |
| Docker Desktop | Latest | [Download](https://www.docker.com/products/docker-desktop/) |

### Installation & Run

**1. Clone the repository**
```bash
git clone https://github.com/sahilkundu-dev/URL-Shortner-System.git
cd URL-Shortner-System
```

**2. Start infrastructure (MySQL + Redis)**
```bash
docker compose up -d
```

Verify containers are running:
```bash
docker ps
# Should show: urlshortener-mysql (port 3306) + urlshortener-redis (port 6379)
```

**3. Run the application**
```bash
mvn spring-boot:run
```

Or run `UrlShortenerApplication.java` directly from IntelliJ IDEA.

**4. Verify startup**

Look for these lines in the console:
```
Tomcat started on port 8080 (http)
Started UrlShortenerApplication in X.XXX seconds
```

---

## 📡 API Reference

### `POST /api/shorten` — Shorten a URL

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

**Request**
```bash
curl -v http://localhost:8080/000001
```

**Response `302 Found`**
```
HTTP/1.1 302
Location: https://www.example.com/some/very/long/url/path
```

The browser automatically follows the redirect to the original URL.

---

### Error Responses

**`404 Not Found`** — Short code doesn't exist
```bash
curl http://localhost:8080/zzzzz
```
```json
{
  "error": "No URL found for short code: zzzzz",
  "status": 404,
  "timestamp": "2026-06-10T17:33:16.395750"
}
```

**`500 Internal Server Error`** — Unexpected server error
```json
{
  "error": "Something went wrong",
  "status": 500,
  "timestamp": "2026-06-10T17:33:16.395750"
}
```

---

## 🧪 Running Tests

```bash
mvn test
```

**Test Coverage — 6 Unit Tests (`UrlServiceImplTest.java`)**

| # | Test | Scenario |
|---|---|---|
| 1 | `shortenUrl_newUrl_savesAndCaches` | New URL → saved to MySQL, cached in Redis |
| 2 | `shortenUrl_duplicateUrl_returnsExisting` | Duplicate URL → returns existing code, no new DB row |
| 3 | `getLongUrl_cacheHit_returnsFromRedis` | Cache hit → returns from Redis, DB never touched |
| 4 | `getLongUrl_cacheMiss_fetchesFromDbAndCaches` | Cache miss → fetches from MySQL, repopulates Redis |
| 5 | `getLongUrl_notFound_throwsException` | Unknown code → throws `UrlNotFoundException` |
| 6 | `shortenUrl_newUrl_setsCacheTtlTo24Hours` | Redis TTL enforced to exactly 24 hours |

All tests use **Mockito mocks** — no database or Redis instance required. Tests run in under 1 second.

```
✅ 6 tests passed — 6 tests total
Process finished with exit code 0
```

---

## 🧠 Key Design Decisions

### 1. Base62 Encoding over Hashing
MD5/SHA hashing produces fixed-length strings but risks collisions and requires collision-resolution logic. Base62 encoding the auto-incremented MySQL primary key is **deterministic and collision-free by design** — two rows can never have the same ID, so they can never have the same short code.

With 6 Base62 characters: `62⁶ = 56,800,235,584` unique URLs. Twitter has never needed more.

### 2. HTTP 302 over 301
- **301 Permanent**: Browser caches the redirect forever. No future requests reach your server — kills analytics.
- **302 Temporary**: Browser re-requests every time. You control the redirect, can update it, and can track every click.

For a URL shortener, 302 is always correct.

### 3. Cache-Aside over Write-Through
The Cache-Aside pattern gives you **graceful degradation** — if Redis goes down, the application falls back to MySQL automatically. With Write-Through, a Redis failure blocks writes entirely. Redis should never be the source of truth; MySQL is.

### 4. Interface-Based Service Layer
`UrlService` interface → `UrlServiceImpl` implementation. This enables:
- **Testability**: Mockito can mock the interface in controller tests
- **Swappability**: Replace the implementation without changing the controller
- **Dependency Inversion**: High-level modules depend on abstractions, not concretions

### 5. Constructor Injection over Field Injection
`@RequiredArgsConstructor` generates a constructor for all `final` fields. This is preferred over `@Autowired` on fields because:
- Dependencies are immutable (`final`)
- Fails fast at startup if a dependency is missing
- Works naturally with Mockito's `@InjectMocks`

---

## 📚 What I Learned

Building this project from scratch taught me:

- **Cache-Aside pattern** implementation and when to use it vs Write-Through
- **Base62 encoding** math and why it's superior to hashing for this use case
- **HTTP redirect semantics** — 301 vs 302 and their real-world implications
- **Spring Data JPA** derived query methods (`findByShortCode`, `findByLongUrl`)
- **Mockito** stubbing patterns — `when().thenReturn()`, `verify()`, `never()`, `UnnecessaryStubbingException`
- **`@Value` injection** vs constants, and why it matters for testability
- **Docker Compose** for local infrastructure management
- **`@RestControllerAdvice`** for centralized exception handling

---

## 🗺️ Roadmap

- [ ] Click analytics — track redirects per short code with timestamps
- [ ] Custom alias support — user-defined short codes
- [ ] URL expiry — per-link TTL with scheduled cleanup
- [ ] Rate limiting — per-IP request throttling
- [ ] Swagger / OpenAPI documentation
- [ ] Deploy to Railway/Render with a real short domain

---

## 👤 Author

**Sahil Kundu**  
Associate Software Engineer → targeting FAANG/Product companies  
[![GitHub](https://img.shields.io/badge/GitHub-sahilkundu--dev-181717?style=flat&logo=github)](https://github.com/sahilkundu-dev)

---

<div align="center">

⭐ If this project helped you, consider giving it a star!

</div>
