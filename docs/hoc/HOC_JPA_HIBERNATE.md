# JPA và Hibernate

ORM ánh xạ bảng DB thành object Java. Tài liệu này tập trung vào chỗ ORM **rò rỉ** — nơi bạn buộc phải hiểu SQL bên dưới, vì đó là nơi mọi bug hiệu năng sinh ra.

> **Bắt buộc đọc trước:** `HOC_SQL.md` bên travel-ai-agent. ORM chỉ sinh SQL. Không đọc được `EXPLAIN` thì không debug được ORM.

---

## 1. JPA vs Hibernate vs Spring Data

Ba tầng chồng lên nhau, hay bị lẫn:

| | Là gì |
|---|---|
| **JPA** | Đặc tả (interface). `@Entity`, `@OneToMany`, `EntityManager` |
| **Hibernate** | Bản cài đặt JPA. Thứ thật sự sinh SQL |
| **Spring Data JPA** | Lớp tiện ích trên JPA. Sinh repository từ interface |

Trong repo: khai báo bằng JPA, chạy bằng Hibernate, viết repository bằng Spring Data.

## 2. Entity

[`SlangWord`](../../backend/src/main/java/com/slangword/domain/SlangWord.java):

```java
@Entity
@Table(name = "slang_word")
@EntityListeners(AuditingEntityListener.class)
public class SlangWord {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String word;

    @CreatedDate @Column(nullable = false, updatable = false)
    private Instant createdAt;
```

### `GenerationType.IDENTITY` nghĩa là gì

DB tự sinh id (PostgreSQL `bigserial`). Hệ quả **quan trọng**: Hibernate phải INSERT ngay để biết id, nên **không gộp INSERT theo lô được**.

Với `SEQUENCE`, Hibernate xin trước một dải id nên gộp lô được. Vì sao ở đây vẫn chọn IDENTITY? Vì seed chỉ chạy một lần và 7.641 dòng đã nhanh sẵn. Với bảng ghi liên tục thì `SEQUENCE` là lựa chọn đúng.

### `Instant` chứ không `LocalDateTime`

`Instant` là một mốc thời gian tuyệt đối (UTC). `LocalDateTime` không mang múi giờ — server đổi múi giờ là dữ liệu sai. Với timestamp lưu DB, luôn dùng `Instant` + cột `timestamptz`.

### Vì sao có `protected SlangWord()`

JPA bắt buộc constructor không tham số để tạo instance bằng reflection. Nhưng `protected` ngăn code ứng dụng tạo entity rỗng vô nghĩa. Buộc dùng `new SlangWord("BBC")`.

## 3. Quan hệ

```java
@OneToMany(mappedBy = "slangWord", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("id ASC")
private List<Definition> definitions = new ArrayList<>();
```

Bốn thuộc tính, mỗi cái giải một vấn đề:

| Thuộc tính | Không có thì sao |
|---|---|
| `mappedBy = "slangWord"` | Hibernate tạo **bảng nối** thừa, tưởng là quan hệ n-n |
| `cascade = ALL` | Phải tự `save()` từng `Definition` |
| `orphanRemoval = true` | `definitions.clear()` chỉ set FK = null → vi phạm `not null` |
| `@OrderBy("id ASC")` | Thứ tự nghĩa nhảy lung tung giữa các lần đọc |

### Chiều sở hữu

Trong DB, quan hệ nằm ở **cột FK** `definition.slang_word_id`. Nên bên `@ManyToOne` mới là bên **sở hữu**:

```java
// Definition.java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "slang_word_id", nullable = false)
private SlangWord slangWord;
```

`mappedBy` bên `@OneToMany` nói: "tôi không sở hữu, xem field `slangWord` bên kia".

Hậu quả thực tế: thêm vào list mà không set `slangWord` thì **không lưu được**. Nên `replaceDefinitions` set cả hai chiều:

```java
public void replaceDefinitions(List<String> texts) {
    definitions.clear();
    texts.forEach(text -> definitions.add(new Definition(this, text)));
    //                                                  ↑ truyền this vào
}
```

