# Kiến trúc SlangWord và lý do đằng sau mỗi quyết định

Tài liệu quan trọng nhất trong bộ này. Không phải "code chạy thế nào" mà là **"tại sao lại làm thế"** — thứ phân biệt người viết được code với người thiết kế được hệ thống.

---

## 1. Bức tranh tổng thể

```
Trình duyệt
    │  HTTPS
    ▼
nginx  ──── phục vụ file tĩnh của SPA
    │  ──── proxy /api/* sang backend
    ▼
Spring Boot (:8081)
    │  JDBC
    ▼
PostgreSQL 16
```

Ba tiến trình độc lập, chỉ nói chuyện qua HTTP/JSON và JDBC.

### Vì sao tách 3 tiến trình mà không gộp?

Java **có thể** phục vụ luôn file tĩnh (đặt vào `src/main/resources/static`). Nhưng tách ra được 3 thứ:

1. **Scale độc lập.** Traffic đọc file tĩnh và traffic gọi API rất khác nhau. Tách ra thì thêm 5 nginx mà vẫn 1 backend được.
2. **Deploy độc lập.** Sửa chữ trên giao diện không cần build lại jar 60MB và restart JVM.
3. **nginx làm việc nó giỏi.** Phục vụ file tĩnh, gzip, TLS — nginx nhanh hơn Tomcat rõ rệt vì nó sinh ra để làm đúng việc đó.

**Đánh đổi phải trả:** thêm một thành phần phải cấu hình và hiểu (xem bug nginx ở phần 7), và lúc dev phải chạy proxy (`vite.config.ts`).

---

## 2. Phân tầng backend

```
web/          ← Controller. Dịch HTTP ↔ lời gọi service. KHÔNG chứa nghiệp vụ.
service/      ← Toàn bộ luật nghiệp vụ. Test được không cần Spring.
repository/   ← Truy cập dữ liệu. Interface, Spring Data sinh implementation.
domain/       ← Entity JPA. Dữ liệu + bất biến của chính nó.
```

**Quy tắc sống còn: mũi tên phụ thuộc chỉ đi xuống.** `service` không được biết gì về `web`. Không import `HttpServletRequest`, không import `ResponseEntity`.

### Vì sao quy tắc đó đáng giá?

Nhìn [`SlangWordServiceTest.java`](../../backend/src/test/java/com/slangword/service/SlangWordServiceTest.java):

```java
@ExtendWith(MockitoExtension.class)
class SlangWordServiceTest {
    @Mock SlangWordRepository repository;
    @InjectMocks SlangWordService service;
```

Không có `@SpringBootTest`. Không khởi động Spring context. Không cần database. **13 test chạy trong 0.5 giây.**

Nếu nghiệp vụ nằm trong controller, muốn test luật "thêm từ trùng thì 409" bạn phải dựng cả web layer. Chậm hơn 100 lần và test cả những thứ không liên quan.

### Ranh giới đó bị phá ở đâu?

Một chỗ: [`HistoryService.recordSearch`](../../backend/src/main/java/com/slangword/service/HistoryService.java) gọi `CurrentUser.get()` — tức là đọc `SecurityContextHolder`, một thứ của tầng web.

```java
public void recordSearch(String keyword, int resultCount) {
    if (keyword == null || keyword.isBlank()) return;
    CurrentUser.get().ifPresent(user ->
            historyRepository.save(new SearchHistory(user.getId(), keyword.trim(), resultCount)));
}
```

**Cách đúng hơn:** controller lấy user id rồi truyền xuống service. Tôi để nguyên vì `SecurityContextHolder` dùng ThreadLocal nên vẫn test được, và đổi sẽ làm chữ ký hàm dài ra ở 4 chỗ. **Đây là đánh đổi có ý thức, không phải sơ suất** — và nếu bị hỏi trong phỏng vấn thì trả lời đúng như vậy.

---

## 3. Vì sao dữ liệu nằm ở DB chứ không phải file

App Swing gốc dùng `HashMap` trong RAM, mỗi lần sửa thì **ghi đè lại toàn bộ file** `slang.txt`:

```java
private void saveSlangDictionaryToFile(String filename) {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
        for (Map.Entry<String, String> entry : slangDictionary.entrySet()) { ... }
```

Cách này hỏng ở đâu:

| Vấn đề | Hậu quả |
|---|---|
| Hai người sửa cùng lúc | Người ghi sau xoá thay đổi của người trước |
| Mất điện lúc đang ghi | File cụt, mất toàn bộ 7.641 từ |
| Muốn tìm theo nghĩa | Duyệt tuần tự toàn bộ map |
| Muốn biết ai sửa gì | Không thể |

Database giải quyết cả bốn: transaction (ACID), index, và khoá.

### Vì sao PostgreSQL mà không phải MySQL/MongoDB?

- **Không phải MongoDB:** dữ liệu này *có quan hệ* — một từ có nhiều nghĩa, một user có nhiều lịch sử. Ép vào document sẽ phải nhân bản hoặc tự join ở tầng ứng dụng.
- **PostgreSQL thay vì MySQL:** cần `pg_trgm` cho tìm kiếm chuỗi con (phần 5). MySQL không có sẵn tương đương.

---

## 4. Vì sao một từ "sở hữu" danh sách nghĩa

Cách đơn giản nhất là một cột text. Nhưng dữ liệu gốc có dòng như:

```
JCB`J C Bamford | excavator manufacturer
```

Một từ, **hai nghĩa**. Nhét cả hai vào một cột nghĩa là mọi truy vấn sau này phải tự tách chuỗi.

Nên có bảng con:

```sql
slang_word(id, word unique, created_at, updated_at)
definition(id, slang_word_id → slang_word, text)
```

Trong Java, [`SlangWord`](../../backend/src/main/java/com/slangword/domain/SlangWord.java) là **aggregate root** — nó sở hữu các `Definition`:

```java
@OneToMany(mappedBy = "slangWord", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("id ASC")
private List<Definition> definitions = new ArrayList<>();

public void replaceDefinitions(List<String> texts) {
    definitions.clear();
    texts.forEach(text -> definitions.add(new Definition(this, text)));
}
```

Ba lựa chọn có chủ đích:

1. **`orphanRemoval = true`** — xoá phần tử khỏi list thì xoá luôn dòng DB. Không có nó, `definitions.clear()` chỉ set FK về null → vi phạm `not null` → lỗi.
2. **`replaceDefinitions` là đường sửa duy nhất.** Không có setter cho list. Bên ngoài không thể để entity ở trạng thái nửa vời.
3. **Constructor `protected SlangWord()`** — JPA bắt buộc phải có constructor không tham số, nhưng `protected` ngăn code ứng dụng tạo entity rỗng vô nghĩa.

---

## 5. Vì sao seed bằng code chứ không bằng migration

Flyway chỉ tạo **schema**. Dữ liệu 7.641 từ nạp bởi [`SeedService`](../../backend/src/main/java/com/slangword/service/SeedService.java) lúc khởi động, nếu bảng rỗng.

Vì sao không viết `V2__insert_data.sql` với 7.641 câu INSERT?

| | Migration SQL | SeedService |
|---|---|---|
| Kích thước file | ~500KB SQL sinh ra | Dùng lại chính `slang.txt` gốc |
| Đọc diff khi sửa | Không đọc nổi | Sửa 1 dòng text |
| Nút "Reset" | Phải viết lại logic | **Dùng lại đúng hàm đó** |

Điểm cuối là quyết định: app Swing gốc có nút Reset. Ở đây nó là `POST /api/v1/admin/reset`, gọi **cùng một** hàm `load()`. Một nguồn sự thật.

---

## 6. Vì sao tìm kiếm chia làm hai truy vấn

Đây là phần đáng nói nhất khi phỏng vấn.

### Cách viết đầu tiên (SAI)

```java
@EntityGraph(attributePaths = "definitions")
Page<SlangWord> findByWordContainingIgnoreCase(String fragment, Pageable pageable);
```

Trông hợp lý: lấy từ + nghĩa trong một lần, có phân trang. Log nói khác:

```
HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
```

**Chuyện gì xảy ra:** khi JOIN sang bảng con, một từ có 2 nghĩa thành 2 dòng SQL. Nếu Hibernate dùng `LIMIT 20` ở SQL, nó có thể cắt mất nghĩa thứ hai của từ cuối. Để không sai kết quả, Hibernate **nạp toàn bộ dòng khớp vào RAM rồi mới cắt trang bằng Java**.

Tìm `you` khớp 851 từ → nạp 851 entity để trả về 20. Với vài triệu bản ghi thì OOM.

### Cách sửa

[`SlangWordService.fetchPage`](../../backend/src/main/java/com/slangword/service/SlangWordService.java):

```java
// Truy vấn 1: phân trang trên id — không JOIN, nên LIMIT/OFFSET chạy ở DB
Page<Long> ids = repository.findIdsByWordFragment(fragment, pageable);

// Truy vấn 2: nạp đúng 20 dòng đó, kèm nghĩa
Map<Long, SlangWord> byId = repository.findByIdIn(ids.getContent()).stream()
        .collect(Collectors.toMap(SlangWord::getId, Function.identity()));

// findByIdIn không hứa thứ tự → phải khôi phục thứ tự của trang id
List<SlangWordResponse> content = ids.getContent().stream()
        .map(byId::get).filter(Objects::nonNull).map(SlangWordResponse::from).toList();
```

Luôn đúng 2 truy vấn, bất kể có bao nhiêu kết quả.

**Chi tiết dễ bỏ sót:** `findByIdIn` trả về theo thứ tự tuỳ ý của DB. Không khôi phục thứ tự thì kết quả nhảy lung tung giữa các lần tải. Có test riêng cho việc này: `searchKeepsTheOrderOfTheIdPage`.

### Vì sao không dùng `@BatchSize` hay `Set` thay `List`?

- `@BatchSize` giảm N+1 nhưng **không** giải quyết phân trang in-memory.
- Đổi `List` → `Set` cho phép Hibernate dùng `LIMIT` ở SQL, nhưng mất thứ tự nghĩa, và vẫn JOIN nên vẫn nhân dòng.

Hai truy vấn là cách duy nhất vừa đúng vừa scale.

---

## 7. Vì sao tìm chuỗi con cần index đặc biệt

`WHERE lower(word) LIKE '%bb%'` — B-tree **không giúp được gì**.

B-tree sắp xếp theo thứ tự từ điển, tìm được "bắt đầu bằng X". Nhưng `%bb%` không có tiền tố để nhảy tới. Kết quả: quét toàn bảng.

Đo thật, trước khi sửa:

```
Seq Scan on slang_word  (actual time=0.258..22.038 rows=57)
  Rows Removed by Filter: 7584
```

7.584 dòng bị đọc rồi vứt đi để lấy 57 dòng.

### pg_trgm

[`V2__search_indexes.sql`](../../backend/src/main/resources/db/migration/V2__search_indexes.sql):

```sql
create extension if not exists pg_trgm;
create index idx_slang_word_word_trgm on slang_word using gin (lower(word) gin_trgm_ops);
```

pg_trgm cắt mỗi giá trị thành **trigram** — cụm 3 ký tự. `"laugh"` → `lau`, `aug`, `ugh` (kèm padding đầu/cuối). Index GIN lưu: trigram nào xuất hiện ở dòng nào. Tìm `%laugh%` thành "tìm các dòng chứa đủ những trigram này".

Kết quả đo:

| Chuỗi tìm | Kế hoạch | Thời gian |
|---|---|---|
| `%bb%` (2 ký tự) | Seq Scan | 3.35 ms |
| `%bbc%` (3 ký tự) | **Bitmap Index Scan** | 0.199 ms |
| `%laugh%` (5 ký tự) | **Bitmap Index Scan** | 0.314 ms |

**Giới hạn phải biết: từ 3 ký tự trở lên index mới dùng được.** 2 ký tự không tạo nổi một trigram hoàn chỉnh. Đây là tính chất cố hữu, không phải cấu hình sai.

**Cái giá:** index GIN to hơn và ghi chậm hơn B-tree. Chấp nhận được vì từ điển đọc nhiều ghi ít. Nếu là bảng log ghi liên tục thì đánh đổi này sai.

---

## 8. Vì sao hai loại token thay vì một

Ban đầu chỉ có một JWT sống 24 giờ. Vấn đề: **JWT không thu hồi được.**

Server không lưu JWT. Nó chỉ kiểm tra chữ ký. Đó là ưu điểm — xác thực không cần chạm DB, nên scale ngang thoải mái. Nhưng cũng có nghĩa: token bị lộ thì **không có cách nào chặn** cho tới khi hết hạn.

### Thiết kế hiện tại

| | Access token | Refresh token |
|---|---|---|
| Dạng | JWT có chữ ký | 256 bit ngẫu nhiên |
| Sống | 15 phút | 7 ngày |
| Lưu ở server | Không | Có (dạng hash SHA-256) |
| Thu hồi được | Không | **Có** |
| Dùng khi nào | Mọi request | Chỉ khi đổi lấy access token mới |

Ý tưởng: thứ **dùng thường xuyên** thì stateless và sống ngắn; thứ **sống lâu** thì lưu lại để kiểm soát được.

### Vì sao chỉ lưu hash?

[`RefreshTokenService`](../../backend/src/main/java/com/slangword/service/RefreshTokenService.java):

```java
repository.save(new RefreshToken(userId, hash(token), familyId, expiresAt));
```

DB bị lộ thì kẻ tấn công có hash, không có token. Cùng lý do với việc không lưu mật khẩu dạng plaintext.

### Vì sao SHA-256 mà không BCrypt như mật khẩu?

Câu hỏi phỏng vấn hay. Lý do:

1. **Token đã có 256 bit ngẫu nhiên** — không có từ điển nào để dò. BCrypt chậm có ích khi mật khẩu yếu và đoán được; ở đây không có gì để đoán.
2. **BCrypt có salt riêng mỗi lần hash** → không tra được bằng index. Muốn tìm token phải duyệt hết mọi dòng và `matches()` từng cái.

Đúng thuật toán cho đúng bài toán, không phải "cái nào an toàn hơn".

### Xoay vòng + phát hiện tái sử dụng

Mỗi lần refresh: cấp token mới, đánh dấu token cũ đã dùng. Nếu ai đó trình lại token **đã dùng rồi**:

```java
if (!stored.isUsable(now)) {
    int revoked = revoker.revokeFamily(stored.getFamilyId(), now);
    log.warn("Refresh token reuse detected for user {}...");
    throw new BadCredentialsException("Invalid refresh token");
}
```

Vì sao thu hồi **cả họ** chứ không chỉ token đó? Vì có 2 khả năng: token bị đánh cắp và kẻ trộm đang dùng, hoặc client hợp lệ bị lệch. **Không phân biệt được ai là ai**, nên an toàn hơn là bắt cả hai đăng nhập lại.

`familyId` nối các token cùng một lần đăng nhập, nên đăng xuất một thiết bị không đá văng các thiết bị khác — có test `logoutLeavesOtherSessionsAlone`.

---

## 9. Vì sao rate limit chỉ ở endpoint đăng nhập

[`RateLimitFilter`](../../backend/src/main/java/com/slangword/security/RateLimitFilter.java) chỉ chặn `/api/v1/auth/**`.

Lý do: **login là thao tác ghi duy nhất không cần token**. Mọi endpoint ghi khác đã yêu cầu JWT hợp lệ. Nên nếu ai muốn đoán mật khẩu, đây là cửa duy nhất.

Thuật toán: **fixed window** — đếm request theo cửa sổ 1 phút cho mỗi địa chỉ.

**Nhược điểm đã biết và chấp nhận:** ở ranh giới hai cửa sổ có thể qua được 2× giới hạn (10 request cuối phút 1 + 10 đầu phút 2). Sliding window sửa được nhưng phức tạp hơn. Với mục đích chống dò mật khẩu, gấp đôi giới hạn vẫn chặn đứng brute force.

**Giới hạn thật sự:** bộ đếm nằm trong RAM của một instance. Chạy 3 replica thì mỗi cái đếm riêng → giới hạn thực tế gấp 3. Muốn đúng phải dùng Redis. Đã ghi rõ trong javadoc — **giới hạn được biết và ghi lại thì tốt hơn giới hạn bị giấu.**

---

## 10. Sáu bug thật gặp trong lúc xây

Phần giá trị nhất để kể trong phỏng vấn.

### 10.1. Spring Security trả 403 thay vì 401

5 test đỏ cùng lúc: `expected:<401> but was:<403>`.

Spring Security stateless mặc định dùng `Http403ForbiddenEntryPoint`. Nhưng chuẩn REST:
- **401** = chưa xác thực (bạn là ai?)
- **403** = đã xác thực nhưng không có quyền (biết bạn là ai, nhưng không cho)

Sửa bằng [`ProblemDetailAuthenticationEntryPoint`](../../backend/src/main/java/com/slangword/security/ProblemDetailAuthenticationEntryPoint.java) — implement cả `AuthenticationEntryPoint` (401) và `AccessDeniedHandler` (403).

### 10.2. Bean nhập nhằng làm app không khởi động

```
expected single matching bean but found 2: corsConfigurationSource, mvcHandlerMappingIntrospector
```

`HandlerMappingIntrospector` của Spring MVC **cũng** implement `CorsConfigurationSource`. Inject theo kiểu là nhập nhằng. Sửa bằng `@Qualifier("corsConfigurationSource")`.

**Bài học:** inject theo type chỉ an toàn khi chắc chắn chỉ có một bean cùng type — mà framework có thể tự thêm bean bạn không biết.

### 10.3. Index không khớp truy vấn

Tôi tạo index trên `lower(word)`. Nhưng Spring Data sinh SQL từ tên method `findByWordIgnoreCase` ra:

```sql
WHERE upper(word) = upper(?)
```

`upper` ≠ `lower` → **index chưa bao giờ được dùng**. Mọi lần tra từ đều quét 7.640 dòng.

Sửa: viết `@Query` tường minh:

```java
@Query("select s from SlangWord s where lower(s.word) = lower(:word)")
Optional<SlangWord> findByWordIgnoreCase(@Param("word") String word);
```

**Bài học lớn:** derived query (sinh từ tên method) tiện nhưng **bạn không thấy SQL nó tạo ra**. Ở chỗ nhạy cảm về hiệu năng, viết query ra tay.

### 10.4. Rollback xoá mất phản ứng an ninh

Phát hiện replay → thu hồi cả họ token → ném exception để từ chối. Test đỏ: token vẫn dùng được.

Vì `@Transactional` **rollback khi có RuntimeException**. Chính exception dùng để từ chối request đã xoá luôn việc thu hồi.

Thử `noRollbackFor` — **vẫn hỏng**, vì `AuthService.refresh` cũng `@Transactional` và giao dịch ngoài mới là chủ.

Sửa đúng: [`RefreshTokenRevoker`](../../backend/src/main/java/com/slangword/service/RefreshTokenRevoker.java) với `REQUIRES_NEW` — treo giao dịch ngoài, commit độc lập.

Phải là **bean riêng**: Spring dùng proxy, gọi method `REQUIRES_NEW` trên `this` sẽ đi thẳng, bỏ qua proxy, và âm thầm chạy trong giao dịch cũ.

### 10.5. nginx huỷ header kế thừa

Đặt 4 security header ở scope ngoài, thêm HSTS trong block TLS. Kết quả: **chỉ HSTS tồn tại.**

nginx không gộp `add_header`. Block nào khai báo dù chỉ một `add_header` thì **vứt toàn bộ** những cái lẽ ra được kế thừa.

Sửa: lặp lại đủ 5 header trong mỗi server block, kèm comment giải thích vì sao lặp.

### 10.6. Secret có giá trị mặc định

```yaml
secret: ${APP_JWT_SECRET:change-me-in-production-...}
```

Default này nằm **trong file jar**. Ai deploy quên set biến môi trường sẽ ký JWT bằng khoá công khai trên GitHub.

Sửa: bỏ hẳn default (`${APP_JWT_SECRET}`) + `@Validated` với `@Size(min = 32)`. Thiếu là **chết lúc khởi động**, nêu rõ tên biến.

**Nguyên tắc:** thà chết to và rõ còn hơn chạy âm thầm với cấu hình không an toàn.

---

## Nếu bị hỏi

**"Kể một bug khó em từng gặp."**
→ Bug 10.4. Nó chạm vào 3 thứ: ngữ nghĩa transaction, cơ chế proxy của Spring, và thiết kế bảo mật. Kể được: triệu chứng → giả thuyết đầu (sai) → vì sao sai → cách sửa đúng → vì sao phải tách bean.

**"Vì sao dùng JWT?"**
→ Đừng nói "vì nó phổ biến". Nói: stateless nên xác thực không chạm DB; cái giá là không thu hồi được; nên access token 15 phút, còn refresh token thì lưu và thu hồi được. Nêu được **đánh đổi** mới là câu trả lời đạt.

**"Làm sao tối ưu một API chậm?"**
→ Đo trước. `EXPLAIN ANALYZE` chỉ ra Seq Scan bỏ 7.584 dòng. Rồi mới sửa. Rồi đo lại: 22ms → 0.199ms. **Không đoán.**

**"Test coverage bao nhiêu?"**
→ 91.2% instruction, 76.6% branch, đo gộp unit + integration, build fail dưới 85/70. Giải thích được vì sao đo gộp: phần lớn hành vi ở đây chỉ chạy qua MockMvc.
