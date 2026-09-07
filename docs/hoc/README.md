# Học Java Fullstack qua SlangWord

Bộ tài liệu này dạy **backend Java** qua đúng code trong repo này. Mỗi khái niệm đều chỉ tới một file thật, và giải thích **vì sao chọn cách đó** chứ không chỉ "làm thế nào".

## Đọc theo thứ tự nào

| # | Tài liệu | Nội dung |
|---|---|---|
| 0 | [LO_TRINH_JAVA_FULLSTACK.md](LO_TRINH_JAVA_FULLSTACK.md) | Lộ trình: bạn đang ở đâu, thiếu gì, học theo thứ tự nào |
| 1 | [KIEN_TRUC_VA_QUYET_DINH.md](KIEN_TRUC_VA_QUYET_DINH.md) | Kiến trúc SlangWord + **lý do** sau mỗi quyết định |
| 2 | [HOC_SPRING_BOOT.md](HOC_SPRING_BOOT.md) | DI/IoC, bean, auto-configuration, cấu hình, transaction |
| 3 | [HOC_JPA_HIBERNATE.md](HOC_JPA_HIBERNATE.md) | ORM, quan hệ, lazy/eager, N+1, 3 bug thật gặp trong dự án |
| 4 | [HOC_REST_API_DESIGN.md](HOC_REST_API_DESIGN.md) | Status code, versioning, error contract, phân trang |
| 5 | [HOC_SPRING_SECURITY.md](HOC_SPRING_SECURITY.md) | Filter chain, JWT, access + refresh token, rate limit |
| 6 | [HOC_TESTING_JAVA.md](HOC_TESTING_JAVA.md) | JUnit 5, Mockito, Testcontainers, coverage, tháp test |

## Đã có bên `travel-ai-agent` — KHÔNG đọc lại ở đây

Repo [travel-ai-agent](https://github.com/Hieuxuan1112/travel-ai-agent) đã có `docs/hoc/`. Những phần dưới đây **dùng chung**, không viết lại:

| Chủ đề | Đọc ở đâu | Ghi chú khi áp vào Java |
|---|---|---|
| Big-O, cấu trúc dữ liệu, OOP/SOLID | `HOC_DSA_OOP.md` | Nguyên lý y hệt. Java thêm: `interface` vs `abstract class`, generics, `equals`/`hashCode` |
| SQL: join, CTE, window function, index, transaction | `HOC_SQL.md` | **Nền tảng bắt buộc** trước khi đọc JPA. ORM chỉ sinh ra SQL — không hiểu SQL thì không debug được ORM |
| Docker: image, multi-stage, non-root, healthcheck, Compose | `HOC_DOCKER.md` | `Dockerfile` của SlangWord dùng đúng các kỹ thuật đó |
| CI/CD, quét container, deploy | `HOC_CICD_CLOUD.md` | SlangWord thêm: JaCoCo gate, ggshield, đếm số test |
| HTTP cơ bản, status code, validation | `HOC_FASTAPI_SSE.md` | Khái niệm HTTP giống nhau; phần *thiết kế* API nằm ở tài liệu 4 đây |
| Prometheus, metrics | `HOC_PROMETHEUS.md` | SlangWord **chưa có** — đây là việc nên làm tiếp |
| Vector DB, prompt engineering, toán AI | `HOC_VECTOR_DB.md`, `HOC_PROMPT_ENGINEERING.md`, `HOC_TOAN_AI.md` | Không liên quan tới JD Java |

**Cách dùng gọn nhất:** đọc `HOC_SQL.md` bên kia trước → rồi quay về đây đọc tài liệu 1 → 6.

## Nguyên tắc viết

1. **Mọi ví dụ là code thật trong repo**, có đường dẫn. Không có đoạn code bịa.
2. **Luôn có phần "vì sao"** — và cả "vì sao không chọn cách kia".
3. **Bug thật được giữ nguyên.** Dự án này gặp 6 bug trong lúc xây; chúng nằm trong tài liệu vì hiểu một bug thật đáng giá hơn đọc mười trang lý thuyết.
4. **Chỗ nào chưa làm thì nói rõ là chưa làm.** Không giả vờ hoàn hảo.

## Câu hỏi phỏng vấn

Mỗi tài liệu kết thúc bằng mục **"Nếu bị hỏi"** — các câu phỏng vấn thật về chủ đề đó, kèm câu trả lời neo vào code repo này. Đó là cách biến một dự án cá nhân thành thứ nói được trong 45 phút.
