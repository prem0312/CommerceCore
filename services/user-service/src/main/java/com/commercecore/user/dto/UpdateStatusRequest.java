package com.commercecore.user.dto;

import com.commercecore.user.model.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull(message = "Status must be specified")
        UserStatus status
) {
}
