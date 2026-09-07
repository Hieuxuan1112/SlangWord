# SlangWord Fullstack Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the archived Swing slang dictionary as a Spring Boot REST API plus a React SPA on PostgreSQL, tested and runnable with one Docker command.

**Architecture:** Three independent processes talking over HTTP/JSON — nginx-served React SPA → Spring Boot API → PostgreSQL. The backend is layered `domain → repository → service → web`, with services holding all business rules so they are unit-testable without Spring. Schema comes from Flyway; the 7,641-entry dictionary is seeded from the original `slang.txt` at startup.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring Data JPA, Spring Security + JJWT 0.12, Flyway, PostgreSQL 16, JUnit 5, Mockito, Testcontainers, Maven. React 18, TypeScript, Vite, Tailwind CSS, TanStack Query, React Router, axios, Vitest, React Testing Library. Docker Compose, GitHub Actions.

**Machine constraint:** this workstation has Node 24 and Docker but **no JDK and no Maven**. Every backend build/test command therefore runs inside a container. Define this alias once per shell session:

```bash
alias mvnd='docker run --rm -v "/d/SlangWord/backend":/app -v "$HOME/.m2":/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn'
```

Integration tests use Testcontainers, which needs the Docker socket. Run them from the host with the socket mounted:

```bash
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  -v "/d/SlangWord/backend":/app -v "$HOME/.m2":/root/.m2 -w /app \
  --network host maven:3.9-eclipse-temurin-21 mvn verify
```

**Git:** the repository owner runs all `git add`/`commit`/`push` themselves. Commit steps below are written for them; the implementing agent must not run git write commands.

---

## File Structure

### Backend (`backend/`)

| File | Responsibility |
|---|---|
| `pom.xml` | Dependencies, Java 21, Spring Boot parent |
| `Dockerfile`, `.dockerignore` | Multi-stage build (maven → temurin-jre) |
| `src/main/resources/application.yml` | Base config; `application-docker.yml` for compose |
| `src/main/resources/db/migration/V1__init.sql` | Schema only, no data |
| `src/main/resources/data/slang.txt` | Original 7,641-entry seed file |
| `domain/SlangWord.java` | Aggregate root, owns definitions |
| `domain/Definition.java` | Child entity |
| `domain/User.java`, `domain/Role.java` | Account and role |
| `domain/SearchHistory.java` | One search event |
| `domain/QuizResult.java`, `domain/QuizMode.java` | One graded quiz answer |
| `repository/*.java` | Four Spring Data interfaces |
| `dto/SlangWordDtos.java` | Request/response records for words |
| `dto/AuthDtos.java`, `dto/QuizDtos.java`, `dto/HistoryDtos.java` | Records per feature |
| `dto/PageResponse.java` | Framework-free pagination envelope |
| `service/SeedService.java` | Parse `slang.txt`, load-if-empty, reset |
| `service/SlangWordService.java` | CRUD + search rules |
| `service/QuizService.java` | Question generation and grading |
| `service/HistoryService.java` | Record and read history/stats |
| `service/AuthService.java` | Register, login, token issue |
| `security/JwtService.java` | Sign and verify tokens |
| `security/JwtAuthFilter.java` | Bearer token → SecurityContext |
| `security/AppUserDetailsService.java`, `security/AuthenticatedUser.java` | Spring Security adapters |
| `config/SecurityConfig.java` | Filter chain, CORS, password encoder |
| `config/JpaConfig.java` | `@EnableJpaAuditing` |
| `config/OpenApiConfig.java` | Swagger metadata + bearer scheme |
| `config/JwtProperties.java`, `CorsProperties.java`, `SeedProperties.java` | Typed config |
| `web/SlangWordController.java` | `/api/slang-words` |
| `web/AuthController.java` | `/api/auth` |
| `web/QuizController.java` | `/api/quiz` |
| `web/HistoryController.java` | `/api/history` |
| `web/AdminController.java` | `/api/admin` |
| `web/GlobalExceptionHandler.java` | Exceptions → RFC 7807 |
| `exception/NotFoundException.java`, `ConflictException.java` | Domain exceptions |

### Frontend (`frontend/`)

| File | Responsibility |
|---|---|
| `src/api/client.ts` | axios instance + auth interceptor |
| `src/api/slangWords.ts`, `auth.ts`, `quiz.ts`, `history.ts` | One module per resource |
| `src/auth/AuthContext.tsx` | Token state, login/logout |
| `src/auth/RequireAuth.tsx` | Route guard |
| `src/components/*` | `Layout`, `SearchBar`, `WordCard`, `Pagination`, `QuizCard`, `StatCard` |
| `src/pages/*` | `SearchPage`, `WordDetailPage`, `WordFormPage`, `QuizPage`, `HistoryPage`, `LoginPage`, `RegisterPage` |
| `src/types.ts` | Shared API types |
| `Dockerfile`, `nginx.conf` | Build + serve + `/api` proxy |

### Root

`docker-compose.yml`, `.github/workflows/ci.yml`, `README.md`, `.gitignore`, `legacy-swing/README.md`.

---

## Task 1: Backend skeleton boots against Postgres

**Files:**
- Create: `backend/pom.xml`, `backend/src/main/java/com/slangword/SlangWordApplication.java`
- Create: `backend/src/main/resources/application.yml`, `backend/src/main/resources/application-docker.yml`
- Create: `backend/src/main/resources/db/migration/V1__init.sql`
- Create: `docker-compose.yml`, `.gitignore`
- Move: original `slang.txt` → `backend/src/main/resources/data/slang.txt`; `SlangDictionaryApp.java`, `Type Parameter.txt`, a copy of `slang.txt` → `legacy-swing/`

- [ ] **Step 1: Write `V1__init.sql`**

```sql
create table app_user (
    id            bigserial primary key,
    username      varchar(64)  not null unique,
    password_hash varchar(255) not null,
    role          varchar(16)  not null,
    created_at    timestamptz  not null
);

create table slang_word (
    id         bigserial primary key,
    word       varchar(128) not null unique,
    created_at timestamptz  not null,
    updated_at timestamptz  not null
);
create index idx_slang_word_word_lower on slang_word (lower(word));

create table definition (
    id            bigserial primary key,
    slang_word_id bigint        not null references slang_word (id) on delete cascade,
    text          varchar(2000) not null
);
create index idx_definition_word on definition (slang_word_id);

create table search_history (
    id           bigserial primary key,
    user_id      bigint       not null references app_user (id) on delete cascade,
    keyword      varchar(128) not null,
    result_count integer      not null,
    searched_at  timestamptz  not null
);
create index idx_history_user on search_history (user_id, searched_at desc);

create table quiz_result (
    id             bigserial primary key,
    user_id        bigint       not null references app_user (id) on delete cascade,
    mode           varchar(32)  not null,
    prompt         varchar(512) not null,
    correct_answer varchar(512) not null,
    chosen_answer  varchar(512),
    is_correct     boolean      not null,
    answered_at    timestamptz  not null
);
create index idx_quiz_user on quiz_result (user_id, answered_at desc);
```

- [ ] **Step 2: Write `application.yml`**

```yaml
spring:
  application:
    name: slangword-api
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/slangword}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate.jdbc.batch_size: 50
  flyway:
    enabled: true

server:
  port: 8081

app:
  jwt:
    secret: ${APP_JWT_SECRET}   # no default: see the security note at the end of this plan
    expiration-seconds: 86400
  cors:
    allowed-origins: ${APP_CORS_ORIGINS:http://localhost:5173,http://localhost:8080}
  seed:
    file: classpath:data/slang.txt

management:
  endpoints.web.exposure.include: health,info
  endpoint.health.probes.enabled: true

springdoc:
  swagger-ui.path: /swagger-ui.html
```

