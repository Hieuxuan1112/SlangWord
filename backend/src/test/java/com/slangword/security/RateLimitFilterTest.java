package com.slangword.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slangword.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private static final int LIMIT = 10;

    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(new ObjectMapper(),
                new RateLimitProperties(LIMIT, Duration.ofMinutes(1)));
        chain = mock(FilterChain.class);
    }

    private MockHttpServletRequest loginFrom(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(ip);
        return request;
    }

    private MockHttpServletResponse send(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void allowsRequestsUpToTheLimit() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            assertThat(send(loginFrom("10.0.0.1")).getStatus()).isEqualTo(HttpStatus.OK.value());
        }
        verify(chain, times(LIMIT)).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsTheRequestAfterTheLimitAndDoesNotCallTheChain() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            send(loginFrom("10.0.0.1"));
        }

        MockHttpServletResponse response = send(loginFrom("10.0.0.1"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader("Retry-After")).isNotNull();
        assertThat(response.getContentAsString()).contains("Too many authentication attempts");
        verify(chain, times(LIMIT))
                .doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void countsEachClientSeparately() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            send(loginFrom("10.0.0.1"));
        }

        assertThat(send(loginFrom("10.0.0.2")).getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void prefersTheForwardedAddressBehindAProxy() throws Exception {
        // Every request arrives from nginx, so without this header one busy
        // client would exhaust the budget for everyone.
        for (int i = 0; i < LIMIT; i++) {
            MockHttpServletRequest request = loginFrom("172.18.0.5");
            request.addHeader("X-Forwarded-For", "203.0.113.9, 172.18.0.5");
            send(request);
        }

        MockHttpServletRequest other = loginFrom("172.18.0.5");
        other.addHeader("X-Forwarded-For", "203.0.113.10");

        assertThat(send(other).getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void leavesOtherEndpointsAlone() throws Exception {
        MockHttpServletRequest search = new MockHttpServletRequest("GET", "/api/v1/slang-words");
        search.setRemoteAddr("10.0.0.1");

        for (int i = 0; i < LIMIT * 3; i++) {
            assertThat(send(search).getStatus()).isEqualTo(HttpStatus.OK.value());
        }
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull());
    }
}
