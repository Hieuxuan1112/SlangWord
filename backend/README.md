# Backend

Spring Boot 3.3 REST API on Java 21 and PostgreSQL 16.

```bash
docker compose up -d db          # from the repository root
mvn spring-boot:run              # http://localhost:8081
mvn verify                       # 23 unit + 31 integration tests
```

No JDK installed? See the container commands in the [root README](../README.md#no-jdk-installed).

## Layout

| Package | Responsibility |
|---|---|
| `domain` | JPA entities. `SlangWord` owns its `Definition` rows; `replaceDefinitions` is the only mutation path. |
| `repository` | Spring Data JPA. Paginated word/definition search, native `findRandom`. |
| `service` | All business rules. Depends on repositories only, so unit tests need no Spring context. |
| `web` | Controllers, DTO mapping, `GlobalExceptionHandler` → RFC 7807. |
| `security` | `JwtService`, `JwtAuthFilter`, `AppUserDetailsService`, `CurrentUser`, `RateLimitFilter`, 401/403 entry point. |
| `config` | Beans and `@ConfigurationProperties` (`JwtProperties`, `CorsProperties`, `SeedProperties`, `RateLimitProperties`), plus `WebMvcConfig` which applies the `/api/v1` prefix. |
| `exception` | `NotFoundException`, `ConflictException`. |

## Configuration

| Property | Environment variable | Default |
|---|---|---|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/slangword` |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | **none — required** |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | **none — required** |
| `app.jwt.secret` | `APP_JWT_SECRET` | **none — required, min 32 chars** |
| `app.jwt.expiration-seconds` | `APP_JWT_EXPIRATION_SECONDS` | `86400` |
| `app.cors.allowed-origins` | `APP_CORS_ORIGINS` | `http://localhost:5173,http://localhost:8080` |
| `app.seed.file` | — | `classpath:data/slang.txt` |
| `app.rate-limit.auth.max-requests` | `APP_RATE_LIMIT_AUTH_MAX` | `10` |
| `app.rate-limit.auth.window` | `APP_RATE_LIMIT_AUTH_WINDOW` | `1m` |

Credentials deliberately have no defaults. A fallback compiled into the jar becomes the
signing key — or database password — of any deployment that forgets to set the variable,
so the application fails at startup instead. `JwtProperties` is `@Validated`, turning a
short or missing key into a startup error that names the property rather than an opaque
`WeakKeyException` on the first login.

## Schema and data

Flyway (`db/migration/V1__init.sql`) owns the schema and nothing else. `SeedService` loads `slang.txt` on startup when `slang_word` is empty, and the same routine backs `POST /api/v1/admin/reset`.

## Coverage

`mvn verify` merges the unit and integration execution data into one report at
`target/site/jacoco/index.html` and fails below the floors in `pom.xml`
(85% instruction, 70% branch). Actual: **91.8% instruction, 77.4% branch, 91.4% line**.

Measuring both suites together is deliberate: most behaviour here is only
exercised through MockMvc, so a unit-only figure would understate it badly.

## Dependency pinning

`tomcat.version` and `postgresql.version` in `pom.xml` override what Spring Boot
manages, to pick up security fixes ahead of the next Boot release. Each override
names the CVE it answers. Remove it once Boot's own pin catches up.

## Tests

`*Test` runs under surefire — plain JUnit 5 and Mockito, no Docker. `*IT` runs under failsafe against a real PostgreSQL container started by Testcontainers, shared across the whole suite by `AbstractIntegrationTest`.
