# SlangWord Fullstack Platform — Design Spec

- **Date:** 2026-09-07
- **Status:** Approved
- **Repo:** `Hieuxuan1112/SlangWord`, branch `feature/fullstack-platform`

## 1. Context and goal

The repository currently holds a single-file Java Swing desktop app (`SlangDictionaryApp.java`, 350 lines) backed by a backtick-delimited text file (`slang.txt`, 7,641 entries). It implements search, add, edit, delete, reset, random word and a 4-option quiz. It has no build file, no tests, and no README.

**Goal:** rebuild the same domain as a production-shaped fullstack application (Spring Boot REST API + React SPA + PostgreSQL) so the repository demonstrates the skills a Java fullstack engineer job description asks for: Spring Boot, Spring Data JPA, Spring Security/JWT, REST design, validation and error handling, relational modelling, migrations, unit and integration testing, React with TypeScript, Docker, and CI.

**Non-goal:** improving the Swing app. It is archived unchanged as evidence of the starting point.

## 2. Scope

### In scope (v1)

- Full CRUD over slang words, with the original app's overwrite-on-duplicate semantics.
- Search by word and search by definition, paginated.
- Random word.
- Quiz in both directions (guess the word from a definition, guess the definition from a word), 4 options.
- JWT authentication: register, login, roles `USER` and `ADMIN`.
- Per-user search history and quiz statistics.
- Reset the dictionary back to the seed file (`ADMIN` only).
- React SPA covering every one of the above.
- One-command local run via Docker Compose; GitHub Actions CI.

### Out of scope (YAGNI)

Refresh tokens, finer-grained permissions than `USER`/`ADMIN`, rate limiting, i18n, WebSocket or leaderboards, cloud deployment, Redis caching, multi-language definitions.

## 3. Architecture

```
Browser ──HTTP──> frontend (React SPA served by nginx, :8080)
                      │  /api/* reverse-proxied
                      ▼
                  backend (Spring Boot 3.3, Java 21, :8081)
                      │  JDBC
                      ▼
                  PostgreSQL 16 (:5432)
```

Repository layout:

```
SlangWord/
├── backend/            Spring Boot API (Maven)
├── frontend/           React + Vite + TypeScript SPA
├── legacy-swing/       Original Swing app, archived unchanged
├── docs/               Specs and plans
├── docker-compose.yml  Postgres + backend + frontend
└── .github/workflows/  CI
```

The three services are independent processes that share nothing but the HTTP contract. Backend and frontend can each be built, tested and run alone.

## 4. Backend design

### 4.1 Layering

| Layer | Responsibility | Components |
|---|---|---|
| `domain` | JPA entities, no framework logic | `SlangWord` 1─n `Definition`, `User`, `SearchHistory`, `QuizResult`, enums `Role`, `QuizMode` |
| `repository` | Data access | Spring Data JPA interfaces; paginated word/definition search; native `findRandom` |
| `service` | Business rules, unit-testable with Mockito | `SlangWordService`, `QuizService`, `HistoryService`, `AuthService`, `SeedService` |
| `web` | HTTP boundary: controllers, DTO mapping, `@RestControllerAdvice` | 5 controllers + `GlobalExceptionHandler` |
| `security` | Stateless JWT | `JwtService`, `JwtAuthFilter`, `AppUserDetailsService`, `AuthenticatedUser`, BCrypt |
| `config` | Beans and typed configuration | `SecurityConfig`, `JpaConfig`, `OpenApiConfig`, `JwtProperties`, `CorsProperties`, `SeedProperties` |

Each service has one clear purpose and depends only on repositories and other services, never on the web layer. Controllers hold no business logic; they translate HTTP to service calls and DTOs.

### 4.2 Data model

```
app_user(id, username unique, password_hash, role, created_at)
slang_word(id, word unique, created_at, updated_at)
definition(id, slang_word_id → slang_word, text)
search_history(id, user_id, keyword, result_count, searched_at)   idx(user_id, searched_at)
quiz_result(id, user_id, mode, prompt, correct_answer, chosen_answer, is_correct, answered_at)
```

A slang word owning a list of `Definition` rows (rather than one text column) models the real data — `slang.txt` contains entries such as `JCB\`J C Bamford | excavator manufacturer` — and demonstrates a JPA one-to-many with cascade and orphan removal.

Schema is created by Flyway (`V1__init.sql`). Data is not seeded by migration: `SeedService` reads `classpath:data/slang.txt` on startup and populates the table only when it is empty, splitting each line on the backtick into a word and its definitions (further split on ` | `). The same routine backs `POST /api/admin/reset`, which restores the original dictionary — the REST equivalent of the Swing app's Reset button.

