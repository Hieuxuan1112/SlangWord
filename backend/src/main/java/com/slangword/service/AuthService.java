package com.slangword.service;

import com.slangword.domain.Role;
import com.slangword.domain.User;
import com.slangword.dto.AuthDtos.AuthResponse;
import com.slangword.dto.AuthDtos.LoginRequest;
import com.slangword.dto.AuthDtos.RegisterRequest;
import com.slangword.exception.ConflictException;
import com.slangword.repository.UserRepository;
import com.slangword.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already taken: " + request.username());
        }
        User user = userRepository.save(
                new User(request.username(), passwordEncoder.encode(request.password()), Role.USER));
        return toResponse(user);
    }

    /**
     * Fails identically for an unknown user and a wrong password so the API does not
     * reveal which usernames exist.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        String role = user.getRole().name();
        return new AuthResponse(
                jwtService.generateToken(user.getUsername(), role),
                user.getUsername(),
                role,
                jwtService.getExpirationSeconds());
    }
}
