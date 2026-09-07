# Testing trong Java

Checklist của nhà tuyển dụng hỏi thẳng: *"Viết Unit & Integration Tests (JUnit, Mockito)... Mức Test Coverage trung bình?"*. Đây là mục dễ ghi điểm nhất, vì hầu hết ứng viên fresher trả lời "em có viết test" mà không có con số.

Repo này: **71 test, 91.2% coverage**, build đỏ nếu tụt dưới ngưỡng.

---

## 1. Tháp test

```
        /\        Ít, chậm, giòn
       /E2E\      → SlangWord chưa có (ghi rõ là chưa)
      /------\
     /  IT    \   40 test — thật DB, thật HTTP, ~25 giây
    /----------\
   /   Unit     \ 31 test — không Spring, không DB, ~2 giây
  /--------------\
```

Vì sao hình tháp chứ không hình chữ nhật: test unit nhanh nên chạy được mỗi lần lưu file. Test integration chậm hơn nhưng bắt được lỗi ở **chỗ ghép nối** — nơi unit test không nhìn thấy.

Trong repo này, **cả 6 bug thật đều bị bắt bởi integration test**, không phải unit test. Vì chúng đều là bug ở chỗ ghép nối: Spring Security với controller, Hibernate với SQL, transaction với exception.

## 2. Unit test — nhanh và cô lập

[`SlangWordServiceTest`](../../backend/src/test/java/com/slangword/service/SlangWordServiceTest.java):

```java
@ExtendWith(MockitoExtension.class)
class SlangWordServiceTest {

    @Mock SlangWordRepository repository;
    @InjectMocks SlangWordService service;

    @Test
    void createRejectsDuplicateWithoutOverwrite() {
        when(repository.findByWordIgnoreCase("BBC")).thenReturn(Optional.of(word("BBC", "old")));

        assertThatThrownBy(() -> service.create(new UpsertSlangWordRequest("BBC", List.of("new")), false))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("BBC");
    }
}
```

Không `@SpringBootTest`. Không DB. **13 test trong 0.5 giây.**

Chạy nhanh được là **hệ quả trực tiếp của thiết kế phân tầng**: service chỉ phụ thuộc repository (một interface), nên thay bằng mock được. Nếu nghiệp vụ nằm trong controller thì không thể.

### Mock là gì

Object giả thay cho thật. `when(...).thenReturn(...)` lập trình sẵn câu trả lời.

**Mock cái gì:** thứ chậm hoặc khó điều khiển — DB, HTTP client, đồng hồ hệ thống.
**Không mock cái gì:** object dữ liệu thuần. `new SlangWord("BBC")` rẻ hơn và thật hơn mock nó.

### `verify` — kiểm tra điều KHÔNG xảy ra

```java
@Test
void emptyResultDoesNotHitTheFetchQuery() {
    when(repository.findIdsByWordFragment(eq("zzz"), any())).thenReturn(new PageImpl<>(List.of()));

    var page = service.search("zzz", SearchField.WORD, PageRequest.of(0, 10));

    assertThat(page.content()).isEmpty();
    verify(repository, never()).findByIdIn(any());   // ← điểm mấu chốt
}
```

Test này bảo vệ một tối ưu: trang rỗng thì **không** chạy truy vấn thứ hai. Không có `verify(never())` thì ai đó xoá dòng `if (ids.isEmpty())` mà test vẫn xanh — hành vi đúng, hiệu năng tệ đi âm thầm.

### Đặt tên test

```java
void createRejectsDuplicateWithoutOverwrite()
void searchKeepsTheOrderOfTheIdPage()
void reusingAnAlreadyRotatedTokenRevokesTheWholeFamily()
```

Tên mô tả **hành vi**, không mô tả method. Đọc danh sách test là đọc được đặc tả hệ thống. So với `testCreate1()`, `testCreate2()` — vô nghĩa.

## 3. Integration test — thật đến đâu

### Vấn đề của H2

