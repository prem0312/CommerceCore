package com.commercecore.auth.config;

import com.commercecore.auth.model.User;
import com.commercecore.auth.model.UserRole;
import com.commercecore.auth.model.UserStatus;
import com.commercecore.auth.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${COMMERCECORE_ADMIN_EMAIL:${app.bootstrap.admin.email:admin@commercecore.io}}")
    private String adminEmail;

    @Value("${COMMERCECORE_ADMIN_PASSWORD:${app.bootstrap.admin.password:}}")
    private String adminPassword;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void validate() {
        if (adminPassword == null || adminPassword.trim().isBlank()) {
            throw new IllegalStateException(
                    "Missing required configuration: COMMERCECORE_ADMIN_PASSWORD must be provided via environment variable."
            );
        }
    }

    @Override
    public void run(String... args) {
        String normalizedEmail = adminEmail.trim().toLowerCase();
        if (!userRepository.existsByEmail(normalizedEmail)) {
            User admin = new User(
                    "System Administrator",
                    normalizedEmail,
                    passwordEncoder.encode(adminPassword),
                    UserRole.ADMIN,
                    UserStatus.ACTIVE
            );
            userRepository.save(admin);
            log.info("Initialized default administrator account: {}", normalizedEmail);
        }
    }
}
