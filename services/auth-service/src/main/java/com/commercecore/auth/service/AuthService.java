package com.commercecore.auth.service;

import com.commercecore.auth.dto.AuthResponse;
import com.commercecore.auth.dto.LoginRequest;
import com.commercecore.auth.dto.RegisterRequest;
import com.commercecore.auth.exception.AccountSuspendedException;
import com.commercecore.auth.exception.EmailAlreadyExistsException;
import com.commercecore.auth.exception.InvalidCredentialsException;
import com.commercecore.auth.model.User;
import com.commercecore.auth.model.UserRole;
import com.commercecore.auth.model.UserStatus;
import com.commercecore.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration failed: Email {} is already registered", normalizedEmail);
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        User user = new User(
                request.name().trim(),
                normalizedEmail,
                encodedPassword,
                UserRole.USER,
                UserStatus.ACTIVE
        );

        User savedUser = userRepository.save(user);
        log.info("Registered new user with ID: {}, email: {}, role: {}", savedUser.getId(), savedUser.getEmail(), savedUser.getRole());

        String token = jwtService.generateToken(savedUser);
        return AuthResponse.of(token, jwtService.getExpirationSeconds(), savedUser.getId(), savedUser.getEmail(), savedUser.getRole(), savedUser.getName());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("Login failed: User not found for email {}", normalizedEmail);
                    return new InvalidCredentialsException("Invalid email or password");
                });

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Login rejected: Account for email {} is {}", normalizedEmail, user.getStatus());
            throw new AccountSuspendedException("Your account is currently " + user.getStatus().name().toLowerCase() + ". Please contact support.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed: Password mismatch for user ID {}", user.getId());
            throw new InvalidCredentialsException("Invalid email or password");
        }

        log.info("Successful login for user ID: {}, email: {}", user.getId(), user.getEmail());
        String token = jwtService.generateToken(user);
        return AuthResponse.of(token, jwtService.getExpirationSeconds(), user.getId(), user.getEmail(), user.getRole(), user.getName());
    }
}
