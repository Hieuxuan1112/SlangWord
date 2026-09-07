# Spring Security và xác thực

Mảng backend hay bị làm sai nhất, vì làm sai thì **vẫn chạy** — chỉ là không an toàn.

---

## 1. Hai câu hỏi khác nhau

| | Câu hỏi | Mã lỗi |
|---|---|---|
| **Authentication** (xác thực) | Bạn là ai? | **401** Unauthorized |
| **Authorization** (phân quyền) | Bạn được làm gì? | **403** Forbidden |

Lẫn lộn hai cái này là bug thật của dự án (bug 10.1). Spring Security stateless **mặc định trả 403 cho cả hai** — sai với chuẩn REST.

Sửa bằng [`ProblemDetailAuthenticationEntryPoint`](../../backend/src/main/java/com/slangword/security/ProblemDetailAuthenticationEntryPoint.java) — một class implement cả hai interface:

```java
@Component
public class ProblemDetailAuthenticationEntryPoint
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override   // chưa đăng nhập
    public void commence(..., AuthenticationException ex) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    @Override   // đã đăng nhập nhưng không đủ quyền
    public void handle(..., AccessDeniedException ex) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "You are not allowed to perform this action");
    }
}
```

Vì sao phân biệt quan trọng: client cần biết nên **đăng nhập lại** (401) hay **báo là không có quyền** (403). Trả 403 cho request ẩn danh khiến client không biết phải làm gì.

## 2. Filter chain

Spring Security là **một chuỗi filter servlet** chạy trước controller.

[`SecurityConfig`](../../backend/src/main/java/com/slangword/config/SecurityConfig.java):

```java
http
    .csrf(csrf -> csrf.disable())
    .cors(cors -> cors.configurationSource(corsSource))
    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/auth/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/slang-words/**").permitAll()
            .anyRequest().authenticated())
    .exceptionHandling(handling -> handling
            .authenticationEntryPoint(problemDetailHandler)
            .accessDeniedHandler(problemDetailHandler))
    .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
    .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
```

Từng dòng một, và **vì sao**:

### `csrf.disable()` — khi nào được phép

CSRF là tấn công lợi dụng **cookie tự động gửi kèm**. Trang độc hại tạo form submit sang bank.com; trình duyệt tự đính cookie phiên vào.

API này **không dùng cookie**. Token nằm trong header `Authorization`, mà JavaScript phải chủ động thêm vào. Trang khác không thêm được → không có CSRF.

**Nhưng:** nếu đổi sang lưu token trong cookie thì **phải bật CSRF lại**. Tắt CSRF chỉ đúng vì kèm quyết định "không dùng cookie".

### `SessionCreationPolicy.STATELESS`

Không tạo `HttpSession`, không lưu `JSESSIONID`. Mọi request tự mang đủ thông tin xác thực.

Được gì: server nào cũng phục vụ được request nào → scale ngang không cần sticky session hay session store chung.

### Thứ tự `authorizeHttpRequests` quan trọng

Khớp **từ trên xuống, dừng ở cái đầu tiên khớp**. `anyRequest().authenticated()` phải ở cuối cùng — đặt lên đầu thì mọi thứ bị chặn.

Lưu ý `HttpMethod.GET` ở dòng slang-words: **đọc thì công khai, ghi thì phải đăng nhập**. Không có `HttpMethod.GET` thì `POST` cũng được phép.

## 3. Mật khẩu

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

**Không bao giờ lưu mật khẩu dạng thường.** Cũng không dùng MD5/SHA-1 — chúng nhanh, mà nhanh là điều bạn *không* muốn: GPU thử được hàng tỉ SHA-256 mỗi giây.

BCrypt được thiết kế để **chậm có chủ đích** và tự sinh salt riêng cho mỗi mật khẩu (chống rainbow table).

### Không tiết lộ username nào tồn tại

[`AuthService.login`](../../backend/src/main/java/com/slangword/service/AuthService.java):

```java
User user = userRepository.findByUsername(request.username())
        .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
        .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
```

Sai username và sai mật khẩu → **cùng một lỗi**. Nếu trả "user không tồn tại" thì kẻ tấn công dò được danh sách tài khoản.

**Còn thiếu (thành thật):** không có username thì không chạy BCrypt, nên phản hồi nhanh hơn → lộ qua **timing**. Sửa đúng là luôn hash một giá trị giả. Chưa làm.

## 4. JWT

Ba phần ngăn bằng dấu chấm, Base64: `header.payload.signature`.

```java
// JwtService.java
return Jwts.builder()
        .subject(username).claim("role", role)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(expirationSeconds)))
        .signWith(key).compact();
```

**Cực kỳ quan trọng: payload chỉ được ENCODE, không mã hoá.** Ai cũng đọc được nội dung. Chữ ký chỉ chứng minh nó **không bị sửa**.