- [ ] **Step 3: Write `docker-compose.yml` with the Postgres service only for now**

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: slangword
      POSTGRES_USER: ${POSTGRES_USER:?set it in .env}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?set it in .env}
    ports: ["5432:5432"]
    volumes: ["pgdata:/var/lib/postgresql/data"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U slangword -d slangword"]
      interval: 5s
      timeout: 5s
      retries: 10
volumes:
  pgdata:
```

- [ ] **Step 4: Verify the module compiles**

Run: `mvnd -q compile`
Expected: BUILD SUCCESS, no output.

- [ ] **Step 5: Verify Flyway applies against a live database**

Run: `docker compose up -d db` then `mvnd -q spring-boot:run` in a second shell; hit `curl -s localhost:8081/actuator/health`.
Expected: `{"status":"UP"}`, and the log shows `Successfully applied 1 migration`.

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml backend/src/main/java backend/src/main/resources docker-compose.yml .gitignore legacy-swing
git commit -m "feat(backend): bootstrap Spring Boot module with Flyway schema"
```

---

## Task 2: Entities and repositories

**Files:**
- Create: `domain/SlangWord.java`, `Definition.java`, `User.java`, `Role.java`, `SearchHistory.java`, `QuizResult.java`, `QuizMode.java`
- Create: `repository/SlangWordRepository.java`, `UserRepository.java`, `SearchHistoryRepository.java`, `QuizResultRepository.java`
- Create: `config/JpaConfig.java`
- Test: `src/test/java/com/slangword/AbstractIntegrationTest.java`, `src/test/java/com/slangword/repository/SlangWordRepositoryIT.java`
- Create: `src/test/resources/application-test.yml`

- [ ] **Step 1: Write the Testcontainers base class**

```java
package com.slangword;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

A single static container is reused by every integration test class, so the suite pays the Postgres startup cost once.

- [ ] **Step 2: Write `src/test/resources/application-test.yml`**

```yaml
app:
  seed:
    file: classpath:data/slang-test.txt
  jwt:
    secret: ${TEST_JWT_SECRET:a-deterministic-value-for-tests-only-see-application-test-yml}
    expiration-seconds: 3600
logging.level.org.hibernate.SQL: warn
```

- [ ] **Step 3: Write the failing repository test**

```java
package com.slangword.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.slangword.AbstractIntegrationTest;
import com.slangword.domain.SlangWord;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

class SlangWordRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    SlangWordRepository repository;

    @BeforeEach
    void reset() {
        repository.deleteAll();
    }

    @Test
    void savesWordWithItsDefinitions() {
        SlangWord word = new SlangWord("JCB");
        word.replaceDefinitions(List.of("J C Bamford", "excavator manufacturer"));
        repository.save(word);

        SlangWord found = repository.findByWordIgnoreCase("jcb").orElseThrow();
        assertThat(found.getDefinitions()).extracting("text")
                .containsExactly("J C Bamford", "excavator manufacturer");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void findsByDefinitionFragment() {
        SlangWord word = new SlangWord("BBE");
        word.replaceDefinitions(List.of("Babe"));
        repository.save(word);

        assertThat(repository.searchByDefinition("bab", PageRequest.of(0, 10)))
                .extracting(SlangWord::getWord).containsExactly("BBE");
    }
}
```

- [ ] **Step 4: Run it and watch it fail**

Run the Testcontainers command from the header with `-Dtest=SlangWordRepositoryIT`.
Expected: compilation failure — `SlangWord` does not exist yet.

- [ ] **Step 5: Write the entities**

`SlangWord` is the aggregate root. It owns its definitions with `cascade = ALL, orphanRemoval = true` so `replaceDefinitions` is the only mutation path callers need:

```java
@Entity
@Table(name = "slang_word")
@EntityListeners(AuditingEntityListener.class)
public class SlangWord {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String word;

    @OneToMany(mappedBy = "slangWord", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Definition> definitions = new ArrayList<>();

    @CreatedDate @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate @Column(nullable = false)
    private Instant updatedAt;

    protected SlangWord() { }

    public SlangWord(String word) { this.word = word; }

    public void replaceDefinitions(List<String> texts) {
        definitions.clear();
        texts.forEach(text -> definitions.add(new Definition(this, text)));
    }
    // getters; setWord(String)
}
```

`Definition` holds `@ManyToOne(fetch = LAZY, optional = false) SlangWord slangWord` and `@Column(length = 2000) String text`.

`User` holds `username` (unique, 64), `passwordHash`, `@Enumerated(STRING) Role role`, `@CreatedDate createdAt`.

`SearchHistory` holds `userId`, `keyword`, `resultCount`, `@CreatedDate searchedAt`.

`QuizResult` holds `userId`, `@Enumerated(STRING) QuizMode mode`, `prompt`, `correctAnswer`, `chosenAnswer`, `boolean correct` mapped to `is_correct`, `@CreatedDate answeredAt`.

`Role` is `{USER, ADMIN}`. `QuizMode` is `{WORD_FROM_DEFINITION, DEFINITION_FROM_WORD}`.

- [ ] **Step 6: Write the repositories**

```java
public interface SlangWordRepository extends JpaRepository<SlangWord, Long> {

    @EntityGraph(attributePaths = "definitions")
    Optional<SlangWord> findByWordIgnoreCase(String word);

    boolean existsByWordIgnoreCase(String word);

    @EntityGraph(attributePaths = "definitions")
    Page<SlangWord> findByWordContainingIgnoreCase(String fragment, Pageable pageable);

    @EntityGraph(attributePaths = "definitions")
    @Query("""
            select distinct s from SlangWord s
            join s.definitions d
            where lower(d.text) like lower(concat('%', :fragment, '%'))
            """)
    Page<SlangWord> searchByDefinition(@Param("fragment") String fragment, Pageable pageable);

    @Query(value = "select * from slang_word order by random() limit 1", nativeQuery = true)
    Optional<SlangWord> findRandom();

    @Query(value = "select s.word from slang_word s where s.id <> :excludeId order by random() limit :count",
           nativeQuery = true)
    List<String> findRandomWordsExcluding(@Param("excludeId") Long excludeId, @Param("count") int count);
}
```

`UserRepository` adds `findByUsername` and `existsByUsername`. `SearchHistoryRepository` adds `findByUserIdOrderBySearchedAtDesc(Long, Pageable)` and `deleteByUserId(Long)`. `QuizResultRepository` adds `findByUserIdOrderByAnsweredAtDesc(Long, Pageable)`, `countByUserId(Long)`, `countByUserIdAndCorrectTrue(Long)`.

`JpaConfig` is a `@Configuration` carrying `@EnableJpaAuditing` — without it `@CreatedDate` stays null and the `not null` column rejects the insert.

- [ ] **Step 7: Run the test to verify it passes**

Expected: 2 tests pass.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/slangword/domain backend/src/main/java/com/slangword/repository \
        backend/src/main/java/com/slangword/config/JpaConfig.java backend/src/test
git commit -m "feat(backend): add JPA entities and repositories with integration tests"
```

---

## Task 3: Seed the dictionary from `slang.txt`

**Files:**
- Create: `service/SeedService.java`, `config/SeedProperties.java`
- Test: `src/test/java/com/slangword/service/SeedServiceTest.java`
- Create: `src/test/resources/data/slang-test.txt`

- [ ] **Step 1: Write the test fixture `src/test/resources/data/slang-test.txt`**

```
BBC`British Broadcasting Corporation
JCB`J C Bamford | excavator manufacturer
BBE`Babe
```

- [ ] **Step 2: Write the failing unit test**

The parser is the part worth testing in isolation: it must split on the backtick, split multi-sense definitions on ` | `, trim, and drop malformed lines.

```java
package com.slangword.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SeedServiceTest {

    @Test
    void parsesWordAndSingleDefinition() {
        List<SeedService.SeedEntry> entries = SeedService.parse(List.of("BBC`British Broadcasting Corporation"));

        assertThat(entries).singleElement()
                .satisfies(e -> {
                    assertThat(e.word()).isEqualTo("BBC");
                    assertThat(e.definitions()).containsExactly("British Broadcasting Corporation");
                });
    }

    @Test
    void splitsMultipleSensesOnPipe() {
        List<SeedService.SeedEntry> entries = SeedService.parse(List.of("JCB`J C Bamford | excavator manufacturer"));

        assertThat(entries).singleElement()
                .extracting(SeedService.SeedEntry::definitions)
                .isEqualTo(List.of("J C Bamford", "excavator manufacturer"));
    }

    @Test
    void skipsBlankAndMalformedLines() {
        assertThat(SeedService.parse(List.of("", "no-backtick-here", "A`", "`B"))).isEmpty();
    }

    @Test
    void keepsLastOccurrenceOfDuplicateWord() {
        List<SeedService.SeedEntry> entries = SeedService.parse(List.of("X`first", "X`second"));

        assertThat(entries).singleElement()
                .extracting(SeedService.SeedEntry::definitions)
                .isEqualTo(List.of("second"));
    }
}
```

- [ ] **Step 3: Run it and watch it fail**

Run: `mvnd -q test -Dtest=SeedServiceTest`
Expected: compilation failure — `SeedService` does not exist.

- [ ] **Step 4: Implement `SeedService`**

```java
package com.slangword.service;

import com.slangword.config.SeedProperties;
import com.slangword.domain.SlangWord;
import com.slangword.repository.SlangWordRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeedService {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);
    private static final String FIELD_SEPARATOR = "`";
    private static final String SENSE_SEPARATOR = " | ";
    private static final int BATCH_SIZE = 500;

    private final SlangWordRepository slangWordRepository;
    private final ResourceLoader resourceLoader;
    private final String seedLocation;

    public SeedService(SlangWordRepository slangWordRepository,
                       ResourceLoader resourceLoader,
                       SeedProperties properties) {
        this.slangWordRepository = slangWordRepository;
        this.resourceLoader = resourceLoader;
        this.seedLocation = properties.file();
    }

    public record SeedEntry(String word, List<String> definitions) { }

    static List<SeedEntry> parse(List<String> lines) {
        Map<String, List<String>> byWord = new LinkedHashMap<>();
        for (String line : lines) {
            int separator = line.indexOf(FIELD_SEPARATOR);
            if (separator <= 0 || separator == line.length() - 1) {
                continue;
            }
            String word = line.substring(0, separator).trim();
            String rawDefinition = line.substring(separator + 1).trim();
            if (word.isEmpty() || rawDefinition.isEmpty()) {
                continue;
            }
            List<String> senses = new ArrayList<>();
            for (String sense : rawDefinition.split(java.util.regex.Pattern.quote(SENSE_SEPARATOR))) {
                String trimmed = sense.trim();
                if (!trimmed.isEmpty()) {
                    senses.add(trimmed);
                }
            }
            if (!senses.isEmpty()) {
                byWord.put(word, senses);
            }
        }
        return byWord.entrySet().stream().map(e -> new SeedEntry(e.getKey(), e.getValue())).toList();
    }

    @Transactional
    public int seedIfEmpty() {
        if (slangWordRepository.count() > 0) {
            log.info("Dictionary already populated, skipping seed");
            return 0;
        }
        return load();
    }

    @Transactional
    public int reset() {
        slangWordRepository.deleteAllInBatch();
        return load();
    }

    private int load() {
        List<SeedEntry> entries = parse(readLines());
        List<SlangWord> batch = new ArrayList<>(BATCH_SIZE);
        int saved = 0;
        for (SeedEntry entry : entries) {
            SlangWord word = new SlangWord(entry.word());
            word.replaceDefinitions(entry.definitions());
            batch.add(word);
            if (batch.size() == BATCH_SIZE) {
                slangWordRepository.saveAll(batch);
                saved += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            slangWordRepository.saveAll(batch);
            saved += batch.size();
        }
        log.info("Seeded {} slang words from {}", saved, seedLocation);
        return saved;
    }

    private List<String> readLines() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resourceLoader.getResource(seedLocation).getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read seed file " + seedLocation, ex);
        }
    }
}
```

`SeedProperties` is `@ConfigurationProperties(prefix = "app.seed") public record SeedProperties(String file) { }`.

- [ ] **Step 5: Wire the startup hook**

```java
package com.slangword.config;

