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

> Transform long, unwieldy URLs into clean, shareable short links — with real-time click analytics.
> Built with the **Cache-Aside pattern**, **Base62 encoding**, **click tracking**, and a fully layered Spring Boot architecture.

<br/>

```
https://medium.com/@j2eeexpert2015/maven-for-java-developers-a-step-by-step-guide-to-setting-up-and-building-projects-59152d09f00c
                                          ↓
                            http://localhost:8080/000004
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
- [Roadmap](#-roadmap)

---

## 🌐 Overview

This is a **production-grade URL Shortener** built from scratch as part of my FAANG preparation journey. It is not a tutorial clone — every architectural decision is deliberate and documented.

The system handles four core operations:

| Operation | Endpoint | Description |
|---|---|---|
| **Shorten** | `POST /api/shorten` | Accepts a long URL, returns a 6-character Base62 short code |
| **Redirect** | `GET /{shortCode}` | Looks up the original URL, records the click, returns HTTP 302 |
| **Analytics** | `GET /api/analytics/{shortCode}` | Returns total click count + last 10 clicks with metadata |
| **Count** | `GET /api/analytics/{shortCode}/count` | Returns total click count as a plain number |

**Real-world problems this solves:**
- Long URLs are ugly, break in emails, and reveal internal system structure
- Short links enable click analytics, expiry, and clean shareable links
- Redis caching reduces database load by serving the majority of redirects from memory
- Every redirect is tracked — timestamp, IP address, and user-agent — for real analytics

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
                         e.g. 3 → "000003"
                              │
                         Update shortCode in MySQL
                              │
                         Cache in Redis (TTL: 24h)
                              │
                         Return short URL ◀──────────
```

### Redirect + Click Tracking Flow

```
Browser ──GET /000003──▶  UrlController
                                │
                        UrlServiceImpl
                                │
                   ┌────────────▼────────────┐
                   │   Redis cache hit?       │
                   └────────────┬────────────┘
                     Yes ▼           No ▼
                 Return cached    MySQL lookup
                 longUrl          + repopulate Redis
                     │                │
                     └────────┬───────┘
                              ▼
                   Record click in url_clicks
                   (timestamp, IP, user-agent)
                              │
                   HTTP 302 redirect
              Location: https://original-url.com
```

### Analytics Flow

```
Client  ──GET /api/analytics/000003──▶  AnalyticsController
                                                │
                                    findByShortCode() → UrlEntity
                                                │
                              ┌─────────────────┴──────────────────┐
                              ▼                                     ▼
                  countByUrlEntity()                findTop10ByUrlEntity
                  → totalClicks: 3                 OrderByClickedAtDesc()
                                                   → recentClicks: [...]
                              │                                     │
                              └─────────────────┬──────────────────┘
                                                ▼
                                      Return ClickResponse JSON
```

---

## 🏗️ Architecture

### Layered Architecture (Bottom-Up)

```
┌──────────────────────────────────────────────────────────────┐
│                        REST API Layer                         │
│         UrlController          │    AnalyticsController       │
│   POST /api/shorten            │  GET /api/analytics/{code}   │
│   GET  /{shortCode}            │  GET /api/analytics/{code}/count│
└──────────────────┬─────────────────────────────┬────────────┘
                   │                             │
┌──────────────────▼─────────────────────────────▼────────────┐
│                       Service Layer                           │
│         UrlService (interface) → UrlServiceImpl               │
│    Business logic: encode, cache, dedup, click recording      │
└──────────────────┬───────────────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────────────┐
│                        Data Layer                             │
│   UrlRepository (JPA)  │  ClickRepository (JPA)  │ RedisTemplate│
│   findByShortCode()    │  countByUrlEntity()      │ get/set TTL  │
│   findByLongUrl()      │  findTop10By...()        │              │
└──────────────────┬───────────────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────────────┐
│                    Infrastructure Layer                        │
│         MySQL 8.0                    │       Redis 7.0         │
│   Table: url_mappings                │  Key:   shortCode       │
│   id, longUrl, shortCode, createdAt  │  Value: longUrl         │
│                                      │  TTL:   24 hours        │
│   Table: url_clicks                  │                         │
│   id, url_id (FK), clickedAt,        │                         │
│   ipAddress, userAgent               │                         │
└──────────────────────────────────────────────────────────────┘
```

### Database Schema

```
url_mappings                         url_clicks
────────────────────────             ───────────────────────────────
id          BIGINT PK AI   ◄──┐      id          BIGINT PK AI
long_url    VARCHAR(2048)     │      url_id      BIGINT FK ──────────┘
short_code  VARCHAR UNIQUE    │      clicked_at  DATETIME NOT NULL
created_at  DATETIME          │      ip_address  VARCHAR(45)
                              └───   user_agent  VARCHAR(512)
```

One `url_mappings` row → many `url_clicks` rows. Standard `@OneToMany` / `@ManyToOne` JPA relationship with `@JoinColumn` enforcing the foreign key at the database level.

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

Redis is never the source of truth — MySQL always is. If Redis goes down, the app gracefully falls back to MySQL on every request.