Cách phổ biến là dùng H2 in-memory cho test. Repo này **cố ý không dùng**, vì:

- H2 không có `pg_trgm` → không test được index tìm kiếm
- H2 xử lý `lower()`, kiểu ngày giờ, `random()` khác PostgreSQL
- Migration Flyway viết cho PostgreSQL không chạy trên H2

Test qua trên H2 rồi hỏng ở production là **tệ hơn không có test** — nó cho cảm giác an toàn giả.

### Testcontainers

[`AbstractIntegrationTest`](../../backend/src/test/java/com/slangword/AbstractIntegrationTest.java):

```java
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

Khởi động **PostgreSQL 16 thật** trong Docker, đúng version production.

`static` block chứ không `@BeforeEach`: container khởi động **một lần** cho cả suite. `@DynamicPropertySource` là cần thiết vì port container là ngẫu nhiên — không thể ghi cứng vào file cấu hình.

### Test qua HTTP

```java
@AutoConfigureMockMvc
class SlangWordControllerIT extends AbstractIntegrationTest {

    @Test
    void refusesDuplicateUnlessOverwriteRequested() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("word", "BBC", "definitions", List.of("Big Bad Cat")));

        mockMvc.perform(post("/api/v1/slang-words").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Slang word already exists: BBC"));

        mockMvc.perform(post("/api/v1/slang-words").param("overwrite", "true")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }
}
```

Test này đi qua **toàn bộ** chuỗi: filter bảo mật → routing → validation → controller → service → Hibernate → PostgreSQL → serialize JSON. Đúng thứ client thật gặp.

### Một mẹo đọc test đáng học

```java
@Test
void registersThenAuthorisesAProtectedCall() throws Exception {
    String token = register("hieu");

    // 404, không phải 401: chứng tỏ token ĐƯỢC CHẤP NHẬN và request đã tới service.
    mockMvc.perform(delete("/api/v1/slang-words/NOPE").header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
}
```

Khẳng định **404** để chứng minh xác thực thành công. Nếu token bị từ chối thì đã 401. Dùng một mã lỗi để chứng minh một tầng khác đã chạy qua.

## 4. Surefire và Failsafe

Hai plugin, hai vai trò:

| | Plugin | Chạy file | Giai đoạn | Cần Docker |
|---|---|---|---|---|
| Unit | surefire | `*Test.java` | `test` | Không |
| Integration | failsafe | `*IT.java` | `verify` | Có |

```xml
<plugin>
    <artifactId>maven-failsafe-plugin</artifactId>
    <executions>
        <execution>
            <goals><goal>integration-test</goal><goal>verify</goal></goals>
        </execution>
    </executions>
</plugin>
```

Vì sao tách: `mvn test` chạy nhanh trong lúc code. `mvn verify` chạy đủ trước khi push.

**Bug thật:** ban đầu tôi chỉ có surefire. `mvn test` báo xanh nhưng **các file `*IT` chưa hề chạy** — surefire không nhận diện chúng. Xanh giả.

## 5. Coverage

### Đo cái gì

```
INSTRUCTION   91.2%   ← bytecode đã chạy
BRANCH        76.6%   ← nhánh if/else đã đi qua
LINE          90.6%   ← dòng code đã chạy
```

**Branch coverage quan trọng hơn line coverage.** Đoạn này 100% line nhưng chỉ 50% branch:

```java
if (a && b) { doSomething(); }
```

Chạy một lần với `a=true, b=true` là đủ 100% line. Nhưng chưa test `a=false`.

### Vì sao đo gộp unit + integration

```xml
<execution>
    <id>merge-all</id>
    <phase>post-integration-test</phase>
    <goals><goal>merge</goal></goals>
```

Phần lớn hành vi của repo này chỉ chạy qua MockMvc (controller, filter bảo mật, mapping JSON). Chỉ đo unit test thì con số thấp giả tạo, khiến người ta đi viết test vô nghĩa để kéo số lên.

### Gate — con số phải có răng

```xml
<jacoco.min.instruction>0.85</jacoco.min.instruction>
<jacoco.min.branch>0.70</jacoco.min.branch>
```

Build **đỏ** nếu tụt dưới. Ngưỡng là **sàn**, không phải mục tiêu: thấp hơn thực tế (91.2/76.6) đủ để thay đổi bình thường không làm đỏ build, nhưng bắt được khi ai đó xoá một mảng test.

**Không bao giờ hạ ngưỡng để build xanh.** Hạ ngưỡng là bỏ chính cái mình vừa dựng.

Loại trừ những thứ không có logic:

```xml
<exclude>com/slangword/SlangWordApplication.class</exclude>
<exclude>com/slangword/dto/**</exclude>
```

Test một `record` chỉ có getter là đo cho vui.

### Cạm bẫy của coverage

**Coverage cao không có nghĩa test tốt.** Đoạn này 100% coverage mà không kiểm tra gì:

```java
@Test
void badTest() {
    service.search("bb", SearchField.WORD, PageRequest.of(0, 10));
    // không assert gì cả
}
```

Coverage đo **code đã chạy**, không đo **hành vi đã được khẳng định**. Nó bắt được vùng chưa test, không bắt được test dở.

## 6. Chốt chặn trong CI

Vấn đề thật: `mvn verify` xanh **không chứng minh** test đã chạy. Suite bị cấu hình sai cũng báo thành công.

Tôi nghi ngờ khi thấy job backend chỉ mất 33 giây. Không đọc được log Actions (403). Thay vì đoán, thêm chốt chặn vào [`ci.yml`](../../.github/workflows/ci.yml):

```yaml
- name: Assert the test suites actually ran
  run: |
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

Từ đó, một suite bị bỏ qua **không thể** báo xanh.

**Nguyên tắc chung: tin nhưng phải kiểm chứng được.** Nếu không chứng minh được chuyện gì đã xảy ra thì thêm cái đo được.

## 7. Test frontend

[`QuizCard.test.tsx`](../../frontend/src/components/__tests__/QuizCard.test.tsx):

```tsx
it('shows the verdict and locks the options once answered', () => {
    render(<QuizCard question={question} chosen="AAA" onChoose={vi.fn()} />)

    expect(screen.getByRole('status')).toHaveTextContent('Wrong — the answer is BBC')
    expect(screen.getByRole('button', { name: 'BBC' })).toBeDisabled()
})
```

Dùng `getByRole` chứ không `getByTestId` hay class CSS. Lý do: role là thứ **người dùng và screen reader** thấy. Đổi class Tailwind không làm hỏng test; đổi ý nghĩa thì mới hỏng — đúng như mong muốn.

## 8. Nếu bị hỏi

**"Test coverage dự án em bao nhiêu?"**
→ 91.2% instruction, 76.6% branch, đo gộp unit và integration, build fail dưới 85/70. Giải thích vì sao đo gộp và vì sao branch quan trọng hơn line.

**"Unit test khác integration test?"**
→ Unit: một class, phụ thuộc là mock, không framework, mili giây. Integration: nhiều tầng ghép lại, hạ tầng thật, giây. Trong dự án này cả 6 bug thật đều bị bắt bởi integration test, vì chúng đều nằm ở chỗ ghép nối.

**"Vì sao Testcontainers thay vì H2?"**
→ H2 không có `pg_trgm`, xử lý `lower()` và kiểu ngày giờ khác, và migration PostgreSQL không chạy trên nó. Test xanh trên H2 rồi hỏng ở production là tệ hơn không test.

**"Mock cái gì và không mock cái gì?"**
→ Mock thứ chậm hoặc khó điều khiển: DB, HTTP client, đồng hồ. Không mock object dữ liệu thuần — tạo thật vừa rẻ vừa thật hơn.

**"Làm sao biết test đang thật sự chạy?"**
→ CI đếm số test trong report và fail nếu thiếu. Tôi thêm sau khi thấy job xong quá nhanh và không có cách kiểm chứng.
