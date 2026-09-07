# Lộ trình: từ hiện tại tới Java Fullstack Engineer

Tài liệu này trả lời thẳng: **bạn đang có gì, thiếu gì, học theo thứ tự nào.**

Viết dựa trên CV thật (`CV_NgoXuanHieu_JavaFullStackDeveloper.pdf`) và checklist thật của HR (vị trí *Fullstack Java, .NET, Vue.js*).

---

## 1. Sự thật cần đối diện trước

Checklist của HR có **5 tiêu chí lọc cứng**. Trượt một cái là bị loại trước khi ai đọc tới bảng điểm:

| # | Yêu cầu | Bạn | Có vá được bằng project không? |
|---|---|---|---|
| 1 | 3–5 năm fullstack thực chiến | ❌ ~5 tháng | **Không.** Thời gian không rút ngắn được |
| 2 | Thành thạo Vue.js | ❌ | Được, nhưng mất 3–4 tuần |
| 3 | Thành thạo Java Spring Boot | ⚠️ 1 project | Đang tiến bộ |
| 4 | .NET Core | ❌ | Được, nhưng mất 4–6 tuần |
| 5 | Tiếng Anh công việc | ✅ IELTS 6.5 | Đã đạt |

**Kết luận thẳng: JD cụ thể này chưa phù hợp.** SlangWord vá được đúng 1 trong 5. Điều đó không có nghĩa công sức bỏ phí — nó có giá trị lớn cho **JD Java fresher/junior** và cho phần Java trong hồ sơ AI Engineer của bạn.

### Mâu thuẫn trong CV cần sửa

CV đề *"Java Full-Stack Developer"* nhưng 90% nội dung là Python/AI: LangGraph, RAG, YOLO, ViT5, PaddleOCR. Java nằm **cuối** mục Languages. Recruiter phát hiện trong 10 giây.

Hai lựa chọn, chọn một và làm cho tới:
- **A.** Đổi tiêu đề thành *AI/Backend Engineer*, để SlangWord làm điểm cộng "biết cả Java/Spring". **Khuyến nghị** — bạn cạnh tranh được ngay.
- **B.** Giữ tiêu đề Java, nhưng phải xây thêm 1–2 project Java nữa và đẩy Java lên đầu mục Languages.

---

## 2. Bản đồ năng lực backend

Sáu tầng. Học từ dưới lên — bỏ tầng dưới thì tầng trên chỉ là học vẹt.

```
6. Vận hành      logging, metrics, tracing, alert
5. Hạ tầng       Docker, CI/CD, cloud, quét bảo mật
4. Chất lượng    test pyramid, coverage, code review
3. Framework     Spring Boot, DI, JPA, Security
2. Nền tảng web  HTTP, REST, xác thực, đồng thời
1. Nền móng      Java/OOP, cấu trúc dữ liệu, SQL, Git
```

### Bạn đang ở đâu

| Tầng | Trạng thái | Bằng chứng |
|---|---|---|
| 1. Nền móng | 🟡 Python vững, **Java còn mỏng** | CV liệt kê Java cuối cùng |
| 2. Nền tảng web | 🟢 Tốt | FastAPI, SSE, webhook, REST |
| 3. Framework | 🟡 Mới có SlangWord | Spring Boot, JPA, Security |
| 4. Chất lượng | 🟢 Mạnh | 71 test, 91.2% coverage, eval harness |
| 5. Hạ tầng | 🟢 Mạnh | Docker, GH Actions, Trivy, Azure, OIDC |
| 6. Vận hành | 🟡 Có ở project AI, **thiếu ở Java** | Prometheus/Grafana (travel-ai-agent), Jaeger tracing (eda-kafka-lab) |

**Điểm yếu rõ nhất: tầng 1 và 3 — chính là Java.** Tầng 4, 5 bạn đã mạnh hơn nhiều fresher.

---

## 3. Thứ tự học

### Giai đoạn 1 — Java thật sự (2–3 tuần)

Đây là lỗ hổng lớn nhất. Bạn viết được Java nhưng chưa **nghĩ** bằng Java.

