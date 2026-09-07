# Spring Boot

Framework backend Java phổ biến nhất. Tài liệu này giải thích **cơ chế** — vì sao code chạy được khi bạn không thấy ai gọi nó.

> Trước khi đọc: nắm OOP và interface (`HOC_DSA_OOP.md` bên travel-ai-agent).

---

## 1. Vấn đề Spring sinh ra để giải

Không có framework, `SlangWordController` phải tự lo mọi thứ:

```java
public class SlangWordController {
    private final SlangWordService service;

    public SlangWordController() {
        DataSource ds = new HikariDataSource(/* url, user, password... */);
        EntityManagerFactory emf = /* dựng Hibernate */;
        SlangWordRepository repo = new SlangWordRepositoryImpl(emf);
        this.service = new SlangWordService(repo);   // ← chỉ dòng này là việc của tôi
    }
}
```

Ba vấn đề:
1. Controller phải **biết cách dựng** mọi thứ bên dưới nó.
2. Muốn test thì không thay repository giả vào được — nó tự `new` bên trong.
3. Đổi từ PostgreSQL sang thứ khác phải sửa mọi chỗ dựng `DataSource`.

## 2. Dependency Injection: lật ngược chiều

Thay vì tự tạo thứ mình cần, **khai báo mình cần gì** và để bên ngoài đưa vào.

[`SlangWordController`](../../backend/src/main/java/com/slangword/web/SlangWordController.java) thật:

```java
@RestController
@RequestMapping("/slang-words")
public class SlangWordController {

    private final SlangWordService service;
    private final HistoryService historyService;

    public SlangWordController(SlangWordService service, HistoryService historyService) {
        this.service = service;
        this.historyService = historyService;
    }
}
```

Không có `new`. Chỉ nói "tôi cần 2 thứ này". Spring nhìn constructor, tìm bean khớp kiểu, và truyền vào.

Gọi là **Inversion of Control** — quyền quyết định "dùng object nào" bị lật từ class sang container.

### Vì sao constructor injection chứ không `@Autowired` trên field?

```java
// KHÔNG NÊN
@Autowired
private SlangWordService service;

// NÊN (repo này dùng cách này ở mọi nơi)
public SlangWordController(SlangWordService service) { this.service = service; }
```

Ba lý do:

1. **`final` được.** Field injection không cho `final` → object có thể bị đổi ruột sau khi tạo.
2. **Test không cần Spring.** `new SlangWordController(mockService, mockHistory)` — xong. Field injection phải dùng reflection hoặc dựng context.
3. **Phụ thuộc quá nhiều thì thấy ngay.** Constructor 8 tham số nhìn là biết class làm quá nhiều việc. 8 field `@Autowired` thì trốn được.

## 3. Bean là gì

**Bean** = object do Spring tạo và quản lý vòng đời.

Các cách để Spring biết tạo bean:

| Cách | Ví dụ trong repo | Dùng khi |
|---|---|---|
| `@Service` | `SlangWordService` | Class nghiệp vụ của mình |
| `@RestController` | `SlangWordController` | Class xử lý HTTP |
| `@Component` | `RateLimitFilter` | Class dùng chung khác |
| `@Repository` | (Spring Data tự sinh) | Truy cập dữ liệu |
| `@Bean` trong `@Configuration` | `passwordEncoder()` | Object của **thư viện ngoài** |

Điểm mấu chốt ở dòng cuối: `BCryptPasswordEncoder` là class của Spring Security, bạn **không sửa được** để thêm annotation. Nên phải khai báo bằng method:

[`SecurityConfig`](../../backend/src/main/java/com/slangword/config/SecurityConfig.java):

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

### Mặc định là singleton

Mỗi bean chỉ có **một** instance dùng chung cho toàn ứng dụng. Hệ quả quan trọng: **bean phải thread-safe.**

Sai lầm điển hình:

```java
@Service
public class BadService {
    private String currentUser;   // ← NGUY HIỂM

    public void handle(String user) {
        this.currentUser = user;   // request khác ghi đè lên
    }
}
```

Có trạng thái thay đổi được ở field của singleton = hai request đồng thời giẫm lên nhau.