---

## 🛠️ Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| **Language** | Java 21 (LTS) | Latest stable LTS — virtual threads, pattern matching |
| **Framework** | Spring Boot 3.5.14 | Auto-configuration, embedded Tomcat, DI container |
| **Database** | MySQL 8.0 | Persistent URL mappings + click events, ACID-compliant |
| **Cache** | Redis 7.0 | In-memory Cache-Aside, sub-millisecond lookups |
| **ORM** | Spring Data JPA + Hibernate | Zero-SQL CRUD, derived query methods, FK constraints |
| **Testing** | JUnit 5 + Mockito | 7 unit tests with mock injection, no DB/Redis needed |
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
│   │   │   │   └── RedisConfig.java              # RedisTemplate<String,String> bean with StringRedisSerializer
│   │   │   ├── controller/
│   │   │   │   ├── UrlController.java             # POST /api/shorten, GET /{shortCode}
│   │   │   │   └── AnalyticsController.java       # GET /api/analytics/{shortCode}[/count]
│   │   │   ├── dto/
│   │   │   │   ├── ShortenRequest.java            # { "longUrl": "..." }
│   │   │   │   ├── ShortenResponse.java           # { "shortUrl": "...", "longUrl": "..." }
│   │   │   │   └── ClickResponse.java             # { shortCode, longUrl, totalClicks, recentClicks[] }
│   │   │   ├── entity/
│   │   │   │   ├── UrlEntity.java                 # @Entity → url_mappings table
│   │   │   │   └── ClickEntity.java               # @Entity → url_clicks table, @ManyToOne UrlEntity
│   │   │   ├── exception/
│   │   │   │   ├── UrlNotFoundException.java      # extends RuntimeException (unchecked)
│   │   │   │   └── GlobalExceptionHandler.java    # @RestControllerAdvice → clean JSON errors
│   │   │   ├── repository/
│   │   │   │   ├── UrlRepository.java             # JpaRepository + findByShortCode/LongUrl
│   │   │   │   └── ClickRepository.java           # countByUrlEntity, findTop10ByUrlEntityOrderByClickedAtDesc
│   │   │   ├── service/
│   │   │   │   ├── UrlService.java                # Interface (Dependency Inversion Principle)
│   │   │   │   └── UrlServiceImpl.java            # Cache-Aside + Base62 + dedup + click recording
│   │   │   ├── util/
│   │   │   │   └── Base62Util.java                # encode(long id) → 6-char padded string
│   │   │   └── UrlShortenerApplication.java       # @SpringBootApplication entry point
│   │   └── resources/
│   │       └── application.properties             # DB + Redis + app.base-url config
│   └── test/
│       └── java/com/sahil/url_shortener/
│           └── UrlServiceImplTest.java             # 7 unit tests, JUnit 5 + Mockito
├── docker-compose.yml                              # MySQL 8.0 + Redis 7.0 containers
├── pom.xml                                         # Dependencies + Java 21
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

Verify both containers are running:
```bash
docker ps
# Expected:
# urlshortener-mysql   mysql:8.0   Up   0.0.0.0:3306->3306/tcp
# urlshortener-redis   redis:7.0   Up   0.0.0.0:6379->6379/tcp
```

**3. Run the application**
```bash
mvn spring-boot:run
```

Or open `UrlShortenerApplication.java` in IntelliJ IDEA and click the green ▶ play button.

**4. Verify startup**

Look for these two lines in the console:
```
Tomcat started on port 8080 (http)
Started UrlShortenerApplication in X.XXX seconds
```

Hibernate will also auto-create both tables on first run:
```sql
create table url_mappings (...)
create table url_clicks (...)
alter table url_clicks add constraint foreign key (url_id) references url_mappings (id)
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
Content-Length: 0
```

Every redirect automatically records a click entry in `url_clicks` with the timestamp, IP address, and user-agent.

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

> `0:0:0:0:0:0:0:1` is IPv6 loopback — the correct representation of `127.0.0.1` in an IPv6-enabled environment.

---

### `GET /api/analytics/{shortCode}/count` — Click Count Only

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

**`404 Not Found`** — Short code doesn't exist
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

**`500 Internal Server Error`** — Unexpected server error
```json
{
  "error": "Something went wrong",
  "status": 500,
  "timestamp": "2026-06-14T12:24:10.864851"
}
```

---

## 🧪 Running Tests

```bash
mvn test
```

**Test Coverage — 7 Unit Tests (`UrlServiceImplTest.java`)**

| # | Test | Scenario |
|---|---|---|
| 1 | `shortenUrl_newUrl_savesAndCaches` | New URL → saved to MySQL, cached in Redis with 24h TTL |
| 2 | `shortenUrl_duplicateUrl_returnsExisting` | Duplicate URL → returns existing code, no new DB row |
| 3 | `getLongUrl_cacheHit_returnsFromRedis` | Cache hit → returns from Redis, no Redis re-write |
| 4 | `getLongUrl_cacheMiss_fetchesFromDbAndCaches` | Cache miss → fetches from MySQL, repopulates Redis |
| 5 | `getLongUrl_notFound_throwsException` | Unknown code → throws `UrlNotFoundException` |
| 6 | `shortenUrl_newUrl_setsCacheTtlTo24Hours` | Redis TTL enforced to exactly 24 hours |
| 7 | `getLongUrl_recordsClickOnEveryRedirect` | Every redirect → `clickRepository.save()` called |