import com.slangword.service.SeedService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedRunner {

    @Bean
    ApplicationRunner seedDictionary(SeedService seedService) {
        return args -> seedService.seedIfEmpty();
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `mvnd -q test -Dtest=SeedServiceTest`
Expected: 4 tests pass.

- [ ] **Step 7: Verify the real file loads**

Run the app against the compose database and query it:
`curl -s localhost:8081/actuator/health` then check the log line.
Expected: `Seeded 7641 slang words from classpath:data/slang.txt`.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/slangword/service/SeedService.java \
        backend/src/main/java/com/slangword/config backend/src/test
git commit -m "feat(backend): seed dictionary from the original slang.txt"
```

---

## Task 4: Read side — get, search, random

**Files:**
- Create: `service/SlangWordService.java`, `dto/SlangWordDtos.java`, `dto/PageResponse.java`
- Create: `exception/NotFoundException.java`, `web/GlobalExceptionHandler.java`, `web/SlangWordController.java`
- Test: `src/test/java/com/slangword/service/SlangWordServiceTest.java`, `src/test/java/com/slangword/web/SlangWordControllerIT.java`

- [ ] **Step 1: Write the failing service unit test**

```java
package com.slangword.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.slangword.domain.SlangWord;
import com.slangword.exception.NotFoundException;
import com.slangword.repository.SlangWordRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class SlangWordServiceTest {

    @Mock SlangWordRepository repository;
    @InjectMocks SlangWordService service;

    private static SlangWord word(String text, String... definitions) {
        SlangWord w = new SlangWord(text);
        w.replaceDefinitions(List.of(definitions));
        return w;
    }

    @Test
    void getByWordReturnsMatch() {
        when(repository.findByWordIgnoreCase("bbc")).thenReturn(Optional.of(word("BBC", "British Broadcasting Corporation")));

        assertThat(service.getByWord("bbc").definitions()).containsExactly("British Broadcasting Corporation");
    }

    @Test
    void getByWordThrowsWhenAbsent() {
        when(repository.findByWordIgnoreCase("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByWord("nope"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void searchByDefinitionUsesDefinitionQuery() {
        when(repository.searchByDefinition(eq("babe"), any()))
                .thenReturn(new PageImpl<>(List.of(word("BBE", "Babe"))));

        var page = service.search("babe", SlangWordService.SearchField.DEFINITION, PageRequest.of(0, 10));

        assertThat(page.content()).singleElement().extracting("word").isEqualTo("BBE");
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void blankQueryListsEverything() {
        when(repository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(word("A", "a"))));

        assertThat(service.search("  ", SlangWordService.SearchField.WORD, PageRequest.of(0, 10)).content()).hasSize(1);
    }

    @Test
    void randomThrowsWhenDictionaryEmpty() {
        when(repository.findRandom()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.random()).isInstanceOf(NotFoundException.class);
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `mvnd -q test -Dtest=SlangWordServiceTest`
Expected: compilation failure — `SlangWordService` does not exist.

- [ ] **Step 3: Implement the read half of `SlangWordService`**

```java
@Service
@Transactional(readOnly = true)
public class SlangWordService {

    public enum SearchField { WORD, DEFINITION }

    private final SlangWordRepository repository;

    public SlangWordService(SlangWordRepository repository) {
        this.repository = repository;
    }

    public SlangWordResponse getByWord(String word) {
        return repository.findByWordIgnoreCase(word)
                .map(SlangWordResponse::from)
                .orElseThrow(() -> new NotFoundException("Slang word not found: " + word));
    }

    public PageResponse<SlangWordResponse> search(String query, SearchField field, Pageable pageable) {
        String fragment = query == null ? "" : query.trim();
        Page<SlangWord> page = fragment.isEmpty()
                ? repository.findAll(pageable)
                : switch (field) {
                    case WORD -> repository.findByWordContainingIgnoreCase(fragment, pageable);
                    case DEFINITION -> repository.searchByDefinition(fragment, pageable);
                };
        return PageResponse.of(page, SlangWordResponse::from);
    }

    public SlangWordResponse random() {
        return repository.findRandom()
                .map(SlangWordResponse::from)
                .orElseThrow(() -> new NotFoundException("Dictionary is empty"));
    }
}
```

`SlangWordResponse.from` maps entity → `record SlangWordResponse(Long id, String word, List<String> definitions, Instant createdAt, Instant updatedAt)`.

`PageResponse<T>` is a framework-free envelope so the JSON contract does not leak Spring's `Page` shape:

```java
public record PageResponse<T>(List<T> content, int page, int size,
                              long totalElements, int totalPages, boolean last) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isLast());
    }
}
```

- [ ] **Step 4: Write the controller**

```java
@RestController
@RequestMapping("/api/slang-words")
public class SlangWordController {

    private final SlangWordService service;

    public SlangWordController(SlangWordService service) { this.service = service; }

    @GetMapping
    public PageResponse<SlangWordResponse> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "word") String field,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SearchField searchField = "definition".equalsIgnoreCase(field) ? SearchField.DEFINITION : SearchField.WORD;
        return service.search(q, searchField, PageRequest.of(page, Math.min(size, 100)));
    }

    @GetMapping("/random")
    public SlangWordResponse random() { return service.random(); }

    @GetMapping("/{word}")
    public SlangWordResponse get(@PathVariable String word) { return service.getByWord(word); }
}
```

`/random` must be declared before `/{word}` so the literal path wins over the variable.

- [ ] **Step 5: Write `GlobalExceptionHandler`**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation failed");
        return problem;
    }
}
```

- [ ] **Step 6: Write the failing controller integration test**

```java
package com.slangword.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slangword.AbstractIntegrationTest;
import com.slangword.service.SeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SlangWordControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired SeedService seedService;

    @BeforeEach
    void seed() {
        seedService.reset();
    }

    @Test
    void searchesByWordFragment() throws Exception {
        mockMvc.perform(get("/api/slang-words").param("q", "BB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].word").value(org.hamcrest.Matchers.containsInAnyOrder("BBC", "BBE")));
    }

    @Test
    void searchesByDefinitionFragment() throws Exception {
        mockMvc.perform(get("/api/slang-words").param("q", "excavator").param("field", "definition"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].word").value("JCB"))
                .andExpect(jsonPath("$.content[0].definitions.length()").value(2));
    }

    @Test
    void returnsProblemDetailForMissingWord() throws Exception {
        mockMvc.perform(get("/api/slang-words/NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Slang word not found: NOPE"));
    }

    @Test
    void returnsRandomWord() throws Exception {
        mockMvc.perform(get("/api/slang-words/random"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.word").isNotEmpty());
    }
}
```

Because security is not configured yet, these run unauthenticated. Task 6 adds `permitAll` for `GET` so they keep passing.

- [ ] **Step 7: Run the whole suite to verify it passes**

Run the Testcontainers command from the header.
Expected: all tests pass.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/slangword/service/SlangWordService.java \
        backend/src/main/java/com/slangword/dto backend/src/main/java/com/slangword/web \
        backend/src/main/java/com/slangword/exception backend/src/test
git commit -m "feat(backend): add slang word search, lookup and random endpoints"
```

---

## Task 5: Write side — create, overwrite, update, delete

**Files:**
- Modify: `service/SlangWordService.java`, `web/SlangWordController.java`, `dto/SlangWordDtos.java`
- Create: `exception/ConflictException.java`
- Modify tests: `SlangWordServiceTest.java`, `SlangWordControllerIT.java`

- [ ] **Step 1: Write the failing unit tests**

These pin the original app's rule: adding a word that already exists is refused unless the caller explicitly asks to overwrite.

```java
    @Test
    void createRejectsDuplicateWithoutOverwrite() {
        when(repository.findByWordIgnoreCase("BBC")).thenReturn(Optional.of(word("BBC", "old")));

        assertThatThrownBy(() -> service.create(new UpsertSlangWordRequest("BBC", List.of("new")), false))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("BBC");
    }

    @Test
    void createOverwritesExistingWhenAsked() {
        SlangWord existing = word("BBC", "old");
        when(repository.findByWordIgnoreCase("BBC")).thenReturn(Optional.of(existing));
        when(repository.save(any(SlangWord.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.create(new UpsertSlangWordRequest("BBC", List.of("new")), true);

        assertThat(response.definitions()).containsExactly("new");
    }

    @Test
    void createSavesNewWord() {
        when(repository.findByWordIgnoreCase("ZZZ")).thenReturn(Optional.empty());
        when(repository.save(any(SlangWord.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.create(new UpsertSlangWordRequest("ZZZ", List.of("sleep")), false).word()).isEqualTo("ZZZ");
    }

    @Test
    void updateThrowsWhenWordMissing() {
        when(repository.findByWordIgnoreCase("GONE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("GONE", new UpdateSlangWordRequest(List.of("x"))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteThrowsWhenWordMissing() {
        when(repository.findByWordIgnoreCase("GONE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("GONE")).isInstanceOf(NotFoundException.class);
    }
```

- [ ] **Step 2: Run and watch them fail**

Run: `mvnd -q test -Dtest=SlangWordServiceTest`
Expected: compilation failure — `create`, `update`, `delete` do not exist.

- [ ] **Step 3: Implement the write methods**

```java
    @Transactional
    public SlangWordResponse create(UpsertSlangWordRequest request, boolean overwrite) {
        String word = request.word().trim();
        Optional<SlangWord> existing = repository.findByWordIgnoreCase(word);
        if (existing.isPresent() && !overwrite) {
            throw new ConflictException("Slang word already exists: " + word);
        }
        SlangWord entity = existing.orElseGet(() -> new SlangWord(word));
        entity.replaceDefinitions(request.definitions().stream().map(String::trim).toList());
        return SlangWordResponse.from(repository.save(entity));
    }

    @Transactional
    public SlangWordResponse update(String word, UpdateSlangWordRequest request) {
        SlangWord entity = repository.findByWordIgnoreCase(word)
                .orElseThrow(() -> new NotFoundException("Slang word not found: " + word));
        entity.replaceDefinitions(request.definitions().stream().map(String::trim).toList());
        return SlangWordResponse.from(repository.save(entity));
    }

    @Transactional
    public void delete(String word) {
        SlangWord entity = repository.findByWordIgnoreCase(word)
                .orElseThrow(() -> new NotFoundException("Slang word not found: " + word));
        repository.delete(entity);
    }
```

- [ ] **Step 4: Add the controller methods**

```java
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SlangWordResponse create(@Valid @RequestBody UpsertSlangWordRequest request,
                                    @RequestParam(defaultValue = "false") boolean overwrite) {
        return service.create(request, overwrite);
    }

    @PutMapping("/{word}")
    public SlangWordResponse update(@PathVariable String word, @Valid @RequestBody UpdateSlangWordRequest request) {
        return service.update(word, request);
    }

    @DeleteMapping("/{word}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String word) {
        service.delete(word);
    }
```

DTOs: `UpsertSlangWordRequest(@NotBlank @Size(max = 128) String word, @NotEmpty List<@NotBlank @Size(max = 2000) String> definitions)` and `UpdateSlangWordRequest(@NotEmpty List<@NotBlank @Size(max = 2000) String> definitions)`.

- [ ] **Step 5: Run the unit tests to verify they pass**

Run: `mvnd -q test -Dtest=SlangWordServiceTest`
Expected: 10 tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/slangword backend/src/test
git commit -m "feat(backend): add create, update and delete for slang words"
```

---

## Task 6: Authentication

**Files:**
- Create: `security/JwtService.java`, `JwtAuthFilter.java`, `AppUserDetailsService.java`, `AuthenticatedUser.java`, `CurrentUser.java`
- Create: `service/AuthService.java`, `dto/AuthDtos.java`, `web/AuthController.java`
- Create: `config/SecurityConfig.java`, `JwtProperties.java`, `CorsProperties.java`
- Test: `src/test/java/com/slangword/service/AuthServiceTest.java`, `src/test/java/com/slangword/web/AuthControllerIT.java`

- [ ] **Step 1: Write the failing `AuthService` unit test**

```java
package com.slangword.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.slangword.domain.Role;
import com.slangword.domain.User;
import com.slangword.dto.AuthDtos.LoginRequest;
import com.slangword.dto.AuthDtos.RegisterRequest;
import com.slangword.exception.ConflictException;
import com.slangword.repository.UserRepository;
import com.slangword.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @InjectMocks AuthService service;

    @Test
    void registerRejectsTakenUsername() {
        when(userRepository.existsByUsername("hieu")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("hieu", "secret123")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void registerHashesPasswordAndReturnsToken() {
        when(userRepository.existsByUsername("hieu")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateToken("hieu", "USER")).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        var response = service.register(new RegisterRequest("hieu", "secret123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void loginRejectsWrongPassword() {
        when(userRepository.findByUsername("hieu"))
                .thenReturn(java.util.Optional.of(new User("hieu", "hashed", Role.USER)));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("hieu", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginRejectsUnknownUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost", "whatever")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
```

Login must throw the same `BadCredentialsException` for an unknown user and a wrong password, so the API does not reveal which usernames exist.

- [ ] **Step 2: Run and watch it fail**

Run: `mvnd -q test -Dtest=AuthServiceTest`
Expected: compilation failure — `AuthService` does not exist.

- [ ] **Step 3: Implement `JwtService`**

```java
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = properties.expirationSeconds();
    }

    public long getExpirationSeconds() { return expirationSeconds; }

    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username).claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key).compact();
    }

    public String extractUsername(String token) { return parse(token).getSubject(); }

    public boolean isValid(String token) {
        try { parse(token); return true; } catch (Exception ex) { return false; }
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
```

The secret must be at least 32 bytes or `hmacShaKeyFor` throws at startup — the default in `application.yml` and the test profile both satisfy this.

- [ ] **Step 4: Implement `AuthService`**

```java
@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already taken: " + request.username());
        }
        User user = userRepository.save(
                new User(request.username(), passwordEncoder.encode(request.password()), Role.USER));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        String role = user.getRole().name();
        return new AuthResponse(jwtService.generateToken(user.getUsername(), role),
                user.getUsername(), role, jwtService.getExpirationSeconds());
    }
}
```

- [ ] **Step 5: Implement the security plumbing**

`AuthenticatedUser implements UserDetails` and carries `Long id` alongside username, password hash and `ROLE_<role>` authority. `AppUserDetailsService` loads a `User` by username and wraps it. `JwtAuthFilter extends OncePerRequestFilter`: if the `Authorization` header starts with `Bearer ` and the token validates, load the user and set a `UsernamePasswordAuthenticationToken` on the `SecurityContext`; otherwise continue the chain untouched, so anonymous requests still reach `permitAll` endpoints.

`CurrentUser` is a small static helper so services do not each re-read the context:

```java
package com.slangword.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() { }

    public static Optional<AuthenticatedUser> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public static AuthenticatedUser require() {
        return get().orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }
}
```

- [ ] **Step 6: Implement `SecurityConfig`**

```java
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, SeedProperties.class})
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) { this.jwtAuthFilter = jwtAuthFilter; }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsSource) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsSource))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/slang-words/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/quiz/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

