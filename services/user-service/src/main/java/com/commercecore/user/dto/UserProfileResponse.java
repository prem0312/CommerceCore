package com.commercecore.user.dto;

import com.commercecore.user.model.UserProfile;
import com.commercecore.user.model.UserRole;
import com.commercecore.user.model.UserStatus;

import java.time.Instant;
import java.util.List;

public record UserProfileResponse(
        Long id,
        Long userId,
        String name,
        String email,
        String phone,
        UserRole role,
        UserStatus status,
        List<AddressResponse> addresses,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserProfileResponse from(UserProfile profile) {
        List<AddressResponse> addressList = profile.getAddresses() != null
                ? profile.getAddresses().stream().map(AddressResponse::from).toList()
                : List.of();

        return new UserProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getName(),
                profile.getEmail(),
                profile.getPhone(),
                profile.getRole(),
                profile.getStatus(),
                addressList,
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