`RateLimitFilter` **có** trạng thái, nên dùng cấu trúc thread-safe:

```java
private final Map<String, Window> windows = new ConcurrentHashMap<>();
```

`ConcurrentHashMap` chứ không phải `HashMap`. `AtomicInteger` chứ không phải `int`.

## 4. Auto-configuration: vì sao chưa viết gì đã chạy

Bạn không viết code nào để kết nối DB, nhưng repository vẫn hoạt động.

`@SpringBootApplication` bật auto-configuration. Cơ chế: Spring Boot có sẵn hàng trăm lớp cấu hình, mỗi lớp gắn điều kiện:

```java
@ConditionalOnClass(DataSource.class)          // có driver JDBC trên classpath?
@ConditionalOnMissingBean(DataSource.class)    // mình chưa tự khai báo?
```

Thấy `postgresql` trong `pom.xml` → tự dựng `DataSource`. Thấy `spring-boot-starter-data-jpa` → tự dựng `EntityManagerFactory`. Thấy `flyway-core` → tự chạy migration.

**Nguyên tắc: mình khai báo thì mình thắng.** Vừa khai báo `SecurityFilterChain` là auto-config bảo mật mặc định (form login) tự tắt.

### Cách debug khi "sao nó không chạy"

```bash
mvn spring-boot:run -Ddebug
```

In ra bảng: cấu hình nào được áp, cái nào không, **và vì sao**. Đây chính là output đã giúp tìm ra bug bean nhập nhằng ở phần 10.2 của tài liệu kiến trúc.

## 5. Cấu hình

### Ba lớp, lớp sau đè lớp trước

```
application.yml  →  application-{profile}.yml  →  biến môi trường  →  tham số dòng lệnh
```

[`application.yml`](../../backend/src/main/resources/application.yml):

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/slangword}
    username: ${SPRING_DATASOURCE_USERNAME}
```

Cú pháp `${BIEN:mặc-định}`:
- `url` **có** mặc định → chạy local không cần set gì
- `username` **không có** → thiếu là chết lúc khởi động

Sự khác biệt đó là cố ý (xem bug 10.6). **Không bao giờ đặt mặc định cho secret.**

### Profile

`application-docker.yml` chỉ có phần khác biệt:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db:5432/slangword    # "db" là tên service trong compose
```

Bật bằng `SPRING_PROFILES_ACTIVE=docker`. Test dùng `@ActiveProfiles("test")` → nạp `application-test.yml`.

### `@ConfigurationProperties`: cấu hình có kiểu

Thay vì rải `@Value("${app.jwt.secret}")` khắp nơi:

[`JwtProperties`](../../backend/src/main/java/com/slangword/config/JwtProperties.java):

```java
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank(message = "set the APP_JWT_SECRET environment variable")
        @Size(min = 32, message = "...ít nhất 32 ký tự...")
        String secret,

        @Min(60) long expirationSeconds,
        @Min(300) long refreshExpirationSeconds) {
}
```

Bốn cái lợi:
1. **Gom nhóm** — mọi cấu hình JWT ở một chỗ.
2. **Có kiểu** — `long` chứ không phải String rồi tự parse.
3. **Validate lúc khởi động** — `@Validated` biến cấu hình sai thành lỗi start, không phải lỗi lúc chạy.
4. **Là `record`** → bất biến, an toàn với singleton.

`@Validated` chính là thứ biến "quên set biến môi trường" thành:

```
APPLICATION FAILED TO START
    Property: app.jwt.secret
    Reason: set the APP_JWT_SECRET environment variable to a random string of at least 32 characters
```

## 6. `@Transactional`

Đánh dấu một method là **một giao dịch DB**: hoặc mọi thứ trong đó thành công, hoặc không gì cả.

```java
@Service
@Transactional(readOnly = true)     // mặc định cho cả class
public class SlangWordService {

    @Transactional                   // ghi thì đè lại
    public SlangWordResponse create(UpsertSlangWordRequest request, boolean overwrite) { ... }
}
```

### Vì sao `readOnly = true` cho việc đọc?