- [ ] **Step 7: Write `AuthController`**

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) { return authService.register(request); }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) { return authService.login(request); }
}
```

- [ ] **Step 8: Write the failing auth integration test**

```java
package com.slangword.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slangword.AbstractIntegrationTest;
import com.slangword.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void clean() { userRepository.deleteAll(); }

    private String register(String username) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("username", username, "password", "secret123"));
        String json = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asText();
    }

    @Test
    void registersThenAuthorisesAProtectedCall() throws Exception {
        String token = register("hieu");

        mockMvc.perform(delete("/api/slang-words/NOPE").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsProtectedCallWithoutToken() throws Exception {
        mockMvc.perform(delete("/api/slang-words/NOPE")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        register("hieu");
        String body = objectMapper.writeValueAsString(java.util.Map.of("username", "hieu", "password", "secret123"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsShortPassword() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("username", "someone", "password", "123"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("password")));
    }
}
```

A 404 rather than 401 on the authenticated `DELETE` proves the token was accepted and the request reached the service.

- [ ] **Step 9: Run the full suite**

Run the Testcontainers command from the header.
Expected: all tests pass.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/slangword/security backend/src/main/java/com/slangword/config \
        backend/src/main/java/com/slangword/service/AuthService.java backend/src/main/java/com/slangword/dto/AuthDtos.java \
        backend/src/main/java/com/slangword/web/AuthController.java backend/src/test
git commit -m "feat(backend): add JWT authentication with register and login"
```

---

## Task 7: Search history

**Files:**
- Create: `service/HistoryService.java`, `web/HistoryController.java`, `dto/HistoryDtos.java`
- Modify: `web/SlangWordController.java` (record a search when authenticated)
- Test: `src/test/java/com/slangword/web/HistoryControllerIT.java`

- [ ] **Step 1: Write the failing integration test**

```java
package com.slangword.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slangword.AbstractIntegrationTest;
import com.slangword.repository.SearchHistoryRepository;
import com.slangword.repository.UserRepository;
import com.slangword.service.SeedService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class HistoryControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired SearchHistoryRepository historyRepository;
    @Autowired SeedService seedService;

    String token;

    @BeforeEach
    void setUp() throws Exception {
        historyRepository.deleteAll();
        userRepository.deleteAll();
        seedService.reset();
        String body = objectMapper.writeValueAsString(Map.of("username", "hieu", "password", "secret123"));
        String json = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(json).get("token").asText();
    }

    @Test
    void recordsSearchForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/slang-words").param("q", "BB").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/history").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].keyword").value("BB"))
                .andExpect(jsonPath("$.content[0].resultCount").value(2));
    }

    @Test
    void doesNotRecordSearchForAnonymousUser() throws Exception {
        mockMvc.perform(get("/api/slang-words").param("q", "BB")).andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(historyRepository.count()).isZero();
    }

    @Test
    void requiresAuthenticationToReadHistory() throws Exception {
        mockMvc.perform(get("/api/history")).andExpect(status().isUnauthorized());
    }

    @Test
    void ignoresBlankKeyword() throws Exception {
        mockMvc.perform(get("/api/slang-words").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(historyRepository.count()).isZero();
    }
}
```

- [ ] **Step 2: Run and watch it fail**

Expected: 404 on `/api/history` — the controller does not exist.

- [ ] **Step 3: Implement `HistoryService`**

```java
@Service
@Transactional
public class HistoryService {

    private final SearchHistoryRepository historyRepository;
    private final QuizResultRepository quizResultRepository;

    public HistoryService(SearchHistoryRepository historyRepository, QuizResultRepository quizResultRepository) {
        this.historyRepository = historyRepository;
        this.quizResultRepository = quizResultRepository;
    }

    /** No-op for anonymous callers and blank keywords. */
    public void recordSearch(String keyword, int resultCount) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        CurrentUser.get().ifPresent(user ->
                historyRepository.save(new SearchHistory(user.getId(), keyword.trim(), resultCount)));
    }

    @Transactional(readOnly = true)
    public PageResponse<SearchHistoryResponse> history(Long userId, Pageable pageable) {
        return PageResponse.of(historyRepository.findByUserIdOrderBySearchedAtDesc(userId, pageable),
                SearchHistoryResponse::from);
    }

    @Transactional(readOnly = true)
    public QuizStatsResponse quizStats(Long userId) {
        long total = quizResultRepository.countByUserId(userId);
        long correct = quizResultRepository.countByUserIdAndCorrectTrue(userId);
        return new QuizStatsResponse(total, correct, total == 0 ? 0d : (double) correct / total);
    }

    public void clearHistory(Long userId) {
        historyRepository.deleteByUserId(userId);
    }
}
```

- [ ] **Step 4: Hook recording into the search endpoint**

```java
    @GetMapping
    public PageResponse<SlangWordResponse> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "word") String field,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SearchField searchField = "definition".equalsIgnoreCase(field) ? SearchField.DEFINITION : SearchField.WORD;
        PageResponse<SlangWordResponse> result = service.search(q, searchField, PageRequest.of(page, Math.min(size, 100)));
        historyService.recordSearch(q, (int) result.totalElements());
        return result;
    }
```

- [ ] **Step 5: Write `HistoryController`**

```java
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) { this.historyService = historyService; }

    @GetMapping
    public PageResponse<SearchHistoryResponse> history(@RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return historyService.history(CurrentUser.require().getId(), PageRequest.of(page, Math.min(size, 100)));
    }

    @GetMapping("/quiz-stats")
    public QuizStatsResponse quizStats() {
        return historyService.quizStats(CurrentUser.require().getId());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear() {
        historyService.clearHistory(CurrentUser.require().getId());
    }
}
```

- [ ] **Step 6: Run the suite to verify it passes**

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/slangword/service/HistoryService.java \
        backend/src/main/java/com/slangword/web backend/src/main/java/com/slangword/dto/HistoryDtos.java backend/src/test
git commit -m "feat(backend): record and expose per-user search history"
```

---

## Task 8: Quiz

**Files:**
- Create: `service/QuizService.java`, `web/QuizController.java`, `dto/QuizDtos.java`
- Test: `src/test/java/com/slangword/service/QuizServiceTest.java`, `src/test/java/com/slangword/web/QuizControllerIT.java`

- [ ] **Step 1: Write the failing unit test**

The rules worth pinning: exactly four distinct options, the correct answer is always among them, and grading is exact-match.

```java
package com.slangword.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.slangword.domain.QuizMode;
import com.slangword.domain.QuizResult;
import com.slangword.domain.SlangWord;
import com.slangword.dto.QuizDtos.QuizAnswerRequest;
import com.slangword.exception.NotFoundException;
import com.slangword.repository.QuizResultRepository;
import com.slangword.repository.SlangWordRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock SlangWordRepository slangWordRepository;
    @Mock QuizResultRepository quizResultRepository;
    @InjectMocks QuizService service;

    private static SlangWord word(String text, String definition) {
        SlangWord w = new SlangWord(text);
        w.replaceDefinitions(List.of(definition));
        return w;
    }

    @Test
    void buildsFourDistinctOptionsIncludingTheAnswer() {
        when(slangWordRepository.findRandom()).thenReturn(Optional.of(word("BBC", "British Broadcasting Corporation")));
        when(slangWordRepository.findRandomWordsExcluding(any(), anyInt())).thenReturn(List.of("AAA", "BBB", "CCC"));

        var question = service.nextQuestion(QuizMode.WORD_FROM_DEFINITION);

        assertThat(question.prompt()).isEqualTo("British Broadcasting Corporation");
        assertThat(question.options()).hasSize(4).doesNotHaveDuplicates().contains("BBC");
    }

    @Test
    void definitionModePromptsWithTheWord() {
        when(slangWordRepository.findRandom()).thenReturn(Optional.of(word("BBC", "British Broadcasting Corporation")));
        when(slangWordRepository.findRandomWordsExcluding(any(), anyInt())).thenReturn(List.of("AAA", "BBB", "CCC"));
        when(slangWordRepository.findByWordIgnoreCase("AAA")).thenReturn(Optional.of(word("AAA", "alpha")));
        when(slangWordRepository.findByWordIgnoreCase("BBB")).thenReturn(Optional.of(word("BBB", "bravo")));
        when(slangWordRepository.findByWordIgnoreCase("CCC")).thenReturn(Optional.of(word("CCC", "charlie")));

        var question = service.nextQuestion(QuizMode.DEFINITION_FROM_WORD);

        assertThat(question.prompt()).isEqualTo("BBC");
        assertThat(question.options()).contains("British Broadcasting Corporation");
    }

    @Test
    void throwsWhenDictionaryEmpty() {
        when(slangWordRepository.findRandom()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.nextQuestion(QuizMode.WORD_FROM_DEFINITION))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void gradesCorrectAnswerAndPersistsIt() {
        when(quizResultRepository.save(any(QuizResult.class))).thenAnswer(i -> i.getArgument(0));
        when(quizResultRepository.countByUserId(7L)).thenReturn(1L);
        when(quizResultRepository.countByUserIdAndCorrectTrue(7L)).thenReturn(1L);

        var result = service.grade(7L, new QuizAnswerRequest(
                QuizMode.WORD_FROM_DEFINITION, "British Broadcasting Corporation", "BBC", "BBC"));

        assertThat(result.correct()).isTrue();
        assertThat(result.totalCorrect()).isEqualTo(1);
    }

    @Test
    void gradesWrongAnswer() {
        when(quizResultRepository.save(any(QuizResult.class))).thenAnswer(i -> i.getArgument(0));
        when(quizResultRepository.countByUserId(anyLong())).thenReturn(1L);
        when(quizResultRepository.countByUserIdAndCorrectTrue(anyLong())).thenReturn(0L);

        var result = service.grade(7L, new QuizAnswerRequest(
                QuizMode.WORD_FROM_DEFINITION, "British Broadcasting Corporation", "BBC", "AAA"));

        assertThat(result.correct()).isFalse();
        assertThat(result.correctAnswer()).isEqualTo("BBC");
    }
}
```

- [ ] **Step 2: Run and watch it fail**

Run: `mvnd -q test -Dtest=QuizServiceTest`
Expected: compilation failure — `QuizService` does not exist.

- [ ] **Step 3: Implement `QuizService`**

```java
@Service
@Transactional(readOnly = true)
public class QuizService {

    private static final int OPTION_COUNT = 4;

    private final SlangWordRepository slangWordRepository;
    private final QuizResultRepository quizResultRepository;

    public QuizService(SlangWordRepository slangWordRepository, QuizResultRepository quizResultRepository) {
        this.slangWordRepository = slangWordRepository;
        this.quizResultRepository = quizResultRepository;
    }

    public QuizQuestion nextQuestion(QuizMode mode) {
        SlangWord answer = slangWordRepository.findRandom()
                .orElseThrow(() -> new NotFoundException("Dictionary is empty"));
        String correctDefinition = answer.getDefinitions().isEmpty()
                ? "" : answer.getDefinitions().get(0).getText();
        List<String> distractorWords =
                slangWordRepository.findRandomWordsExcluding(answer.getId(), OPTION_COUNT - 1);

        String prompt;
        String correctOption;
        List<String> options = new ArrayList<>();
        if (mode == QuizMode.WORD_FROM_DEFINITION) {
            prompt = correctDefinition;
            correctOption = answer.getWord();
            options.addAll(distractorWords);
        } else {
            prompt = answer.getWord();
            correctOption = correctDefinition;
            distractorWords.stream()
                    .map(slangWordRepository::findByWordIgnoreCase)
                    .flatMap(Optional::stream)
                    .filter(w -> !w.getDefinitions().isEmpty())
                    .map(w -> w.getDefinitions().get(0).getText())
                    .forEach(options::add);
        }

        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        distinct.add(correctOption);
        distinct.addAll(options);
        List<String> finalOptions = new ArrayList<>(distinct);
        Collections.shuffle(finalOptions);
        return new QuizQuestion(mode, prompt, correctOption, finalOptions);
    }

    @Transactional
    public QuizAnswerResult grade(Long userId, QuizAnswerRequest request) {
        boolean correct = request.correctAnswer().equals(request.chosenAnswer());
        quizResultRepository.save(new QuizResult(userId, request.mode(), request.prompt(),
                request.correctAnswer(), request.chosenAnswer(), correct));
        return new QuizAnswerResult(correct, request.correctAnswer(),
                quizResultRepository.countByUserId(userId),
                quizResultRepository.countByUserIdAndCorrectTrue(userId));
    }
}
```

A `LinkedHashSet` before shuffling guarantees no duplicate option, which matters because two slang words can share a definition.

- [ ] **Step 4: Define the quiz DTOs**

```java
public record QuizQuestion(QuizMode mode, String prompt, String correctAnswer, List<String> options) { }

public record QuizAnswerRequest(@NotNull QuizMode mode, @NotBlank String prompt,
                                @NotBlank String correctAnswer, @NotBlank String chosenAnswer) { }

public record QuizAnswerResult(boolean correct, String correctAnswer, long totalAnswered, long totalCorrect) { }
```

The question carries `correctAnswer` and the client echoes it back when answering. This keeps the API stateless — no server-side question store — at the cost of a client that could cheat. That is an acceptable trade for a dictionary quiz; the honest score is the user's own.

- [ ] **Step 5: Write `QuizController`**

```java
@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) { this.quizService = quizService; }

    @GetMapping
    public QuizQuestion next(@RequestParam(defaultValue = "word-from-definition") String mode) {
        QuizMode quizMode = "definition-from-word".equalsIgnoreCase(mode)
                ? QuizMode.DEFINITION_FROM_WORD : QuizMode.WORD_FROM_DEFINITION;
        return quizService.nextQuestion(quizMode);
    }

    @PostMapping("/answer")
    public QuizAnswerResult answer(@Valid @RequestBody QuizAnswerRequest request) {
        return quizService.grade(CurrentUser.require().getId(), request);
    }
}
```

- [ ] **Step 6: Write the failing integration test**

```java
package com.slangword.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slangword.AbstractIntegrationTest;
import com.slangword.repository.QuizResultRepository;
import com.slangword.repository.UserRepository;
import com.slangword.service.SeedService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class QuizControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired QuizResultRepository quizResultRepository;
    @Autowired SeedService seedService;

    String token;

    @BeforeEach
    void setUp() throws Exception {
        quizResultRepository.deleteAll();
        userRepository.deleteAll();
        seedService.reset();
        String body = objectMapper.writeValueAsString(Map.of("username", "quizzer", "password", "secret123"));
        String json = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(json).get("token").asText();
    }

    @Test
    void servesAQuestionAnonymously() throws Exception {
        mockMvc.perform(get("/api/quiz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prompt").isNotEmpty())
                .andExpect(jsonPath("$.options").isArray());
    }

    @Test
    void gradesAndStoresAnAnswer() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "mode", "WORD_FROM_DEFINITION", "prompt", "Babe",
                "correctAnswer", "BBE", "chosenAnswer", "BBE"));

        mockMvc.perform(post("/api/quiz/answer").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.totalCorrect").value(1));
    }

    @Test
    void requiresAuthenticationToAnswer() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "mode", "WORD_FROM_DEFINITION", "prompt", "Babe",
                "correctAnswer", "BBE", "chosenAnswer", "BBE"));

        mockMvc.perform(post("/api/quiz/answer").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }
}
```

The seed fixture has only three words, so a question may carry fewer than four options — the integration test asserts the array exists rather than its size; the unit test covers the four-option rule with mocks.

- [ ] **Step 7: Run the suite**

Expected: all tests pass.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/slangword/service/QuizService.java \
        backend/src/main/java/com/slangword/web/QuizController.java \
        backend/src/main/java/com/slangword/dto/QuizDtos.java backend/src/test
git commit -m "feat(backend): add two-way quiz with graded, persisted answers"
```

