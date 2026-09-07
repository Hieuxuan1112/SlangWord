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
| `security` | `JwtService`, `JwtAuthFilter`, `AppUserDetailsService`, `CurrentUser`, 401/403 entry point. |
| `config` | Beans and `@ConfigurationProperties` (`JwtProperties`, `CorsProperties`, `SeedProperties`). |
| `exception` | `NotFoundException`, `ConflictException`. |

## Configuration

| Property | Environment variable | Default |
|---|---|---|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/slangword` |
| `app.jwt.secret` | `APP_JWT_SECRET` | a development placeholder — **override in production** |
| `app.jwt.expiration-seconds` | — | `86400` |
| `app.cors.allowed-origins` | `APP_CORS_ORIGINS` | `http://localhost:5173,http://localhost:8080` |
| `app.seed.file` | — | `classpath:data/slang.txt` |

The JWT secret must be at least 32 bytes; `Keys.hmacShaKeyFor` rejects anything shorter at startup.

## Schema and data

Flyway (`db/migration/V1__init.sql`) owns the schema and nothing else. `SeedService` loads `slang.txt` on startup when `slang_word` is empty, and the same routine backs `POST /api/admin/reset`.

## Tests

`*Test` runs under surefire — plain JUnit 5 and Mockito, no Docker. `*IT` runs under failsafe against a real PostgreSQL container started by Testcontainers, shared across the whole suite by `AbstractIntegrationTest`.