## 4. LAZY vs EAGER — và bài toán N+1

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
```

- **EAGER**: nạp luôn khi nạp entity cha.
- **LAZY**: chỉ nạp khi thật sự truy cập.

**Luôn mặc định LAZY.** EAGER kéo theo dây chuyền: nạp `Definition` → nạp `SlangWord` → nạp toàn bộ `definitions` của nó → ...

### N+1 là gì

```java
List<SlangWord> words = repository.findAll();        // 1 truy vấn
for (SlangWord w : words) {
    w.getDefinitions().size();                       // N truy vấn nữa
}
```

20 từ = **21 truy vấn**. Mỗi truy vấn một vòng mạng tới DB.

### Cách sửa: `@EntityGraph`

```java
@EntityGraph(attributePaths = "definitions")
List<SlangWord> findByIdIn(Collection<Long> ids);
```

Bảo Hibernate JOIN sẵn `definitions` trong **một** truy vấn.

### Nhưng `@EntityGraph` + phân trang = bẫy

Đây là bug thật số 1 của dự án.

**Code sai ban đầu:**

```java
@EntityGraph(attributePaths = "definitions")
Page<SlangWord> findByWordContainingIgnoreCase(String fragment, Pageable pageable);
```

**Log cảnh báo:**

```
HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
```

**Vì sao:** JOIN làm một từ có 2 nghĩa thành **2 dòng SQL**. Nếu Hibernate đặt `LIMIT 20` ở SQL, nó có thể cắt mất nghĩa thứ hai của từ cuối cùng → kết quả sai. Để tránh sai, Hibernate chọn: **nạp hết mọi dòng khớp vào RAM rồi cắt trang bằng Java**.

Tìm `you` khớp 851 từ → nạp 851 entity để trả 20.

**Cách sửa — hai truy vấn** ([`SlangWordService`](../../backend/src/main/java/com/slangword/service/SlangWordService.java)):

```java
// 1. Phân trang trên id. Không JOIN → LIMIT/OFFSET chạy được ở DB.
Page<Long> ids = repository.findIdsByWordFragment(fragment, pageable);

// 2. Nạp đúng 20 dòng đó, kèm nghĩa.
Map<Long, SlangWord> byId = repository.findByIdIn(ids.getContent()).stream()
        .collect(Collectors.toMap(SlangWord::getId, Function.identity()));

// 3. Khôi phục thứ tự — findByIdIn không hứa thứ tự nào.
List<SlangWordResponse> content = ids.getContent().stream()
        .map(byId::get).filter(Objects::nonNull).map(SlangWordResponse::from).toList();
```

Luôn đúng 2 truy vấn, bất kể kết quả bao nhiêu.

**Quy tắc rút ra: phân trang trên id, không phân trang trên entity có fetch collection.**

## 5. Derived query vs `@Query` — bug thật số 2

Spring Data sinh SQL từ **tên method**:

```java
Optional<User> findByUsername(String username);
// → select * from app_user where username = ?
```

Tiện. Nhưng **bạn không thấy SQL nó tạo ra** — và đó là nơi bug số 2 nằm.

Tôi tạo index:

```sql
create index idx_slang_word_word_lower on slang_word (lower(word));
```

Và viết:

```java
Optional<SlangWord> findByWordIgnoreCase(String word);
```

`EXPLAIN ANALYZE` cho thấy:

```
Seq Scan on slang_word  (actual time=0.020..3.333 rows=1)
  Filter: (upper((word)::text) = 'BBC'::text)
  Rows Removed by Filter: 7640
```

**Spring Data sinh `upper()`, index là `lower()`.** Index chưa bao giờ được dùng. Mọi lần tra từ đều quét 7.640 dòng.

**Sửa — viết tường minh:**

```java
@EntityGraph(attributePaths = "definitions")
@Query("select s from SlangWord s where lower(s.word) = lower(:word)")
Optional<SlangWord> findByWordIgnoreCase(@Param("word") String word);
```

Cùng truy vấn đó với `lower()`:

```
Index Scan using idx_slang_word_word_lower  (actual time=1.843..1.846 rows=1)
```

**Khi nào dùng cái nào:**

| | Derived query | `@Query` |
|---|---|---|
| Truy vấn đơn giản, không nhạy hiệu năng | ✅ | thừa |
| Có index cần khớp chính xác | ❌ | ✅ |
| Điều kiện phức tạp | tên method dài kinh khủng | ✅ |

## 6. Persistence context

Trong một transaction, Hibernate giữ một **cache bậc 1** các entity đang quản lý. Ba hệ quả:

**1. Cùng một object.**

```java
SlangWord a = repository.findById(1L).get();
SlangWord b = repository.findById(1L).get();
// a == b — chỉ một truy vấn
```

**2. Dirty checking — không cần gọi save().**

```java
@Transactional
public SlangWordResponse update(String word, UpdateSlangWordRequest request) {
    SlangWord entity = repository.findByWordIgnoreCase(word).orElseThrow(...);
    entity.replaceDefinitions(...);
    return SlangWordResponse.from(repository.save(entity));  // save() ở đây là thừa
}
```

Entity đang được quản lý → Hibernate tự phát hiện thay đổi và UPDATE lúc commit. Tôi vẫn gọi `save()` cho rõ ý định, nhưng biết là nó không bắt buộc.

**3. `LazyInitializationException`.**

Truy cập field LAZY **sau khi** transaction đóng → nổ. Đây là lý do `application.yml` có:

```yaml
spring:
  jpa:
    open-in-view: false