---

## Task 9: Admin reset, OpenAPI, backend image

**Files:**
- Create: `web/AdminController.java`, `config/OpenApiConfig.java`, `backend/Dockerfile`, `backend/.dockerignore`
- Create: `src/main/resources/application-docker.yml`
- Test: `src/test/java/com/slangword/web/AdminControllerIT.java`

- [ ] **Step 1: Write the failing test**

```java
    @Test
    void rejectsResetForNonAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/reset").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsResetForAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/reset").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seeded").value(3));
    }
```

The admin fixture is created directly through the repository, since registration only ever mints `USER`:

```java
    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        userRepository.save(new User("root", passwordEncoder.encode("secret123"), Role.ADMIN));
        adminToken = jwtService.generateToken("root", "ADMIN");
        userToken = register("plain");
    }
```

- [ ] **Step 2: Run and watch it fail**

Expected: 404 — `/api/admin/reset` does not exist.

- [ ] **Step 3: Implement `AdminController`**

```java
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SeedService seedService;

    public AdminController(SeedService seedService) { this.seedService = seedService; }

    @PostMapping("/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Integer> reset() {
        return Map.of("seeded", seedService.reset());
    }
}
```

- [ ] **Step 4: Add `OpenApiConfig`**

```java
@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    @Bean
    OpenAPI slangWordOpenApi() {
        return new OpenAPI()
                .info(new Info().title("SlangWord API").version("1.0.0")
                        .description("Slang dictionary REST API — search, CRUD, quiz and per-user history."))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
```