All tests use **Mockito mocks** — no database, Redis, or Docker required. Tests run in under 1 second.

```
✅ 7 tests passed — 7 tests total
Process finished with exit code 0
```

---

## 🧠 Key Design Decisions

### 1. Base62 Encoding over Hashing
MD5/SHA hashing produces fixed-length strings but risks collisions and requires collision-resolution logic. Base62 encoding the auto-incremented MySQL primary key is **deterministic and collision-free by design** — two rows can never have the same ID, so they can never have the same short code. Codes are left-padded to 6 characters (`000001`) for consistent length.

With 6 Base62 characters: `62⁶ = 56,800,235,584` unique URLs. Twitter has never needed more.

### 2. HTTP 302 over 301
- **301 Permanent**: Browser caches the redirect forever. No future requests reach your server — kills analytics entirely.
- **302 Temporary**: Browser re-requests every time. You control the redirect, can update it, and every click is tracked.

For a URL shortener with analytics, 302 is always the correct choice.

### 3. Cache-Aside over Write-Through
The Cache-Aside pattern gives you **graceful degradation** — if Redis goes down, the app falls back to MySQL automatically on every request. With Write-Through, a Redis failure blocks writes entirely. Redis is an optimization, not a dependency — MySQL is always the source of truth.

### 4. Always-Query MySQL for Click Recording
The redirect flow always queries MySQL (even on Redis cache hits) to get the `UrlEntity` needed for the `@ManyToOne` relationship in `ClickEntity`. This is a deliberate tradeoff — it adds one DB read per redirect but enables accurate, relationship-preserving click storage without denormalization.

### 5. Interface-Based Service Layer
`UrlService` interface → `UrlServiceImpl` implementation. This enables:
- **Testability**: Mockito mocks the interface — no Spring context needed in unit tests
- **Swappability**: Replace the implementation without changing the controller
- **Dependency Inversion**: High-level modules depend on abstractions, not concretions

### 6. Constructor Injection over Field Injection
`@RequiredArgsConstructor` generates a constructor for all `final` fields. This is preferred over `@Autowired` on fields because:
- Dependencies are immutable (`final`) — thread-safe by design
- Fails fast at startup if any dependency is missing
- Works naturally with Mockito's `@InjectMocks` in unit tests

### 7. `FetchType.LAZY` on the Click-to-URL Relationship
`@ManyToOne(fetch = FetchType.LAZY)` on `ClickEntity.urlEntity` means JPA does not load the full `UrlEntity` when fetching clicks unless explicitly accessed. This prevents N+1 queries when listing 10 recent clicks — without lazy loading, each click fetch would also load its parent URL row unnecessarily.

---

## 📚 What I Learned

Building this project from scratch taught me:

- **Cache-Aside pattern** — implementation, tradeoffs vs Write-Through, graceful degradation
- **Base62 encoding** — the math, why it's collision-free, and why it beats hashing for this problem
- **HTTP redirect semantics** — 301 vs 302 and their real-world implications for analytics
- **Spring Data JPA** — derived query methods (`findByShortCode`, `countByUrlEntity`, `findTop10By...OrderBy...`)
- **JPA relationships** — `@ManyToOne`, `@JoinColumn`, `FetchType.LAZY`, foreign key constraints
- **Mockito** — stubbing patterns, `verify()`, `never()`, `UnnecessaryStubbingException` and how to fix it
- **`@Value` injection** vs constants, and why it matters for testability with `ReflectionTestUtils`
- **Docker Compose** — local infrastructure management, container networking, volume persistence
- **`@RestControllerAdvice`** — centralized exception handling, clean JSON error responses
- **`HttpServletRequest`** — extracting IP address and User-Agent from incoming HTTP requests
- **Hibernate DDL** — `ddl-auto=update` auto-creating and evolving tables from `@Entity` classes

---

## 🗺️ Roadmap

- [x] URL shortening with Base62 encoding
- [x] Redis Cache-Aside pattern with 24h TTL
- [x] HTTP 302 redirect
- [x] Clean 404 JSON error handling via `@RestControllerAdvice`
- [x] Click analytics — per-redirect tracking with timestamp, IP, user-agent
- [x] Analytics endpoints — total count + recent 10 clicks
- [x] 7 JUnit 5 + Mockito unit tests
- [ ] URL validation — reject malformed or non-HTTP URLs
- [ ] Custom alias support — user-defined short codes
- [ ] URL expiry — per-link TTL with `@Scheduled` cleanup
- [ ] Rate limiting — per-IP request throttling with Bucket4j
- [ ] Swagger / OpenAPI documentation
- [ ] User authentication with JWT
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