```

`open-in-view: true` (mặc định của Spring Boot!) giữ session mở suốt request, che lỗi này đi — và che luôn cả N+1 xảy ra trong lúc render. **Tắt nó đi để lỗi lộ ra sớm.**

## 7. Flyway — quản lý thay đổi schema

`ddl-auto: validate` trong `application.yml`. Hibernate **kiểm tra** schema khớp entity, không tự sửa.

**Không bao giờ dùng `ddl-auto: update` ở production.** Nó âm thầm đổi schema theo entity: không review được, không rollback được, không tái lập được.

Schema do Flyway sở hữu, mỗi file chạy đúng một lần theo số thứ tự:

```
V1__init.sql             bảng + index cơ bản
V2__search_indexes.sql   pg_trgm + GIN index
V3__refresh_token.sql    bảng refresh token
```

**Quy tắc bất di bất dịch: file đã chạy thì không sửa.** Flyway lưu checksum; sửa file cũ là build đỏ ở mọi môi trường đã chạy. Muốn đổi thì thêm file mới.

### Dữ liệu thì không dùng migration

Flyway chỉ tạo schema. 7.641 từ nạp bằng [`SeedService`](../../backend/src/main/java/com/slangword/service/SeedService.java) lúc khởi động nếu bảng rỗng — lý do ở tài liệu kiến trúc phần 5.

## 8. Index — điều ORM không làm hộ

Bug số 3: `LIKE '%x%'` không dùng được B-tree.

B-tree sắp xếp theo tiền tố. `%bb%` không có tiền tố để nhảy tới → quét toàn bảng.

[`V2__search_indexes.sql`](../../backend/src/main/resources/db/migration/V2__search_indexes.sql):

```sql
create extension if not exists pg_trgm;
create index idx_definition_text_trgm on definition using gin (lower(text) gin_trgm_ops);
```

Đo thật:

| Truy vấn | Trước | Sau |
|---|---|---|
| `%laugh%` trong nghĩa | Seq Scan, 38.7 ms | Bitmap Heap Scan, 3.0 ms |
| `%bbc%` trong từ | Seq Scan, 22.0 ms | Bitmap Index Scan, 0.199 ms |
| `%bb%` (2 ký tự) | Seq Scan | **vẫn Seq Scan** |

**Giới hạn: cần ≥ 3 ký tự.** Trigram là cụm 3 ký tự; 2 ký tự không tạo nổi một trigram hoàn chỉnh.

### Cách tự kiểm tra

```sql
EXPLAIN ANALYZE SELECT s.id FROM slang_word s WHERE lower(s.word) LIKE lower('%bbc%');
```

Đọc kết quả:
- `Seq Scan` → quét toàn bảng, không dùng index
- `Index Scan` / `Bitmap Index Scan` → có dùng index
- `Rows Removed by Filter: 7584` → đọc 7.584 dòng rồi vứt đi

**Kỹ năng backend quan trọng nhất trong tài liệu này: biết chạy `EXPLAIN ANALYZE` và đọc được nó.** ORM giấu SQL; `EXPLAIN` là cách lôi nó ra.

## 9. Nếu bị hỏi

**"N+1 là gì, sửa thế nào?"**
→ Một truy vấn lấy N bản ghi, rồi N truy vấn nữa lấy quan hệ của từng cái. Sửa bằng `@EntityGraph` hoặc `join fetch`. Nhưng cẩn thận: kết hợp với phân trang thì Hibernate chuyển sang cắt trang trong RAM — tôi gặp đúng lỗi này, sửa bằng cách phân trang trên id rồi mới fetch.

**"LAZY hay EAGER?"**
→ LAZY, luôn luôn. EAGER kéo dây chuyền và không tắt được ở chỗ dùng. Cần dữ liệu thì fetch tường minh ngay chỗ cần.

**"Vì sao không dùng ddl-auto: update?"**
→ Nó đổi schema production mà không ai review, không rollback được, không tái lập được. Dùng `validate` + công cụ migration.

**"Làm sao biết truy vấn có dùng index không?"**
→ `EXPLAIN ANALYZE`. Tôi phát hiện một index chưa từng được dùng vì Spring Data sinh `upper()` còn index là `lower()`.

**"Khi nào derived query không đủ?"**
→ Khi SQL sinh ra phải khớp chính xác với index. Derived query giấu SQL, mà chỗ nhạy cảm hiệu năng thì phải nhìn thấy SQL.
