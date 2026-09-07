package com.slangword.service;

import com.slangword.domain.Role;
import com.slangword.domain.User;
import com.slangword.dto.AuthDtos.AuthResponse;
import com.slangword.dto.AuthDtos.LoginRequest;
import com.slangword.dto.AuthDtos.RegisterRequest;
import com.slangword.exception.ConflictException;
import com.slangword.repository.UserRepository;
import com.slangword.security.JwtService;
import com.slangword.service.RefreshTokenService.IssuedToken;
import com.slangword.service.RefreshTokenService.Rotation;
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
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already taken: " + request.username());
        }
        User user = userRepository.save(
                new User(request.username(), passwordEncoder.encode(request.password()), Role.USER));
        return issueFor(user);
    }

    /**
     * Fails identically for an unknown user and a wrong password so the API does not
     * reveal which usernames exist.
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        return issueFor(user);
    }

    /**
     * Trades a refresh token for a new pair. The user is looked up by the id the
     * token proves ownership of — no password is involved, so nothing here can be
     * used to escalate to a different account.
     */
    public AuthResponse refresh(String refreshToken) {
        Rotation rotation = refreshTokenService.rotate(refreshToken);
        User user = userRepository.findById(rotation.userId())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        String role = user.getRole().name();
        return new AuthResponse(
                jwtService.generateToken(user.getUsername(), role),
                rotation.token().token(),
                user.getUsername(),
                role,
                jwtService.getExpirationSeconds());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthResponse issueFor(User user) {
        String role = user.getRole().name();
        IssuedToken refresh = refreshTokenService.issue(user.getId());
        return new AuthResponse(
                jwtService.generateToken(user.getUsername(), role),
                refresh.token(),
                user.getUsername(),
                role,
                jwtService.getExpirationSeconds());
    }
}