→ **Không bao giờ đặt dữ liệu nhạy cảm vào JWT.** Ở đây chỉ có username và role.

### Xác thực token

[`JwtAuthFilter`](../../backend/src/main/java/com/slangword/security/JwtAuthFilter.java):

```java
String header = request.getHeader("Authorization");
if (header == null || !header.startsWith("Bearer ")) {
    filterChain.doFilter(request, response);   // đi tiếp, không chặn
    return;
}
String token = header.substring("Bearer ".length());
if (jwtService.isValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
    UserDetails userDetails = userDetailsService.loadUserByUsername(jwtService.extractUsername(token));
    ...
    SecurityContextHolder.getContext().setAuthentication(authentication);
}
filterChain.doFilter(request, response);
```

Điểm thiết kế: filter **không từ chối** request thiếu token — nó chỉ không set authentication rồi đi tiếp. Việc từ chối là của `authorizeHttpRequests`. Nhờ vậy endpoint `permitAll` vẫn nhận được request ẩn danh, mà search vẫn ghi được lịch sử **nếu** có token.

## 5. Vì sao cần refresh token

Đây là phần đáng học nhất.

**Vấn đề: JWT không thu hồi được.** Server không lưu nó, chỉ kiểm tra chữ ký. Token bị lộ thì không có cách nào chặn cho tới khi hết hạn. Ban đầu tôi để 24 giờ — tức là 24 giờ không kiểm soát.

**Nhưng rút ngắn thời gian sống thì user phải đăng nhập liên tục.**

Lối thoát: **hai token, mỗi cái một việc.**

| | Access token | Refresh token |
|---|---|---|
| Dạng | JWT có chữ ký | 256 bit ngẫu nhiên |
| Sống | 15 phút | 7 ngày |
| Server lưu? | Không | Có (hash SHA-256) |
| Thu hồi được? | Không | **Có** |
| Gửi khi nào | Mọi request | Chỉ khi làm mới |

Nguyên tắc: **thứ dùng thường xuyên thì stateless và sống ngắn; thứ sống lâu thì lưu lại để kiểm soát được.**

### Vì sao chỉ lưu hash, và vì sao SHA-256 chứ không BCrypt

```java
// RefreshTokenService.java
repository.save(new RefreshToken(userId, hash(token), familyId, expiresAt));
```

DB lộ thì kẻ tấn công có hash, không dùng được.

SHA-256 thay vì BCrypt vì hai lý do:

1. **Token đã là 256 bit ngẫu nhiên.** BCrypt chậm có ích khi mật khẩu yếu và đoán được. Ở đây không có gì để đoán — không gian tìm kiếm là 2²⁵⁶.
2. **BCrypt salt khác nhau mỗi lần hash** → không tra bằng index được. Muốn tìm token phải quét toàn bảng và `matches()` từng dòng.

**Chọn thuật toán theo bài toán, không theo "cái nào an toàn hơn".** Đây là câu hỏi phỏng vấn hay gặp.

### Xoay vòng + phát hiện tái sử dụng

Mỗi lần refresh: cấp token mới, đánh dấu cũ đã dùng.

```java
if (!stored.isUsable(now)) {
    int revoked = revoker.revokeFamily(stored.getFamilyId(), now);
    log.warn("Refresh token reuse detected for user {}...", stored.getUserId());
    throw new BadCredentialsException("Invalid refresh token");
}
```

Trình lại token đã dùng nghĩa là gì? Hai khả năng:
- Token bị đánh cắp, kẻ trộm đang dùng
- Client hợp lệ bị lệch (mất mạng giữa chừng)

**Không phân biệt được**, nên thu hồi cả họ — bắt cả hai đăng nhập lại. Thà phiền còn hơn để kẻ trộm ở lại.

`familyId` nối các token cùng một lần đăng nhập → đăng xuất một thiết bị không đá văng thiết bị khác. Có test `logoutLeavesOtherSessionsAlone`.

### Bug transaction (quan trọng)

Lần viết đầu thất bại. Test `reusingAnAlreadyRotatedTokenRevokesTheWholeFamily` báo token vẫn dùng được.

**Vì `@Transactional` rollback khi gặp RuntimeException.** Chính exception dùng để từ chối request đã xoá luôn việc thu hồi.

Thử `noRollbackFor = BadCredentialsException.class` → **vẫn hỏng**, vì `AuthService.refresh` cũng `@Transactional` và giao dịch ngoài mới là chủ.

Sửa đúng — [`RefreshTokenRevoker`](../../backend/src/main/java/com/slangword/service/RefreshTokenRevoker.java) là **bean riêng** với `REQUIRES_NEW`:

```java
@Component
public class RefreshTokenRevoker {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeFamily(UUID familyId, Instant now) {
        return repository.revokeFamily(familyId, now);
    }
}
```