| Chủ đề | Vì sao cần | Học ở đâu |
|---|---|---|
| Collections: `List`/`Set`/`Map`, khi nào dùng cái nào | Câu hỏi phỏng vấn kinh điển | `HOC_DSA_OOP.md` (travel-ai-agent) + tự viết bằng Java |
| `equals()` / `hashCode()` — hợp đồng giữa chúng | Sai là `HashMap` hỏng âm thầm | Effective Java, Item 10–11 |
| Generics, wildcard `? extends` / `? super` | Đọc được API Spring | `Type Parameter.txt` trong `legacy-swing/` |
| Stream API | Code Java hiện đại | Đọc `SlangWordService.fetchPage` trong repo |
| `Optional` — dùng đúng cách | Không phải để thay mọi null check | Repo dùng ở `findByWordIgnoreCase` |
| Exception: checked vs unchecked | Quyết định hành vi rollback của `@Transactional` | [HOC_SPRING_BOOT.md](HOC_SPRING_BOOT.md) §6 |
| `record` vs `class` | Repo dùng record cho mọi DTO | Xem `dto/` |

**Cách kiểm chứng đã học được:** viết lại `SlangWordService` từ đầu, không nhìn code cũ. Chạy được bộ test có sẵn là đạt.

### Giai đoạn 2 — Spring Boot (2 tuần)

Đọc theo thứ tự, mỗi tài liệu kèm mở code thật ra xem:

1. [HOC_SPRING_BOOT.md](HOC_SPRING_BOOT.md) — DI, bean, auto-config, `@Transactional`
2. [HOC_JPA_HIBERNATE.md](HOC_JPA_HIBERNATE.md) — ORM và chỗ nó rò rỉ
3. [HOC_REST_API_DESIGN.md](HOC_REST_API_DESIGN.md) — thiết kế API
4. [HOC_SPRING_SECURITY.md](HOC_SPRING_SECURITY.md) — xác thực

**Bắt buộc trước đó:** `HOC_SQL.md` bên travel-ai-agent. Không đọc được `EXPLAIN ANALYZE` thì không debug được ORM — đó là gốc của 3 trong 9 bug của dự án này.

**Kiểm chứng:** thêm một tính năng mới vào SlangWord có đủ migration + test. Ví dụ: cho user "yêu thích" một từ (`favourite`), có endpoint thêm/xoá/liệt kê, có phân trang, có test.

### Giai đoạn 3 — Lấp chỗ trống của dự án (1 tuần)

Những thứ SlangWord **cố tình chưa làm**, ghi rõ trong plan doc. Tự làm chúng là bài tập tốt nhất:

| Việc | Học được gì |
|---|---|
| Structured logging + correlation ID | Truy vết một request qua nhiều tầng |
| Actuator + Prometheus + Grafana | Tầng 6. Bạn đã làm ở project AI, port sang Java |
| Idempotency-Key cho `POST` | Xử lý retry đúng cách |
| Cache với Redis | Khi nào cache có ích, khi nào gây bug |
| Rate limit dùng Redis | Vì sao bộ đếm trong RAM sai khi có nhiều replica |
| Phát event khi thêm/sửa từ, một consumer đánh index | Nối kiến thức Kafka ở `eda-kafka-lab` vào một app Java thật |
| Soft delete + audit | Yêu cầu thật của domain tài chính |

### Giai đoạn 4 — Chỉ khi thật sự nhắm JD này (6–8 tuần)

| Việc | Thời gian | Ghi chú |
|---|---|---|
| Vue 3: Composition API, Pinia, Vue Router | 3–4 tuần | Viết lại frontend SlangWord bằng Vue là bài tập chuẩn |
| .NET Core: ASP.NET Web API, EF Core, DI | 4–6 tuần | Khái niệm giống Spring; cú pháp khác |
| Microservices thật | 4+ tuần | Tách SlangWord thành 2 service + message broker. Bạn đã có nền Kafka từ `eda-kafka-lab` — phần thiếu là ranh giới service và hợp đồng API, không phải messaging |

**Đánh giá thật: 3 việc này mất 3–4 tháng, và vẫn không tạo ra 3 năm kinh nghiệm.** Chỉ làm nếu bạn thật sự muốn theo hướng .NET + Vue lâu dài, không phải để chạy theo một JD.

---

## 4. Việc nên làm ngay tuần này

Xếp theo tỉ lệ giá trị / công sức:

1. **Sửa mâu thuẫn tiêu đề CV** (1 giờ) — chọn A hoặc B ở mục 1.
2. **Đọc [KIEN_TRUC_VA_QUYET_DINH.md](KIEN_TRUC_VA_QUYET_DINH.md)** (1 giờ) — bạn phải giải thích được từng quyết định trong repo của chính mình.
3. **Tự chạy `EXPLAIN ANALYZE`** trên DB đang chạy (30 phút) — thấy tận mắt Seq Scan vs Index Scan.
4. **Tập kể 9 bug** thành câu chuyện 2 phút mỗi cái (3 giờ) — đây là thứ tạo khác biệt trong phỏng vấn.
5. **Merge branch `feature/fullstack-platform` vào `main`** (15 phút) — người xem repo nhìn `main` đầu tiên. Hiện `main` vẫn là bản Swing cũ.