- [ ] **Step 5: Write `backend/Dockerfile`**

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/slangword-api.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

`backend/.dockerignore` holds `target/`, `.mvn/`, `*.iml`.

- [ ] **Step 6: Write `application-docker.yml`**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db:5432/slangword
app:
  cors:
    allowed-origins: http://localhost:8080
```

- [ ] **Step 7: Run the suite and build the image**

Run the Testcontainers command, then `docker build -t slangword-api ./backend`.
Expected: tests pass; image builds.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/slangword/web/AdminController.java \
        backend/src/main/java/com/slangword/config/OpenApiConfig.java \
        backend/Dockerfile backend/.dockerignore backend/src/main/resources/application-docker.yml backend/src/test
git commit -m "feat(backend): add admin reset, OpenAPI docs and container image"
```

---

## Task 10: Frontend scaffold, API layer, auth context

**Files:**
- Create: `frontend/package.json`, `vite.config.ts`, `tsconfig.json`, `tailwind.config.js`, `postcss.config.js`, `index.html`, `.eslintrc.cjs`
- Create: `src/main.tsx`, `src/App.tsx`, `src/index.css`, `src/types.ts`
- Create: `src/api/client.ts`, `src/api/slangWords.ts`, `src/api/auth.ts`, `src/api/quiz.ts`, `src/api/history.ts`
- Create: `src/auth/AuthContext.tsx`, `src/auth/RequireAuth.tsx`
- Create: `src/components/Layout.tsx`

- [ ] **Step 1: Scaffold the project**

Run:
```bash
cd /d/SlangWord && npm create vite@latest frontend -- --template react-ts
cd frontend && npm install
npm install axios react-router-dom @tanstack/react-query
npm install -D tailwindcss@3 postcss autoprefixer vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom
npx tailwindcss init -p
```

- [ ] **Step 2: Write `src/types.ts`**

```ts
export interface SlangWord {
  id: number;
  word: string;
  definitions: string[];
  createdAt: string;
  updatedAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export type QuizMode = 'WORD_FROM_DEFINITION' | 'DEFINITION_FROM_WORD';

export interface QuizQuestion {
  mode: QuizMode;
  prompt: string;
  correctAnswer: string;
  options: string[];
}

export interface QuizAnswerResult {
  correct: boolean;
  correctAnswer: string;
  totalAnswered: number;
  totalCorrect: number;
}

export interface SearchHistoryEntry {
  id: number;
  keyword: string;
  resultCount: number;
  searchedAt: string;
}

export interface QuizStats {
  totalAnswered: number;
  totalCorrect: number;
  accuracy: number;
}

export interface AuthSession {
  token: string;
  username: string;
  role: string;
  expiresInSeconds: number;
}
```

- [ ] **Step 3: Write `src/api/client.ts`**

```ts
import axios from 'axios';

export const TOKEN_KEY = 'slangword.token';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let onUnauthorized: (() => void) | null = null;

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      onUnauthorized?.();
    }
    return Promise.reject(error);
  },
);

/** Pull the human-readable message out of an RFC 7807 ProblemDetail. */
export function problemMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.detail ?? error.message ?? fallback;
  }
  return fallback;
}
```

- [ ] **Step 4: Write the resource modules**

```ts
// src/api/slangWords.ts
import { api } from './client';
import type { Page, SlangWord } from '../types';

export type SearchField = 'word' | 'definition';

export async function searchWords(q: string, field: SearchField, page: number, size = 20) {
  const { data } = await api.get<Page<SlangWord>>('/slang-words', { params: { q, field, page, size } });
  return data;
}

export async function getWord(word: string) {
  const { data } = await api.get<SlangWord>(`/slang-words/${encodeURIComponent(word)}`);
  return data;
}

export async function getRandomWord() {
  const { data } = await api.get<SlangWord>('/slang-words/random');
  return data;
}

export async function createWord(word: string, definitions: string[], overwrite = false) {
  const { data } = await api.post<SlangWord>('/slang-words', { word, definitions }, { params: { overwrite } });
  return data;
}

export async function updateWord(word: string, definitions: string[]) {
  const { data } = await api.put<SlangWord>(`/slang-words/${encodeURIComponent(word)}`, { definitions });
  return data;
}

export async function deleteWord(word: string) {
  await api.delete(`/slang-words/${encodeURIComponent(word)}`);
}
```

`auth.ts` exposes `register(username, password)` and `login(username, password)` returning `AuthSession`. `quiz.ts` exposes `nextQuestion(mode)` and `submitAnswer(payload)`. `history.ts` exposes `getHistory(page)`, `getQuizStats()` and `clearHistory()`.

- [ ] **Step 5: Write `src/auth/AuthContext.tsx`**

```tsx
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { TOKEN_KEY, setUnauthorizedHandler } from '../api/client';
import * as authApi from '../api/auth';
import type { AuthSession } from '../types';

const USER_KEY = 'slangword.user';

interface AuthState {
  username: string | null;
  role: string | null;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<{ username: string; role: string } | null>(() => {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  });

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    setSession(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(logout);
  }, [logout]);

  const persist = (result: AuthSession) => {
    localStorage.setItem(TOKEN_KEY, result.token);
    const user = { username: result.username, role: result.role };
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    setSession(user);
  };

  const value = useMemo<AuthState>(() => ({
    username: session?.username ?? null,
    role: session?.role ?? null,
    isAuthenticated: session !== null,
    login: async (username, password) => persist(await authApi.login(username, password)),
    register: async (username, password) => persist(await authApi.register(username, password)),
    logout,
  }), [session, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
```