Phải tách bean vì Spring dùng proxy — gọi `this.revokeFamily()` sẽ bỏ qua proxy và âm thầm chạy trong giao dịch cũ.

**Bài học tổng quát: hành động bảo mật phải commit độc lập với kết quả của request gây ra nó.** Cùng nguyên tắc với audit log.

## 6. Client phải xử lý gì

[`frontend/src/api/client.ts`](../../frontend/src/api/client.ts):

```java
let refreshInFlight: Promise<string> | null = null;
```

Vì sao cần biến này: một trang gọi 3 API cùng lúc, cả 3 nhận 401 → cả 3 gọi refresh. Server **xoay vòng** token, nên chỉ một cái thắng; hai cái kia trình token đã dùng → **kích hoạt phát hiện replay** → user bị đăng xuất **vì đã mở một trang bận**.

Chia sẻ chung một promise đang bay: một lần refresh duy nhất, cả ba cùng chờ.

Đây là ví dụ điển hình: **quyết định ở backend tạo ra ràng buộc ở frontend.** Fullstack là phải thấy được cả hai đầu.

## 7. Rate limiting

[`RateLimitFilter`](../../backend/src/main/java/com/slangword/security/RateLimitFilter.java) chỉ bảo vệ `/api/v1/auth/**`.

Vì sao chỉ chỗ đó: **login là thao tác ghi duy nhất không cần token**. Mọi endpoint ghi khác đã đòi JWT hợp lệ.

Đặt **trước** `JwtAuthFilter` để trận brute force bị chặn trước khi server tốn công hash BCrypt.

```java
private String clientKey(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
        return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
}
```

Phải đọc `X-Forwarded-For` vì mọi request đi qua nginx — nếu không, **mọi người dùng trông như một client** và một người dùng nhiều là chặn hết cả.

**Cảnh báo:** header này client tự đặt được. Chỉ tin nó khi chắc chắn có reverse proxy phía trước ghi đè. nginx của repo này làm đúng vậy:

```nginx
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
```

### Giới hạn đã biết

- **Fixed window** → ở ranh giới 2 cửa sổ có thể qua 2× giới hạn. Chấp nhận được với mục đích chống dò mật khẩu.
- **Bộ đếm trong RAM một instance** → 3 replica thì giới hạn thực tế gấp 3. Đúng phải dùng Redis.

Cả hai đã ghi trong javadoc. **Giới hạn được ghi lại tốt hơn giới hạn bị giấu.**

## 8. Bảo mật ở tầng HTTP

[`nginx.conf`](../../frontend/nginx.conf):

| Header | Chặn được gì |
|---|---|
| `X-Content-Type-Options: nosniff` | Trình duyệt đoán MIME type và chạy file upload như script |
| `X-Frame-Options: DENY` | Clickjacking — nhúng site vào iframe trong suốt |
| `Content-Security-Policy` | XSS — chặn script từ nguồn lạ |
| `Referrer-Policy: no-referrer` | Rò URL nội bộ sang site khác |
| `Strict-Transport-Security` | Hạ cấp HTTPS → HTTP |

**Bẫy nginx (bug 10.5):** nginx **không gộp** `add_header`. Block nào khai báo dù chỉ một cái thì vứt toàn bộ những cái lẽ ra kế thừa. Tôi đặt 4 header ở scope ngoài + HSTS trong block TLS → chỉ HSTS sống sót. Phải lặp lại đủ trong mỗi block.

## 9. Nếu bị hỏi

**"401 khác 403 thế nào?"**
→ 401 = chưa biết bạn là ai. 403 = biết rồi nhưng không cho. Spring Security stateless mặc định trả 403 cho cả hai, phải viết entry point riêng để sửa.

**"Vì sao tắt CSRF được?"**
→ Vì API không dùng cookie. Token ở header `Authorization`, JS phải chủ động thêm, site khác không làm được. Nếu chuyển sang cookie thì phải bật lại.

**"JWT lưu ở đâu phía client?"**
→ localStorage dễ bị XSS đọc; httpOnly cookie chống được XSS nhưng mở ra CSRF. Repo này dùng localStorage + CSP nghiêm ngặt + access token sống 15 phút để giảm thiệt hại. Không có lựa chọn hoàn hảo — phải nêu được đánh đổi.

**"Token bị lộ thì làm sao?"**
→ Access token thì không thu hồi được, nên nó chỉ sống 15 phút. Refresh token thì lưu server nên revoke được, và nếu bị replay thì cả họ token bị thu hồi.

**"Vì sao hash refresh token bằng SHA-256 chứ không BCrypt?"**
→ Token đã có 256 bit entropy nên không có gì để dò; và BCrypt có salt riêng nên không tra bằng index được.
