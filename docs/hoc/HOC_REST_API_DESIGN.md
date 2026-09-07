# Thiết kế REST API

Viết được endpoint là một chuyện. Thiết kế API để người khác dùng được, và để sửa được sau 2 năm, là chuyện khác.

> HTTP cơ bản (method, status code, header) đã có ở `HOC_FASTAPI_SSE.md` bên travel-ai-agent. Ở đây là phần **thiết kế**.

---

## 1. Tài nguyên, không phải hành động

URL nên đặt tên **sự vật**, method HTTP nói **hành động**.

```
❌ POST /api/getSlangWord
❌ POST /api/deleteWord?id=5

✅ GET    /api/v1/slang-words/BBC
✅ DELETE /api/v1/slang-words/BBC
```

Vì sao quan trọng: `GET` được cache, được retry an toàn, được bookmark. `POST /getSlangWord` mất hết những thứ đó — proxy không dám cache vì POST có thể có tác dụng phụ.

Bảng endpoint của repo:

```
GET    /api/v1/slang-words          tìm kiếm, có phân trang
GET    /api/v1/slang-words/{word}   một từ
GET    /api/v1/slang-words/random   một từ ngẫu nhiên
POST   /api/v1/slang-words          thêm mới
PUT    /api/v1/slang-words/{word}   thay thế nghĩa
DELETE /api/v1/slang-words/{word}   xoá
```

### Bẫy thứ tự route

```java
@GetMapping("/random")     // PHẢI đứng trước
public SlangWordResponse random() { ... }

@GetMapping("/{word}")     // nếu đứng trước sẽ nuốt luôn /random
public SlangWordResponse get(@PathVariable String word) { ... }
```

Đặt `/{word}` trước thì `/random` bị hiểu là "tìm từ tên là random" → 404. **Đường dẫn cố định luôn đứng trước biến.** Lỗi tương tự cũng có ở React Router (`/words/new` phải trước `/words/:word`).

## 2. Chọn đúng status code

| Code | Khi nào | Trong repo |
|---|---|---|
| `200 OK` | Thành công, có body | GET, PUT |
| `201 Created` | Tạo mới xong | `POST /slang-words` |
| `204 No Content` | Xong, không có gì trả về | `DELETE` |
| `400 Bad Request` | Body/tham số sai | Validation fail |
| `401 Unauthorized` | Chưa xác thực | Thiếu/sai token |
| `403 Forbidden` | Đã xác thực, không đủ quyền | USER gọi `/admin/reset` |
| `404 Not Found` | Không có tài nguyên đó | Từ không tồn tại |
| `409 Conflict` | Xung đột trạng thái | Thêm từ đã có |
| `429 Too Many Requests` | Vượt rate limit | Đăng nhập quá nhiều lần |

**Đừng trả 200 kèm `{"error": ...}`.** Client phải parse body mới biết thất bại; mọi công cụ giám sát sẽ đếm nó là thành công.

### 409 là ví dụ đáng nói

App Swing gốc, thêm từ trùng thì hiện hộp thoại "đã tồn tại, ghi đè?". Chuyển sang REST thế nào?

```java
@Transactional
public SlangWordResponse create(UpsertSlangWordRequest request, boolean overwrite) {
    Optional<SlangWord> existing = repository.findByWordIgnoreCase(word);
    if (existing.isPresent() && !overwrite) {
        throw new ConflictException("Slang word already exists: " + word);
    }
    ...
}
```

- `POST /slang-words` → **409** kèm thông báo rõ
- `POST /slang-words?overwrite=true` → **201**, ghi đè

Server không giữ trạng thái "đang chờ xác nhận". Client nhận 409, hỏi người dùng, gửi lại kèm cờ. **Stateless nhưng vẫn tái hiện được trải nghiệm của hộp thoại.**

## 3. Error contract: RFC 7807

Lỗi cũng là một phần API. Client phải parse được lỗi bằng code, không phải bằng cách đọc chuỗi.