Việc số 5 quan trọng hơn vẻ ngoài của nó: hiện tại ai mở github.com/Hieuxuan1112/SlangWord vẫn thấy **1 file Java và không có README**.

---

## 5. Điền checklist của HR thế nào

Điểm số phải do **bạn** tự chấm — đây là bản tự đánh giá. Nhưng phần "Bằng chứng thực tế" thì có thể ghi cụ thể:

| Mục | Bằng chứng ghi được |
|---|---|
| **A1** Vue.js | *Chưa có. React/Next.js: website công ty BestHR 3 locale, SPA SlangWord* — ghi trung thực, đừng bỏ trống |
| **A2** Unit test FE | *Vitest + React Testing Library (SlangWord); Playwright E2E (BestHR)* |
| **B3** REST API + Swagger | *Spring Boot 3.5, `/api/v1` versioning, RFC 7807 ProblemDetail, springdoc OpenAPI. .NET: chưa có* |
| **B4** Microservices + RDB | *PostgreSQL: thiết kế schema, Flyway migration, index pg_trgm đo bằng EXPLAIN ANALYZE. Hệ phân tán: tự thiết kế EDA 6 service trên Kafka, có DLQ và tracing. Chưa vận hành microservices ở production* |
| **B5** Test + Quality Gate | ***91.2% instruction / 76.6% branch**, 31 unit + 40 integration (Testcontainers), JaCoCo gate fail dưới 85/70 trong GitHub Actions* |
| **C6** Docker + CI/CD | *Multi-stage, non-root, healthcheck, Compose; GH Actions 4 job; GHCR + Azure Container Apps qua OIDC keyless* |
| **C7** Secure coding | *BCrypt, JWT access + refresh có xoay vòng & phát hiện replay, rate limit, CSP/HSTS, secret không có default, ggshield + Trivy trong CI* |
| **C8** Message broker | *Apache Kafka 3.9.1 (KRaft): prototype EDA 6 consumer group, tự hiện thực DLQ, retry + backoff, at-least-once, tua offset đọc lại lịch sử, Jaeger tracing — repo `eda-kafka-lab`. Cộng webhook + DLQ ở BestHR* |
| **D9** Bảo hiểm/Ngân hàng | *Chưa có* |

**Nguyên tắc điền: ghi rõ cái chưa có.** Người phỏng vấn sẽ đào đúng vào ô bạn tự chấm cao. Chấm 3 mà không đỡ được câu hỏi thì tệ hơn chấm 1 và thành thật.

---

## 6. Sách và tài liệu đáng đọc

Ít mà kỹ, hơn nhiều mà hời hợt:

| Sách | Vì sao |
|---|---|
| **Effective Java** (Bloch) | Chuẩn mực Java. Đọc Item 10–11 (`equals`/`hashCode`), 15–18 (đóng gói, kế thừa) trước |
| **Spring in Action** | Bao quát Spring Boot; đọc song song với code repo này |
| **Designing Data-Intensive Applications** (Kleppmann) | Nặng nhưng là ranh giới giữa junior và senior. Đọc chương 3 (storage & index) sau khi hiểu phần pg_trgm |
| **Docs chính thức Spring** | Chất lượng cao bất thường. Đọc trước blog |

**Không nên:** cày tutorial YouTube. Bạn đã qua giai đoạn đó — giờ là lúc đọc code thật và tài liệu gốc.

---

## 7. Thước đo tiến bộ

Không phải "đã học xong bao nhiêu tutorial" mà là **giải thích được gì**:

- [ ] Giải thích được vì sao `@Transactional` không có tác dụng khi gọi nội bộ
- [ ] Đọc được `EXPLAIN ANALYZE` và nói được vì sao index không được dùng
- [ ] Giải thích được đánh đổi của JWT, và vì sao cần refresh token
- [ ] Nói được vì sao Testcontainers thay vì H2
- [ ] Thiết kế được API cho một tính năng mới: tài nguyên, method, status code, lỗi, phân trang
- [ ] Kể được một bug thật: triệu chứng → giả thuyết sai → vì sao sai → cách sửa đúng

**Sáu gạch đầu dòng này là 80% nội dung một buổi phỏng vấn backend.** Repo này có sẵn câu trả lời thật cho cả sáu — vì cả sáu đều đã thật sự xảy ra ở đây.
