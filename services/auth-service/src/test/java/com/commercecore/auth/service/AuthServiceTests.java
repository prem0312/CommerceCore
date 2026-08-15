package com.commercecore.auth.service;

import com.commercecore.auth.dto.AuthResponse;
import com.commercecore.auth.dto.LoginRequest;
import com.commercecore.auth.dto.RegisterRequest;
import com.commercecore.auth.exception.EmailAlreadyExistsException;
import com.commercecore.auth.exception.InvalidCredentialsException;
import com.commercecore.auth.model.User;
import com.commercecore.auth.model.UserRole;
import com.commercecore.auth.model.UserStatus;
import com.commercecore.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User("John Doe", "john@example.com", "$2a$12$hashedPassword", UserRole.USER, UserStatus.ACTIVE);
        sampleUser.setId(1L);

        adminUser = new User("Admin User", "admin@commercecore.io", "$2a$12$adminHashedPassword", UserRole.ADMIN, UserStatus.ACTIVE);
        adminUser.setId(99L);
    }

    @Test
    @DisplayName("Should successfully register a new user and always assign USER role")
    void register_Success_AlwaysCreatesUserRole() {
        RegisterRequest request = new RegisterRequest("John Doe", "john@example.com", "Password123!");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("$2a$12$hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtService.generateToken(any(User.class))).thenReturn("mock.jwt.token");
        when(jwtService.getExpirationSeconds()).thenReturn(86400L);

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("mock.jwt.token");
        assertThat(response.email()).isEqualTo("john@example.com");
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.role()).isEqualTo(UserRole.USER);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("Should throw EmailAlreadyExistsException when registering duplicate email")
    void register_DuplicateEmail_ThrowsException() {
        RegisterRequest request = new RegisterRequest("John Doe", "john@example.com", "Password123!");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("john@example.com");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should successfully authenticate an ADMIN user")
    void login_AdminUser_Success() {
        LoginRequest request = new LoginRequest("admin@commercecore.io", "AdminPass123!");

        when(userRepository.findByEmail("admin@commercecore.io")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("AdminPass123!", "$2a$12$adminHashedPassword")).thenReturn(true);
        when(jwtService.generateToken(adminUser)).thenReturn("admin.jwt.token");
        when(jwtService.getExpirationSeconds()).thenReturn(86400L);

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("admin.jwt.token");
        assertThat(response.userId()).isEqualTo(99L);
        assertThat(response.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("Should successfully login with valid credentials")
    void login_Success() {
        LoginRequest request = new LoginRequest("john@example.com", "Password123!");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password123!", "$2a$12$hashedPassword")).thenReturn(true);
        when(jwtService.generateToken(sampleUser)).thenReturn("valid.jwt.token");
        when(jwtService.getExpirationSeconds()).thenReturn(86400L);

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("valid.jwt.token");
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.role()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("Should throw InvalidCredentialsException when password does not match")
    void login_WrongPassword_ThrowsException() {
        LoginRequest request = new LoginRequest("john@example.com", "WrongPassword");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPassword", "$2a$12$hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("Should throw InvalidCredentialsException when user not found")
    void login_UserNotFound_ThrowsException() {
        LoginRequest request = new LoginRequest("unknown@example.com", "Password123!");

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }
}