Hibernate bỏ qua **dirty checking** — không cần chụp ảnh entity để so sánh lúc commit. Nhanh hơn và ít bộ nhớ hơn. Ngoài ra nó là lời khai báo ý định: method này không được sửa gì.

### Hai cái bẫy phải nhớ

**Bẫy 1: rollback chỉ với unchecked exception.**

Mặc định `@Transactional` rollback khi gặp `RuntimeException`, **không** rollback với checked exception. Đây là nguồn gốc bug 10.4 — exception dùng để từ chối request đã xoá luôn việc thu hồi token.

**Bẫy 2: gọi nội bộ không đi qua proxy.**

```java
@Service
public class Bad {
    public void a() {
        b();          // ← @Transactional của b() KHÔNG có tác dụng
    }

    @Transactional(propagation = REQUIRES_NEW)
    public void b() { }
}
```

Spring bọc bean trong một **proxy**. Gọi từ ngoài vào thì đi qua proxy → transaction advice chạy. Gọi `this.b()` thì đi thẳng, proxy không biết gì.

Đây là lý do [`RefreshTokenRevoker`](../../backend/src/main/java/com/slangword/service/RefreshTokenRevoker.java) phải là bean riêng:

```java
@Component
public class RefreshTokenRevoker {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeFamily(UUID familyId, Instant now) {
        return repository.revokeFamily(familyId, now);
    }
}
```

### Propagation hay dùng

| Giá trị | Nghĩa | Dùng khi |
|---|---|---|
| `REQUIRED` (mặc định) | Có sẵn thì tham gia, không thì tạo mới | Hầu hết trường hợp |
| `REQUIRES_NEW` | **Luôn** tạo giao dịch mới, treo cái cũ | Việc phải commit dù caller có rollback — audit log, thu hồi token |
| `MANDATORY` | Bắt buộc đã có sẵn, không thì ném lỗi | Method chỉ được gọi trong giao dịch |

## 7. Vòng đời một request

Đi từ trình duyệt tới DB và ngược lại:

```
1. nginx nhận, proxy /api/* sang :8081
2. Servlet container (Tomcat nhúng)
3. Filter chain:
     RateLimitFilter    ← chặn brute force TRƯỚC khi tốn công hash mật khẩu
     JwtAuthFilter      ← đọc Bearer token, đặt SecurityContext
     Spring Security     ← kiểm tra quyền theo URL
4. DispatcherServlet → tìm controller theo URL + method
5. WebMvcConfig thêm tiền tố /api/v1
6. @Valid chạy validation → sai thì ném MethodArgumentNotValidException
7. Controller → Service (mở transaction) → Repository → SQL
8. Trả DTO → Jackson serialize sang JSON
9. Có exception? @RestControllerAdvice bắt, đổi thành ProblemDetail
```

Thứ tự ở bước 3 là **quyết định có chủ đích**: rate limit đặt trước tất cả để một trận brute force bị chặn trước khi server phải làm việc nặng.

## 8. Nếu bị hỏi

**"DI là gì và vì sao cần?"**
→ Class khai báo thứ nó cần thay vì tự tạo. Lợi: test thay được mock, đổi implementation không sửa nơi dùng, và phụ thuộc lộ ra ở constructor nên thấy được class có đang làm quá nhiều việc không.

**"Vì sao constructor injection tốt hơn field injection?"**
→ `final` được, test không cần Spring, và constructor phình to là tín hiệu class làm quá nhiều việc.

**"Bean scope mặc định? Hệ quả?"**
→ Singleton. Nghĩa là bean phải thread-safe. Trong repo này `RateLimitFilter` có trạng thái nên dùng `ConcurrentHashMap` + `AtomicInteger`.

**"`@Transactional` gọi từ method khác cùng class có chạy không?"**
→ Không. Spring dùng proxy; gọi nội bộ đi thẳng, bỏ qua proxy. Tôi gặp đúng lỗi này khi làm thu hồi refresh token, phải tách ra bean riêng.

**"Auto-configuration hoạt động thế nào?"**
→ Cấu hình có điều kiện dựa trên classpath và bean đã có. `@ConditionalOnMissingBean` nghĩa là mình khai báo thì mình thắng.
