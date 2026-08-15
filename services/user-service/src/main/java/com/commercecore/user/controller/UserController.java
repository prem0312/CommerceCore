package com.commercecore.user.controller;

import com.commercecore.user.dto.*;
import com.commercecore.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "User profile, address and account status endpoints")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile", description = "Returns the user profile of the currently logged-in user.")
    public ResponseEntity<UserProfileResponse> getCurrentUser(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserProfileResponse response = userService.getOrCreateProfile(userId, email, null, role);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user profile by ID", description = "Retrieves a user profile. Accessible by the profile owner or an ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden: requester does not have access", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User profile not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserProfileResponse> getUserById(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {

        UserProfileResponse response = userService.getProfile(id, requesterId, requesterRole);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user profile", description = "Updates editable profile fields (name, phone). Accessible by owner or ADMIN.")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProfileRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {

        UserProfileResponse response = userService.updateProfile(id, request, requesterId, requesterRole);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update user account status (Admin only)", description = "Suspends or activates a user account. Requires ADMIN role.")
    public ResponseEntity<UserProfileResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {

        UserProfileResponse response = userService.updateStatus(id, request, requesterRole);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/addresses")
    @Operation(summary = "Add shipping address", description = "Adds a new address to the user's profile.")
    public ResponseEntity<AddressResponse> addAddress(
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {

        AddressResponse response = userService.addAddress(id, request, requesterId, requesterRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/addresses")
    @Operation(summary = "Get user addresses", description = "Lists all shipping addresses associated with the user profile.")
    public ResponseEntity<List<AddressResponse>> getAddresses(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {

        List<AddressResponse> responses = userService.getAddresses(id, requesterId, requesterRole);
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/{id}/addresses/{addressId}")
    @Operation(summary = "Delete user address", description = "Removes a specific address from the user profile.")
    public ResponseEntity<Void> deleteAddress(
            @PathVariable Long id,
            @PathVariable Long addressId,
            @RequestHeader(value = "X-User-Id", required = false) Long requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {

        userService.deleteAddress(id, addressId, requesterId, requesterRole);
        return ResponseEntity.noContent().build();
    }
}