- [ ] **Step 6: Write `src/auth/RequireAuth.tsx`**

```tsx
import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from './AuthContext';

export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  return <>{children}</>;
}
```

- [ ] **Step 7: Configure Vite to proxy `/api` in dev**

```ts
// vite.config.ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { '/api': { target: 'http://localhost:8081', changeOrigin: true } },
  },
  test: { environment: 'jsdom', globals: true, setupFiles: './src/setupTests.ts' },
});
```

- [ ] **Step 8: Verify the dev server starts**

Run: `cd /d/SlangWord/frontend && npm run dev`
Expected: Vite serves on `http://localhost:5173` with no TypeScript errors.

- [ ] **Step 9: Commit**

```bash
git add frontend
git commit -m "feat(frontend): scaffold React app with API client and auth context"
```

---

## Task 11: Search, detail and word form pages

**Files:**
- Create: `src/pages/SearchPage.tsx`, `WordDetailPage.tsx`, `WordFormPage.tsx`
- Create: `src/components/SearchBar.tsx`, `WordCard.tsx`, `Pagination.tsx`
- Modify: `src/App.tsx` (routes)

- [ ] **Step 1: Write `SearchPage.tsx`**

```tsx
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { getRandomWord, searchWords } from '../api/slangWords';
import type { SearchField } from '../api/slangWords';
import { SearchBar } from '../components/SearchBar';
import { WordCard } from '../components/WordCard';
import { Pagination } from '../components/Pagination';

export function SearchPage() {
  const [query, setQuery] = useState('');
  const [field, setField] = useState<SearchField>('word');
  const [page, setPage] = useState(0);

  const results = useQuery({
    queryKey: ['slang-words', query, field, page],
    queryFn: () => searchWords(query, field, page),
  });

  const random = useQuery({ queryKey: ['random-word'], queryFn: getRandomWord, enabled: false });

  return (
    <div className="space-y-6">
      <SearchBar
        query={query}
        field={field}
        onQueryChange={(value) => { setQuery(value); setPage(0); }}
        onFieldChange={(value) => { setField(value); setPage(0); }}
      />

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={() => random.refetch()}
          className="rounded-md bg-slate-800 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
        >
          Random word
        </button>
        {random.data && (
          <Link to={`/words/${encodeURIComponent(random.data.word)}`} className="text-sm text-sky-600 underline">
            {random.data.word} — {random.data.definitions[0]}
          </Link>
        )}
      </div>

      {results.isLoading && <p className="text-slate-500">Loading…</p>}
      {results.isError && <p className="text-red-600">Could not load results.</p>}

      {results.data && (
        <>
          <p className="text-sm text-slate-500">{results.data.totalElements} result(s)</p>
          <ul className="space-y-3">
            {results.data.content.map((word) => <WordCard key={word.id} word={word} />)}
          </ul>
          <Pagination page={results.data.page} totalPages={results.data.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Write `SearchBar.tsx`, `WordCard.tsx`, `Pagination.tsx`**

`SearchBar` is a controlled input plus a `word`/`definition` radio pair, debounced by 300 ms before calling `onQueryChange`. `WordCard` renders a `<li>` with the word as a `Link` to `/words/:word` and its definitions as a list. `Pagination` renders previous/next buttons disabled at the bounds and a `Page X of Y` label.

- [ ] **Step 3: Write `WordDetailPage.tsx`**

Fetches with `useQuery(['slang-word', word], () => getWord(word))`. When `isAuthenticated`, shows Edit (navigates to `/words/:word/edit`) and Delete (a `useMutation` calling `deleteWord`, then `queryClient.invalidateQueries({ queryKey: ['slang-words'] })` and `navigate('/')`). A 404 renders "Word not found".

- [ ] **Step 4: Write `WordFormPage.tsx`**

One component serves create and edit, keyed on whether the route has a `:word` param. Local state holds the word and a `string[]` of definitions with add/remove row buttons. Submit calls `createWord` or `updateWord`. On a 409 from create, it shows an inline "Already exists — overwrite?" confirm that re-submits with `overwrite: true`, mirroring the original Swing dialog.

- [ ] **Step 5: Wire the routes in `App.tsx`**

```tsx
<Routes>
  <Route element={<Layout />}>
    <Route path="/" element={<SearchPage />} />
    <Route path="/words/new" element={<RequireAuth><WordFormPage /></RequireAuth>} />
    <Route path="/words/:word" element={<WordDetailPage />} />
    <Route path="/words/:word/edit" element={<RequireAuth><WordFormPage /></RequireAuth>} />
    <Route path="/quiz" element={<QuizPage />} />
    <Route path="/history" element={<RequireAuth><HistoryPage /></RequireAuth>} />
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<RegisterPage />} />
  </Route>
</Routes>
```

`/words/new` must precede `/words/:word` or the literal segment is swallowed by the parameter.

- [ ] **Step 6: Verify against the running API**

Run the backend, then `npm run dev`, open `http://localhost:5173`, search `BB`.
Expected: results render; clicking a word opens its detail page.

- [ ] **Step 7: Commit**

```bash
git add frontend/src
git commit -m "feat(frontend): add search, detail and word form pages"
```

---

## Task 12: Quiz, history and auth pages

**Files:**
- Create: `src/pages/QuizPage.tsx`, `HistoryPage.tsx`, `LoginPage.tsx`, `RegisterPage.tsx`
- Create: `src/components/QuizCard.tsx`, `StatCard.tsx`

- [ ] **Step 1: Write `QuizCard.tsx`**

```tsx
import type { QuizQuestion } from '../types';

interface Props {
  question: QuizQuestion;
  chosen: string | null;
  onChoose: (option: string) => void;
}

export function QuizCard({ question, chosen, onChoose }: Props) {
  const answered = chosen !== null;

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <p className="mb-1 text-xs uppercase tracking-wide text-slate-400">
        {question.mode === 'WORD_FROM_DEFINITION' ? 'Which slang word means…' : 'What does this slang word mean?'}
      </p>
      <p className="mb-5 text-lg font-medium text-slate-900">{question.prompt}</p>

      <ul className="space-y-2">
        {question.options.map((option) => {
          const isCorrect = option === question.correctAnswer;
          const isChosen = option === chosen;
          let style = 'border-slate-200 hover:border-sky-400';
          if (answered && isCorrect) style = 'border-emerald-500 bg-emerald-50';
          else if (answered && isChosen) style = 'border-red-500 bg-red-50';

          return (
            <li key={option}>
              <button
                type="button"
                disabled={answered}
                onClick={() => onChoose(option)}
                className={`w-full rounded-md border px-4 py-2 text-left text-sm ${style}`}
              >
                {option}
              </button>
            </li>
          );
        })}
      </ul>

      {answered && (
        <p className="mt-4 text-sm font-medium" role="status">
          {chosen === question.correctAnswer ? 'Correct' : `Wrong — the answer is ${question.correctAnswer}`}
        </p>
      )}
    </div>
  );
}
```

Rendering the verdict through `role="status"` gives the Vitest test in Task 13 a stable hook and helps screen readers.

- [ ] **Step 2: Write `QuizPage.tsx`**

Holds `mode` state and a mode toggle, `useQuery(['quiz', mode], () => nextQuestion(mode))`, `chosen` state, and a `useMutation` on `submitAnswer` fired on choose when authenticated (anonymous users still see the verdict locally but nothing is recorded). A "Next question" button clears `chosen` and refetches.

- [ ] **Step 3: Write `HistoryPage.tsx` and `StatCard.tsx`**

`HistoryPage` runs two queries — `getHistory(page)` and `getQuizStats()` — rendering three `StatCard`s (answered, correct, accuracy as a percentage) above a table of keyword / results / time. A "Clear history" button mutates `clearHistory` and invalidates the history query.

- [ ] **Step 4: Write `LoginPage.tsx` and `RegisterPage.tsx`**

Both hold `username`/`password` state, call `useAuth().login` or `.register` on submit, show `problemMessage(error)` inline on failure, and navigate to `location.state?.from ?? '/'` on success.

- [ ] **Step 5: Verify end to end**

Register a user in the UI, search a word, take a quiz question, open `/history`.
Expected: the search appears in history and the quiz answer moves the stats.

- [ ] **Step 6: Commit**

```bash
git add frontend/src
git commit -m "feat(frontend): add quiz, history and authentication pages"
```

---

## Task 13: Frontend tests, image and compose wiring

**Files:**
- Create: `src/setupTests.ts`, `src/components/__tests__/QuizCard.test.tsx`, `src/pages/__tests__/SearchPage.test.tsx`, `src/auth/__tests__/RequireAuth.test.tsx`
- Create: `frontend/Dockerfile`, `frontend/nginx.conf`, `frontend/.dockerignore`
- Modify: `docker-compose.yml`

- [ ] **Step 1: Write `src/setupTests.ts`**

```ts
import '@testing-library/jest-dom';
```

- [ ] **Step 2: Write the failing `QuizCard` test**

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { QuizCard } from '../QuizCard';
import type { QuizQuestion } from '../../types';

const question: QuizQuestion = {
  mode: 'WORD_FROM_DEFINITION',
  prompt: 'British Broadcasting Corporation',
  correctAnswer: 'BBC',
  options: ['AAA', 'BBC', 'CCC', 'DDD'],
};

