package com.commercecore.user.service;

import com.commercecore.user.dto.*;
import com.commercecore.user.exception.AddressNotFoundException;
import com.commercecore.user.exception.UnauthorizedAccessException;
import com.commercecore.user.exception.UserNotFoundException;
import com.commercecore.user.model.Address;
import com.commercecore.user.model.UserProfile;
import com.commercecore.user.model.UserRole;
import com.commercecore.user.model.UserStatus;
import com.commercecore.user.repository.AddressRepository;
import com.commercecore.user.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserProfileRepository userProfileRepository;
    private final AddressRepository addressRepository;

    public UserService(UserProfileRepository userProfileRepository, AddressRepository addressRepository) {
        this.userProfileRepository = userProfileRepository;
        this.addressRepository = addressRepository;
    }

    private void validateAccess(Long targetUserId, Long requesterId, String requesterRole) {
        boolean isAdmin = "ADMIN".equalsIgnoreCase(requesterRole);
        boolean isOwner = requesterId != null && requesterId.equals(targetUserId);

        if (!isAdmin && !isOwner) {
            log.warn("Access denied: Requester ID {} cannot access User ID {}", requesterId, targetUserId);
            throw new UnauthorizedAccessException("You do not have permission to access or modify this profile.");
        }
    }

    @Transactional
    public UserProfileResponse getOrCreateProfile(Long userId, String email, String name, String role) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    log.info("Creating initial UserProfile for User ID {}", userId);
                    UserRole userRole = role != null ? UserRole.valueOf(role.toUpperCase()) : UserRole.USER;
                    UserProfile newProfile = new UserProfile(
                            userId,
                            name != null ? name : "User-" + userId,
                            email != null ? email : "user" + userId + "@commercecore.io",
                            null,
                            userRole,
                            UserStatus.ACTIVE
                    );
                    return userProfileRepository.save(newProfile);
                });

        return UserProfileResponse.from(profile);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId, Long requesterId, String requesterRole) {
        validateAccess(userId, requesterId, requesterRole);
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return UserProfileResponse.from(profile);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request, Long requesterId, String requesterRole) {
        validateAccess(userId, requesterId, requesterRole);
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        profile.setName(request.name().trim());
        if (request.phone() != null) {
            profile.setPhone(request.phone().trim());
        }

        UserProfile saved = userProfileRepository.save(profile);
        log.info("Updated profile for User ID {}", userId);
        return UserProfileResponse.from(saved);
    }

    @Transactional
    public UserProfileResponse updateStatus(Long userId, UpdateStatusRequest request, String requesterRole) {
        if (!"ADMIN".equalsIgnoreCase(requesterRole)) {
            throw new UnauthorizedAccessException("Only administrators can modify user account status.");
        }

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        profile.setStatus(request.status());
        UserProfile saved = userProfileRepository.save(profile);
        log.info("Administrator updated status for User ID {} to {}", userId, request.status());
        return UserProfileResponse.from(saved);
    }

    @Transactional
    public AddressResponse addAddress(Long userId, AddressRequest request, Long requesterId, String requesterRole) {
        validateAccess(userId, requesterId, requesterRole);
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Address address = new Address(
                request.street().trim(),
                request.city().trim(),
                request.state().trim(),
                request.postalCode().trim(),
                request.country().trim(),
                request.isDefault()
        );

        profile.addAddress(address);
        userProfileRepository.save(profile);
        log.info("Added address ID {} for User ID {}", address.getId(), userId);
        return AddressResponse.from(address);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(Long userId, Long requesterId, String requesterRole) {
        validateAccess(userId, requesterId, requesterRole);
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return profile.getAddresses().stream().map(AddressResponse::from).toList();
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId, Long requesterId, String requesterRole) {
        validateAccess(userId, requesterId, requesterRole);
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Address address = profile.getAddresses().stream()
                .filter(a -> a.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new AddressNotFoundException(addressId));

        profile.removeAddress(address);
        addressRepository.delete(address);
        log.info("Deleted address ID {} for User ID {}", addressId, userId);
    }
}