RFC 7807 (`application/problem+json`) là chuẩn:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Slang word already exists: BBC"
}
```

Spring 6 có sẵn `ProblemDetail`. Xử lý tập trung ở [`GlobalExceptionHandler`](../../backend/src/main/java/com/slangword/web/GlobalExceptionHandler.java):

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
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

Vì sao tập trung một chỗ:
1. **Controller sạch** — không có `try/catch` lặp lại ở mọi method.
2. **Định dạng nhất quán** — client viết một hàm parse lỗi duy nhất.
3. **Không rò rỉ** — không có stack trace bay ra ngoài.

Service ném exception nghiệp vụ (`NotFoundException`, `ConflictException`), không biết gì về HTTP. Đúng nguyên tắc phân tầng.

Phía client, một hàm duy nhất ([`client.ts`](../../frontend/src/api/client.ts)):

```typescript
export function problemMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (axios.isAxiosError(error)) {
    const detail = (error.response?.data as { detail?: string } | undefined)?.detail
    return detail ?? error.message ?? fallback
  }
  return fallback
}
```

## 4. Versioning

Checklist của nhà tuyển dụng hỏi thẳng mục này.

**Vấn đề:** API công khai thì bạn không kiểm soát được client. Đổi định dạng response là làm hỏng ứng dụng của người khác.

**Giải pháp:** version trong đường dẫn. `/api/v1/...` chạy mãi mãi; thay đổi phá vỡ tương thích thì ra `/api/v2/...`.

### Cách làm quan trọng

Cách ngây thơ: sửa từng controller thành `@RequestMapping("/api/v1/slang-words")`. Nhược điểm: lặp lại 5 lần, ai đó quên là API không nhất quán.

Cách trong repo — [`WebMvcConfig`](../../backend/src/main/java/com/slangword/config/WebMvcConfig.java), khai báo **một chỗ**:

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    public static final String API_V1 = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_V1, HandlerTypePredicate.forBasePackage("com.slangword.web")
                .and(HandlerTypePredicate.forAnnotation(RestController.class)));
    }
}
```

Controller giữ nguyên `@RequestMapping("/slang-words")`.

Vì sao giới hạn theo **base package**: nếu áp cho mọi `@RestController` thì controller của springdoc (Swagger) cũng bị thêm tiền tố → Swagger UI hỏng. Giới hạn phạm vi để không đụng vào bean của thư viện.

**Khi cần v2:** tạo package `com.slangword.web.v2`, thêm predicate mới. Controller v1 không bị chạm.

### Các cách version khác

| Cách | Ví dụ | Đánh giá |
|---|---|---|
| Trong URL | `/api/v1/words` | Rõ ràng, cache tốt, dễ debug. **Chọn cái này** |
| Header | `Accept: application/vnd.app.v1+json` | "REST thuần" hơn nhưng khó test, khó cache |
| Query param | `/api/words?version=1` | Dễ quên, rối với cache |

## 5. Phân trang

Không bao giờ trả về danh sách không giới hạn. 7.641 từ trong một response là 2MB JSON, và sẽ là 200MB khi dữ liệu lớn lên.

```java
@GetMapping
public PageResponse<SlangWordResponse> search(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "word") String field,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    ...
    PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));   // chặn trên 100
}
```

`Math.min(size, 100)` là **bắt buộc**. Không có nó, ai đó gọi `?size=1000000` là làm sập server. Không tin tham số từ client.

### Vì sao có `PageResponse` riêng thay vì trả `Page` của Spring

```java
public record PageResponse<T>(List<T> content, int page, int size,
                              long totalElements, int totalPages, boolean last) {
```

`Page` của Spring serialize ra JSON có cả `pageable`, `sort`, `numberOfElements`... — chi tiết nội bộ của Spring Data. Trả nó ra ngoài nghĩa là **API của bạn bị trói vào phiên bản Spring Data**; nâng version có thể đổi JSON và làm hỏng client.

DTO riêng = hợp đồng do mình sở hữu.

## 6. DTO, không phải entity

**Không bao giờ trả entity JPA trực tiếp ra API.**

```java
public record SlangWordResponse(Long id, String word, List<String> definitions,
                                Instant createdAt, Instant updatedAt) {
    public static SlangWordResponse from(SlangWord entity) {
        return new SlangWordResponse(entity.getId(), entity.getWord(),
                entity.getDefinitions().stream().map(Definition::getText).toList(), ...);
    }
}
```

Bốn lý do:

1. **Rò rỉ dữ liệu.** `User` có `passwordHash`. Trả entity là gửi luôn hash mật khẩu ra ngoài.
2. **Vòng lặp vô hạn.** `SlangWord` → `definitions` → mỗi cái trỏ ngược `slangWord` → Jackson quay vòng tới `StackOverflowError`.
3. **Lazy loading nổ.** Jackson serialize sau khi transaction đóng → `LazyInitializationException`.
4. **Đổi DB là đổi API.** Đổi tên cột entity làm hỏng mọi client.

Request cũng cần DTO riêng, vì trường được phép gửi lên khác trường trả về:

```java
public record UpsertSlangWordRequest(
        @NotBlank @Size(max = 128) String word,
        @NotEmpty List<@NotBlank @Size(max = 2000) String> definitions) {
}
```

Không có `id`, không có `createdAt` — client không được đặt những thứ đó.

## 7. Validation ở biên

```java
@PostMapping
public SlangWordResponse create(@Valid @RequestBody UpsertSlangWordRequest request, ...)
```

`@Valid` chạy Bean Validation **trước khi** vào controller body. Sai thì ném `MethodArgumentNotValidException` → handler đổi thành 400 kèm chi tiết từng field.

**Nguyên tắc: validate càng sớm càng tốt.** Dữ liệu rác không được đi sâu vào hệ thống. Đến service thì `request` đã chắc chắn hợp lệ về mặt cấu trúc — service chỉ lo luật nghiệp vụ (từ này đã tồn tại chưa).

Chú ý cú pháp lồng: `List<@NotBlank String>` validate **từng phần tử**, `@NotEmpty List<...>` validate **cả list**. Hai thứ khác nhau.

## 8. Tài liệu API

```java
@Bean
OpenAPI slangWordOpenApi() {
    return new OpenAPI().info(new Info().title("SlangWord API").version("1.0.0")...)
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
}
```

springdoc đọc annotation và sinh OpenAPI spec + Swagger UI tại `/swagger-ui.html`.

Vì sao dùng cách sinh tự động thay vì viết tay: **tài liệu viết tay luôn lỗi thời.** Sinh từ code thì đổi code là đổi tài liệu.

## 9. Idempotency — khái niệm phải biết

Một thao tác **idempotent** là gọi nhiều lần cũng cho kết quả như gọi một lần.

| Method | Idempotent? | Vì sao quan trọng |
|---|---|---|
| `GET` | ✅ | Cache và retry an toàn |
| `PUT` | ✅ | Thay thế toàn bộ → gọi lại vẫn ra trạng thái đó |
| `DELETE` | ✅ | Xoá cái đã xoá → vẫn là "không tồn tại" |
| `POST` | ❌ | Gọi 2 lần tạo 2 bản ghi |

Vì sao cần biết: mạng lỗi thì client **retry**. Với `PUT`/`DELETE` thì retry an toàn. Với `POST` thì có thể tạo trùng.

Repo này chưa xử lý: nếu `POST /slang-words` timeout rồi client thử lại, có thể nhận 409 dù lần đầu đã thành công. Cách chuẩn là **Idempotency-Key header**. **Chưa làm** — ghi ra để biết là thiếu, không phải để giả vờ đủ.

## 10. Nếu bị hỏi

**"Thiết kế API cho chức năng X."**
→ Bắt đầu từ danh từ (tài nguyên), rồi method, rồi status code cho từng nhánh, rồi định dạng lỗi, rồi phân trang nếu là danh sách. Nói được vì sao chọn từng cái.

**"POST khác PUT thế nào?"**
→ POST tạo mới, không idempotent. PUT thay thế, idempotent. Quan trọng vì nó quyết định retry có an toàn không.

**"Version API thế nào?"**
→ Tiền tố URL, khai báo một chỗ bằng `PathMatchConfigurer` chứ không lặp ở từng controller. Giới hạn theo base package để không đụng vào endpoint của Swagger.

**"Vì sao không trả thẳng entity?"**
→ Rò field nhạy cảm, vòng lặp serialize, lỗi lazy loading, và trói API vào schema DB.

**"Trả lỗi kiểu gì?"**
→ RFC 7807 ProblemDetail, xử lý tập trung ở `@RestControllerAdvice`. Service ném exception nghiệp vụ, không biết gì về HTTP.
