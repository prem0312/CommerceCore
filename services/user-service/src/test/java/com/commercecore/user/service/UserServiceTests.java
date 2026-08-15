package com.commercecore.user.service;

import com.commercecore.user.dto.AddressRequest;
import com.commercecore.user.dto.AddressResponse;
import com.commercecore.user.dto.UpdateProfileRequest;
import com.commercecore.user.dto.UpdateStatusRequest;
import com.commercecore.user.dto.UserProfileResponse;
import com.commercecore.user.exception.UnauthorizedAccessException;
import com.commercecore.user.exception.UserNotFoundException;
import com.commercecore.user.model.Address;
import com.commercecore.user.model.UserProfile;
import com.commercecore.user.model.UserRole;
import com.commercecore.user.model.UserStatus;
import com.commercecore.user.repository.AddressRepository;
import com.commercecore.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private AddressRepository addressRepository;

    @InjectMocks
    private UserService userService;

    private UserProfile sampleProfile;

    @BeforeEach
    void setUp() {
        sampleProfile = new UserProfile(10L, "Jane Doe", "jane@example.com", "1234567890", UserRole.USER, UserStatus.ACTIVE);
        sampleProfile.setId(1L);
    }

    @Test
    @DisplayName("Should retrieve profile for profile owner")
    void getProfile_ByOwner_Success() {
        when(userProfileRepository.findByUserId(10L)).thenReturn(Optional.of(sampleProfile));

        UserProfileResponse response = userService.getProfile(10L, 10L, "USER");

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.email()).isEqualTo("jane@example.com");
    }

    @Test
    @DisplayName("Should retrieve profile when requester is ADMIN")
    void getProfile_ByAdmin_Success() {
        when(userProfileRepository.findByUserId(10L)).thenReturn(Optional.of(sampleProfile));

        UserProfileResponse response = userService.getProfile(10L, 999L, "ADMIN");

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Should throw UnauthorizedAccessException when non-admin accesses someone else's profile")
    void getProfile_Unauthorized_ThrowsException() {
        assertThatThrownBy(() -> userService.getProfile(10L, 20L, "USER"))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(userProfileRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("Should update user profile successfully")
    void updateProfile_Success() {
        when(userProfileRepository.findByUserId(10L)).thenReturn(Optional.of(sampleProfile));
        when(userProfileRepository.save(any(UserProfile.class))).thenReturn(sampleProfile);

        UpdateProfileRequest request = new UpdateProfileRequest("Jane Updated", "9876543210");
        UserProfileResponse response = userService.updateProfile(10L, request, 10L, "USER");

        assertThat(response.name()).isEqualTo("Jane Updated");
        assertThat(response.phone()).isEqualTo("9876543210");
    }

    @Test
    @DisplayName("Should update status when requested by ADMIN")
    void updateStatus_ByAdmin_Success() {
        when(userProfileRepository.findByUserId(10L)).thenReturn(Optional.of(sampleProfile));
        when(userProfileRepository.save(any(UserProfile.class))).thenReturn(sampleProfile);

        UpdateStatusRequest request = new UpdateStatusRequest(UserStatus.SUSPENDED);
        UserProfileResponse response = userService.updateStatus(10L, request, "ADMIN");

        assertThat(response.status()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    @DisplayName("Should reject status update when requested by standard USER")
    void updateStatus_ByUser_ThrowsUnauthorized() {
        UpdateStatusRequest request = new UpdateStatusRequest(UserStatus.SUSPENDED);

        assertThatThrownBy(() -> userService.updateStatus(10L, request, "USER"))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("Should add address for profile owner")
    void addAddress_Success() {
        when(userProfileRepository.findByUserId(10L)).thenReturn(Optional.of(sampleProfile));
        when(userProfileRepository.save(any(UserProfile.class))).thenReturn(sampleProfile);

        AddressRequest request = new AddressRequest("123 Main St", "Metropolis", "NY", "10001", "USA", true);
        AddressResponse response = userService.addAddress(10L, request, 10L, "USER");

        assertThat(response).isNotNull();
        assertThat(response.street()).isEqualTo("123 Main St");
        assertThat(response.isDefault()).isTrue();
    }
}