### 4.3 API surface

| Method | Path | Auth | Notes |
|---|---|---|---|
| `POST` | `/api/auth/register` | public | 409 if username taken; returns JWT |
| `POST` | `/api/auth/login` | public | 401 on bad credentials; returns JWT |
| `GET` | `/api/slang-words` | public | `q`, `field=word\|definition`, `page`, `size`; paginated |
| `GET` | `/api/slang-words/{word}` | public | 404 if absent |
| `GET` | `/api/slang-words/random` | public | |
| `POST` | `/api/slang-words` | `USER` | `?overwrite=true` to replace; 409 otherwise |
| `PUT` | `/api/slang-words/{word}` | `USER` | replaces the definition list |
| `DELETE` | `/api/slang-words/{word}` | `USER` | 204 |
| `POST` | `/api/admin/reset` | `ADMIN` | wipe and re-seed |
| `GET` | `/api/quiz` | public | `mode=word-from-definition\|definition-from-word`; returns prompt + 4 options |
| `POST` | `/api/quiz/answer` | `USER` | grades and records the attempt |
| `GET` | `/api/history` | `USER` | own search history, paginated |
| `GET` | `/api/history/quiz-stats` | `USER` | answered, correct, accuracy |
| `GET` | `/actuator/health`, `/swagger-ui.html` | public | |

`GET /api/slang-words` records a `SearchHistory` row when the request carries a valid JWT, and silently skips it for anonymous callers. This keeps search public while still giving logged-in users the history feature from the original app.

### 4.4 Error handling

A single `@RestControllerAdvice` converts exceptions to RFC 7807 `ProblemDetail` responses:

| Exception | Status |
|---|---|
| `NotFoundException` | 404 |
| `ConflictException` | 409 |
| `MethodArgumentNotValidException` | 400, detail lists each invalid field |
| `BadCredentialsException` | 401 |
| `IllegalArgumentException` | 400 |

Services throw domain exceptions; they never return `null` to signal absence.

### 4.5 Security

Stateless JWT. `JwtAuthFilter` runs before `UsernamePasswordAuthenticationFilter`, validates the `Bearer` token, and populates the `SecurityContext` with an `AuthenticatedUser` carrying the user id. Passwords are hashed with BCrypt. Authorisation is expressed in `SecurityConfig` for URL patterns and with `@PreAuthorize` for the admin reset. CORS origins come from typed configuration so the compose setup and local `vite dev` can both reach the API.

## 5. Frontend design

React 18 + Vite + TypeScript + Tailwind CSS + TanStack Query + React Router + axios.

| Route | Purpose |
|---|---|
| `/` | Search bar with a word/definition toggle, paginated results, "random word" action |
| `/words/:word` | Detail view; edit and delete actions when logged in |
| `/words/new`, `/words/:word/edit` | Create/edit form, authenticated only |
| `/quiz` | Mode picker, 4-option question, immediate feedback and streak |
| `/history` | Search history table and quiz statistics cards |
| `/login`, `/register` | Authentication forms |

`AuthContext` holds the JWT in memory and mirrors it to `localStorage`; an axios interceptor attaches the `Authorization` header and clears the session on 401. API calls live in `src/api/`, one module per resource, so components never build URLs themselves. Server state is owned by TanStack Query; React state is used only for local UI.

## 6. Testing strategy

Definition of done for each layer:

| Kind | Tooling | Coverage |
|---|---|---|
| Backend unit | JUnit 5 + Mockito | `QuizService` option generation and grading, `SlangWordService` overwrite/conflict rules, `AuthService` registration and login |
| Backend integration | `@SpringBootTest` + Testcontainers Postgres + MockMvc | CRUD round trip, paginated search by word and by definition, register → login → authenticated call, admin reset, 401/403/404/409 paths |
| Frontend unit | Vitest + React Testing Library | search page renders results, quiz card grades an answer, protected route redirects when anonymous |
| CI | GitHub Actions | backend job runs `mvn verify`; frontend job runs `npm ci`, lint, test, build |

No JDK or Maven is installed on the development machine, so every backend build and test run executes inside the `maven:3.9-eclipse-temurin-21` container. The frontend uses the locally installed Node 24.

## 7. Verification

The work is done when:

1. `docker compose up --build` brings up Postgres, the API and the SPA, and the SPA at `http://localhost:8080` can search, view, create, edit, delete, take a quiz, register, log in and show history.
2. `mvn verify` (in Docker) passes all backend unit and integration tests.
3. `npm run lint && npm test && npm run build` passes in `frontend/`.
4. `README.md` lets someone who has only Docker installed run the project from a clean clone.
