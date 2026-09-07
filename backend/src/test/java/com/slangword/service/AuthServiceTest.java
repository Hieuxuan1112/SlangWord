package com.slangword.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.slangword.domain.Role;
import com.slangword.domain.User;
import com.slangword.dto.AuthDtos.LoginRequest;
import com.slangword.dto.AuthDtos.RegisterRequest;
import com.slangword.exception.ConflictException;
import com.slangword.repository.UserRepository;
import com.slangword.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    @InjectMocks
    AuthService service;

    @Test
    void registerRejectsTakenUsername() {
        when(userRepository.existsByUsername("hieu")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("hieu", "secret123")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void registerHashesPasswordAndReturnsToken() {
        when(userRepository.existsByUsername("hieu")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken("hieu", "USER")).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        var response = service.register(new RegisterRequest("hieu", "secret123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void loginRejectsWrongPassword() {
        when(userRepository.findByUsername("hieu"))
                .thenReturn(Optional.of(new User("hieu", "hashed", Role.USER)));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("hieu", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginRejectsUnknownUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost", "whatever")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
