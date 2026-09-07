# CI/CD và bảo mật chuỗi cung ứng

> Khái niệm CI/CD chung, quét container, deploy cloud đã có ở `HOC_CICD_CLOUD.md` bên travel-ai-agent. **Đọc cái đó trước.** Tài liệu này là pipeline thật của SlangWord: từng job làm gì, vì sao thứ tự như vậy, và 4 lần pipeline nói dối.

---

## 1. Pipeline gồm gì

[`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) — 4 job:

```
secrets ─┐
         ├─ backend  ─┐
         └─ frontend ─┴─ images
```

| Job | Làm gì | Chặn được gì |
|---|---|---|
| `secrets` | ggshield quét bí mật hardcode | Credential lọt lên GitHub |
| `backend` | `mvn verify` + coverage gate + đếm test | Code hỏng, coverage tụt, suite bị bỏ qua |
| `frontend` | lint, test, build | TypeScript sai, test đỏ, build vỡ |
| `images` | build image + Trivy quét | Lỗ hổng trong dependency và trong OS |

`images` có `needs: [backend, frontend]` — không tốn công build image nếu test đã đỏ.

Ba job đầu chạy **song song**. Job nào cũng có thể fail độc lập, và fail sớm thì biết sớm.

## 2. Vì sao mỗi job tồn tại

### `secrets` — vì sao đặt đầu tiên

Secret lọt lên repo public thì **không rút lại được**. Xoá commit không đủ: GitHub cache, người khác đã fork, bot đã quét. Cách duy nhất là **thu hồi** credential đó.

Nên phải chặn **trước khi** push, không phải sửa sau.

### `backend` — không chỉ chạy test

```yaml
- name: Build and test
  run: mvn -B verify
- name: Upload coverage report
  if: always()
  uses: actions/upload-artifact@v4
- name: Assert the test suites actually ran
  run: ...
```

`if: always()` ở bước upload: **báo cáo coverage phải tải lên được kể cả khi test đỏ** — đó chính là lúc bạn cần xem nó nhất.

### `images` — quét thứ thật sự chạy

Trivy quét **image đã build**, không phải file `pom.xml`. Khác biệt quan trọng:

- Quét `pom.xml` → chỉ thấy dependency mình khai báo
- Quét image → thấy **cả dependency bắc cầu và gói hệ điều hành trong base image**

Trong dự án này, lỗ hổng cuối cùng còn lại nằm ở **OpenSSL của Alpine**, không phải ở Java. Quét manifest sẽ không bao giờ tìm ra.

## 3. Bốn lần pipeline nói dối

Phần quan trọng nhất tài liệu này. Một pipeline sai còn tệ hơn không có pipeline, vì nó tạo niềm tin sai.

### Lần 1: `mvn test` xanh nhưng test tích hợp chưa chạy

Ban đầu chỉ có surefire. `mvn test` báo xanh, 23 test pass.

Nhưng **các file `*IT.java` chưa hề chạy** — surefire chỉ nhận `*Test.java`. 31 test tích hợp nằm im.

Sửa: thêm `maven-failsafe-plugin`, và dùng `mvn verify` thay vì `mvn test`.

| Plugin | Nhận file | Giai đoạn |
|---|---|---|
| surefire | `*Test.java` | `test` |
| failsafe | `*IT.java` | `verify` |

### Lần 2: job xanh trong 33 giây

Backend job báo thành công sau 33 giây. Quá nhanh cho `mvn verify` có Testcontainers khởi động PostgreSQL.

Không đọc được log GitHub Actions nếu chưa đăng nhập (403). **Không có cách kiểm chứng.**

Thay vì đoán, thêm cái đo được:

```bash
count_tests() {
  local dir=$1 min=$2 label=$3
  if [ ! -d "$dir" ]; then echo "::error::$label produced no reports"; exit 1; fi
  total=$(grep -ho 'tests="[0-9]*"' "$dir"/*.xml | grep -o '[0-9]*' | awk '{s+=$1} END {print s+0}')
  echo "$label: $total tests"
  if [ "$total" -lt "$min" ]; then echo "::error::$label ran $total, expected >= $min"; exit 1; fi
}
count_tests target/surefire-reports 20 "Unit tests"
count_tests target/failsafe-reports 25 "Integration tests"
```

Lần chạy sau in ra `Unit tests: 31` và `Integration tests: 40` — nghi ngờ được giải đáp **bằng bằng chứng**, không bằng phỏng đoán. Và từ đó một suite bị bỏ qua không thể báo xanh nữa.

### Lần 3: job "Secret scan" xanh mà không quét gì

```
✓ CI / Secret scan — Succeeded in 13 seconds
```

Nhưng bước duy nhất bên trong bị `skipped`, vì `GITGUARDIAN_API_KEY` chưa cấu hình.

**Nguy hiểm hơn job đỏ.** Job đỏ thì người ta sửa. Job xanh giả thì người ta tin.

```yaml
- name: Report that the scan was skipped
  if: ${{ env.GITGUARDIAN_API_KEY == '' }}
  run: |
    echo "::warning::Secret scan SKIPPED — GITGUARDIAN_API_KEY is not set."
    echo "### Secret scan skipped" >> "$GITHUB_STEP_SUMMARY"
```

### Lần 4: job chết trước khi chạy step nào

```
✗ CI / Container images — Failed in 3 seconds
   → [failure] Set up job
```

Fail ở **"Set up job"** nghĩa là lỗi ở *định nghĩa job*, không phải ở code. Chưa step nào chạy, nên đọc log step là vô ích.

Nguyên nhân: `aquasecurity/trivy-action@0.28.0`. Tag thật là **`v0.28.0`** — có chữ `v`. Ref không phân giải được.

**Cách đọc triệu chứng:**

| Fail ở | Nghĩa là |
|---|---|
| "Set up job" | YAML sai, hoặc action/tag không tồn tại |
| Một step cụ thể | Lệnh trong step đó lỗi |
| "Post ..." | Bước dọn dẹp lỗi, thường vô hại |

## 4. Quản lý bí mật

### Không secret nào được có giá trị mặc định

Lỗi ban đầu:

```yaml
secret: ${APP_JWT_SECRET:change-me-in-production-this-must-be-at-least-32-bytes-long}
```

Default này **nằm trong file jar**. Ai deploy quên set biến môi trường sẽ ký JWT bằng khoá công khai trên GitHub.

Sửa:

```yaml
secret: ${APP_JWT_SECRET}
```

Cộng với `@Validated` ở [`JwtProperties`](../../backend/src/main/java/com/slangword/config/JwtProperties.java) → thiếu là chết ngay lúc khởi động, kèm tên biến:

```
APPLICATION FAILED TO START
    Property: app.jwt.secret
    Reason: set the APP_JWT_SECRET environment variable to a random string of at least 32 characters
```

**Nguyên tắc: thà chết to và rõ còn hơn chạy âm thầm với cấu hình không an toàn.**

### `.env` và `.env.example`

```bash
# .env.example — được commit
POSTGRES_PASSWORD=
APP_JWT_SECRET=
```

Giá trị **để trống** có chủ đích. Ban đầu tôi ghi `CHANGE_ME`, và scanner báo động — bất kỳ chuỗi nào có dạng `KEY=value` đều bị coi là secret. Để trống vừa hết cảnh báo, vừa là template đúng hơn: copy ra mà chưa điền thì compose từ chối chạy.

```yaml
POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?set POSTGRES_PASSWORD in .env}
```

Cú pháp `${BIEN:?thông báo}` của compose: thiếu hoặc rỗng thì dừng kèm thông báo.

### Nhưng điều đó lại làm hỏng CI

Bắt buộc mọi biến nghĩa là compose từ chối **parse** file khi thiếu — kể cả lệnh `build`, vốn không cần biến nào lúc chạy. CI không có `.env` → job chết.

Sửa **không phải** bằng cách nới lỏng yêu cầu, mà bằng cách sinh giá trị vứt đi:

```yaml
- name: Generate throwaway credentials
  run: |
    {
      echo "POSTGRES_PASSWORD=$(openssl rand -hex 16)"
      echo "APP_JWT_SECRET=$(openssl rand -base64 48)"
    } > .env
```

**Không commit giá trị nào, và không hạ tiêu chuẩn.**

### Khi scanner báo, hỏi xem nó chỉ vào cái gì

GitGuardian báo 5 secret trong file test — `Map.of("username", "editor", "password", "secret123")`. Đều là fixture, không phải credential thật.

Phản xạ đầu là thêm vào danh sách bỏ qua. Nhưng nhìn kỹ: **5 class test đều lặp lại y hệt đoạn đăng ký-lấy-token.** Cảnh báo bảo mật chỉ là triệu chứng; bệnh là trùng lặp.

Chữa bệnh — gom vào lớp cha:

```java
protected String createAccountToken(String username) throws Exception { ... }
protected String credentials(String username) throws Exception { ... }
```

Hết cảnh báo, **và** 5 file ngắn đi. Nếu bỏ qua cảnh báo thì mất luôn manh mối.

## 5. Quét lỗ hổng

```yaml
- uses: aquasecurity/trivy-action@v0.36.0
  with:
    image-ref: slangword-api:latest
    severity: HIGH,CRITICAL
    ignore-unfixed: true
    exit-code: '1'
```

### Vì sao `ignore-unfixed: true`

CVE chưa có bản vá thì không làm gì được. Để nó fail build là dạy cả team thói quen bỏ qua job đỏ — mà đó là điều tệ nhất một pipeline có thể gây ra.

### Lần chạy đầu tìm được gì

```
CRITICAL  spring-security-web  CVE-2026-22732   Security policy bypass
HIGH      spring-core          CVE-2025-41249   Annotation Detection
HIGH      spring-expression    CVE-2026-41850   DoS via SpEL
HIGH      spring-webmvc        CVE-2026-41842   DoS
HIGH      spring-webmvc        CVE-2026-41845   XSS
```

Spring Boot 3.3.5 ra tháng 11/2024, lúc quét đã gần 2 năm tuổi.

**Nâng lên 3.5.16 → 71 test vẫn xanh, không sửa một dòng code nào.** Đó là phần thưởng cho việc có bộ test tốt: nâng version là thao tác an toàn, không phải canh bạc.

### Ghim đè phiên bản Spring Boot quản lý

Sau khi nâng vẫn còn Tomcat và pgjdbc. Boot chưa kịp cập nhật thì ghi đè:

```xml
<!-- tomcat 10.1.59 - CVE-2026-65182, CVE-2026-65905, CVE-2026-68525
     pgjdbc 42.7.13 - CVE-2026-54291 -->
<tomcat.version>10.1.59</tomcat.version>
<postgresql.version>42.7.13</postgresql.version>
```

Comment ghi **CVE nào** — để sau này biết khi nào gỡ được override.

### Vá cả base image

Lỗ hổng cuối là OpenSSL trong Alpine, không phải Java. Base image luôn chậm hơn kho gói:

```dockerfile
RUN apk --no-cache upgrade \
    && addgroup -S app && adduser -S app -G app
```

Kết quả:

```
slangword-api:latest (alpine 3.24.1)   0 vulnerabilities
app/app.jar                            0 vulnerabilities
```

## 6. Quality gate

Gate là thứ biến CI từ "chạy cho vui" thành "bảo vệ thật".

| Gate | Ngưỡng | Chặn gì |
|---|---|---|
| Test | mọi test phải pass | Code hỏng |
| Coverage | ≥85% instruction, ≥70% branch | Thêm code mà không thêm test |
| Số lượng test | ≥20 unit, ≥25 integration | Suite bị bỏ qua |
| Trivy | 0 HIGH/CRITICAL có bản vá | Dependency lỗi thời |
| ggshield | 0 secret mới | Credential lọt lên |

**Ngưỡng là sàn, không phải mục tiêu.** Đặt 85% khi thực tế là 91,2% — đủ chỗ cho thay đổi bình thường, nhưng bắt được khi ai đó xoá một mảng test.

**Không bao giờ hạ ngưỡng để build xanh.** Hạ ngưỡng là vứt bỏ chính cái vừa dựng. Nếu coverage tụt thì hoặc viết thêm test, hoặc quyết định có ý thức rằng phần đó không cần test và loại trừ nó tường minh.

## 7. Nếu bị hỏi

**"Pipeline CI của em có gì?"**
→ 4 job: quét bí mật, test + coverage gate, build frontend, build image + quét lỗ hổng. Nói được **vì sao** từng cái tồn tại, không chỉ liệt kê.

**"Quality gate là gì, em đặt cái nào?"**
→ Điều kiện làm build đỏ. Ở đây có 5, kể được ngưỡng và lý do chọn ngưỡng đó.

**"Nếu quét bảo mật báo lỗi thì làm sao?"**
→ Xem có bản vá chưa. Có thì nâng và chạy lại test. Chưa có thì đánh giá xem code mình có chạm vào đường dẫn bị ảnh hưởng không, ghi lại quyết định. Không bao giờ tắt job.

**"Làm sao chắc chắn CI thật sự chạy test?"**
→ Đếm số test trong report và fail nếu thiếu. Tôi thêm sau khi thấy job xong quá nhanh và không có cách kiểm chứng.

**"Secret quản lý thế nào?"**
→ Không có giá trị mặc định trong code; đọc từ biến môi trường; validate lúc khởi động để thiếu là chết ngay; `.env` gitignore, `.env.example` để trống; quét tự động trong CI.