describe('QuizCard', () => {
  it('renders the prompt and every option', () => {
    render(<QuizCard question={question} chosen={null} onChoose={vi.fn()} />);

    expect(screen.getByText('British Broadcasting Corporation')).toBeInTheDocument();
    expect(screen.getAllByRole('button')).toHaveLength(4);
  });

  it('reports the choice back to the parent', async () => {
    const onChoose = vi.fn();
    render(<QuizCard question={question} chosen={null} onChoose={onChoose} />);

    await userEvent.click(screen.getByRole('button', { name: 'BBC' }));

    expect(onChoose).toHaveBeenCalledWith('BBC');
  });

  it('shows the verdict once answered', () => {
    render(<QuizCard question={question} chosen="AAA" onChoose={vi.fn()} />);

    expect(screen.getByRole('status')).toHaveTextContent('Wrong — the answer is BBC');
  });
});
```

- [ ] **Step 3: Write the `SearchPage` and `RequireAuth` tests**

`SearchPage.test.tsx` mocks `../api/slangWords` with `vi.mock`, returns a one-item page, renders inside a `QueryClientProvider` and `MemoryRouter`, and asserts the word and the `1 result(s)` label appear. `RequireAuth.test.tsx` renders the guard inside a `MemoryRouter` with no token and asserts the protected text is absent and the login route rendered.

- [ ] **Step 4: Run the tests**

Run: `cd /d/SlangWord/frontend && npm test -- --run`
Expected: all tests pass.

- [ ] **Step 5: Write `frontend/nginx.conf`**

```nginx
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    location /api/ {
        proxy_pass http://api:8081/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

The SPA fallback in the last block is what keeps a hard refresh on `/words/BBC` from 404-ing.

- [ ] **Step 6: Write `frontend/Dockerfile`**

```dockerfile
FROM node:22-alpine AS build
WORKDIR /build
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

- [ ] **Step 7: Complete `docker-compose.yml`**

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: slangword
      POSTGRES_USER: ${POSTGRES_USER:?set it in .env}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?set it in .env}
    volumes: ["pgdata:/var/lib/postgresql/data"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U slangword -d slangword"]
      interval: 5s
      timeout: 5s
      retries: 10

  api:
    build: ./backend
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/slangword
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      APP_JWT_SECRET: ${APP_JWT_SECRET:?set it in .env}
    depends_on:
      db: { condition: service_healthy }
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8081/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 12

  web:
    build: ./frontend
    ports: ["8080:80"]
    depends_on:
      api: { condition: service_healthy }

volumes:
  pgdata:
```

- [ ] **Step 8: Verify the whole stack**

Run: `cd /d/SlangWord && docker compose up --build -d`, wait for health, then `curl -s localhost:8080/api/slang-words?q=BBC`.
Expected: JSON with `"word":"BBC"`, and `http://localhost:8080` serves the SPA.

- [ ] **Step 9: Commit**

```bash
git add frontend docker-compose.yml
git commit -m "feat: add frontend tests, nginx image and full compose stack"
```

---

## Task 14: CI and documentation

**Files:**
- Create: `.github/workflows/ci.yml`, `README.md`, `legacy-swing/README.md`
- Create: `backend/README.md`, `frontend/README.md`

- [ ] **Step 1: Write `.github/workflows/ci.yml`**

```yaml
name: CI

on:
  push:
    branches: [main, "feature/**"]
  pull_request:
    branches: [main]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: temurin
          cache: maven
      - name: Build and test
        working-directory: backend
        run: mvn -B verify

  frontend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - run: npm ci
      - run: npm run lint
      - run: npm test -- --run
      - run: npm run build

  images:
    runs-on: ubuntu-latest
    needs: [backend, frontend]
    steps:
      - uses: actions/checkout@v4
      - run: docker compose build
```

GitHub's `ubuntu-latest` runner has a Docker daemon, so the Testcontainers integration tests in the `backend` job work without extra setup.

- [ ] **Step 2: Write the root `README.md`**

Sections, in order: one-line description and badge; the evolution story (Swing coursework → fullstack platform) with a link to `legacy-swing/`; architecture diagram as a fenced `mermaid` block; tech stack table; quick start (`docker compose up --build`, then the three URLs); API table copied from the spec; local development without Docker; testing commands; project layout; and a link to `docs/superpowers/specs/` and `docs/superpowers/plans/`.

- [ ] **Step 3: Write `legacy-swing/README.md`**

Short: what the original app did, how to compile and run it (`javac SlangDictionaryApp.java && java SlangDictionaryApp`), its data format, and a one-paragraph "what changed and why" pointing at the new backend — the honest framing that makes the archive an asset rather than clutter.

- [ ] **Step 4: Verify the README renders and every command in it works**

Run each command block from a clean clone in a scratch directory.
Expected: `docker compose up --build` reaches a working `http://localhost:8080`.

- [ ] **Step 5: Commit**

```bash
git add .github README.md legacy-swing/README.md backend/README.md frontend/README.md
git commit -m "docs: add CI workflow and project documentation"
```

---

## Self-Review

**Spec coverage:** every spec section maps to a task — §4.2 data model → Tasks 1–2; §4.2 seeding → Task 3; §4.3 read endpoints → Task 4; write endpoints → Task 5; auth → Task 6; history → Task 7; quiz → Task 8; admin reset and OpenAPI → Task 9; §5 frontend → Tasks 10–12; §6 testing → Tasks 2–8 (backend) and 13 (frontend); §7 verification → Tasks 13–14.

**Naming consistency:** `SeedProperties` (not `AppProperties`) throughout; `SlangWordService.SearchField` is the enum used by both service and controller; `CurrentUser.require()` is the single accessor for the authenticated id; `PageResponse.of(page, mapper)` is the only pagination mapper.

**Known trade-off recorded in Task 8:** the quiz is stateless and trusts the client's echoed `correctAnswer`. Acceptable for this domain; noted so a reviewer sees it was a decision, not an oversight.

---

## Implementation notes — where the build diverged from this plan

Recorded after execution so the plan matches the repository.

| Planned | Actual | Why |
|---|---|---|
| Testcontainers 1.20.4 | **1.21.4** | 1.20.4 could not negotiate an API version with Docker Engine 29; every strategy failed with `Could not find a valid Docker environment`. 1.21.4 connects cleanly. |
| React 18, ESLint | **React 19, oxlint** | What `npm create vite@latest` scaffolds today. Both `npm run lint` and the CI job work unchanged. |
| Surefire runs everything | **failsafe added** | Surefire only picks up `*Test`; the `*IT` classes need `maven-failsafe-plugin`, so `mvn verify` is the command that runs integration tests. |
| — | **`ProblemDetailAuthenticationEntryPoint` added** | Spring Security's stateless default answers 403 to anonymous requests. Five integration tests failed on `expected:<401> but was:<403>`; a custom entry point plus access-denied handler restores REST semantics and renders both as RFC 7807. |
| — | **`@Qualifier("corsConfigurationSource")`** | Spring MVC's `HandlerMappingIntrospector` also implements `CorsConfigurationSource`, so injecting the filter chain's CORS source by type alone failed to start the context. |
| `WordFormPage` copies fetched data into state via `useEffect` | **split into `WordFormPage` loader + keyed `WordForm`** | The effect tripped `react(set-state-in-effect)`. Remounting the form via `key` gives it correct initial state with no effect at all. |
| `AuthContext.tsx` holds context and provider | **`AuthContext.ts` + `AuthProvider.tsx`** | Fast Refresh needs a module to export only components; the first split collided on Windows because the two names differed only in case. |
| `ports: "8080:80"` | **`"${WEB_PORT:-8080}:80"`** | 8080 was already bound on the development machine. |
| nginx proxies `/api` only | **also `/swagger-ui`, `/v3/api-docs`, `/actuator`** | Those paths live outside `/api`, so the SPA fallback was swallowing them and returning `index.html` with a 200. |

**Verified on completion:** `mvn verify` → 23 unit + 31 integration tests pass. `npm run lint` clean, `npm test -- --run` → 8 pass, `npm run build` succeeds. `docker compose up --build` serves the SPA, seeds 7,641 words, and answers the full authenticated flow (register → create → 409 on duplicate → overwrite → search → history → quiz → stats → 403 on admin reset → delete).

---

## Post-merge fix: secrets had defaults

GitGuardian raised four "Username Password" incidents against the first push. None was a live credential — they were a local Postgres container's password and a development JWT key — but the practice was wrong, and one case was a real vulnerability: `app.jwt.secret` carried a fallback that ships **inside the jar**, so any deployment forgetting to set `APP_JWT_SECRET` would sign tokens with a key published in this repository.

Fixed by removing every default:

- `spring.datasource.username`, `spring.datasource.password` and `app.jwt.secret` resolve from the environment with no fallback, so a missing value fails startup.
- `JwtProperties` is `@Validated` with `@NotBlank` and `@Size(min = 32)`, turning a missing or weak key into a startup error naming the variable and suggesting `openssl rand -base64 48`, instead of an opaque `WeakKeyException` on the first login.
- Compose reads credentials from a gitignored `.env`, with `.env.example` committed as the template. `${VAR:?message}` makes a missing variable a clear refusal rather than an empty string.
- `.gitguardian.yaml` scopes scanning past test fixtures.

**Verified:** `docker compose config` refuses to run without `.env`. The image exits at startup for a short secret (`Reason: set the APP_JWT_SECRET environment variable to a random string of at least 32 characters`) and for an unset one. The full stack then starts on freshly generated credentials, re-seeds 7,641 words, and passes the smoke flow. All 54 backend tests still pass.

**Operational note found while testing:** Postgres applies `POSTGRES_PASSWORD` only when initialising an empty data directory, so rotating the password needs `docker compose down -v`, not just `down`.

### Still open

The security pass surfaced items not yet addressed, listed here so they are not mistaken for oversights: no TLS, no rate limiting on `/api/auth/login`, no refresh token or revocation (a leaked JWT stays valid for 24 h), no API versioning, no coverage gate, and paginated search fetches its collection in memory (`HHH90003004`).
