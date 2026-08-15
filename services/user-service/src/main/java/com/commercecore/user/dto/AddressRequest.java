package com.commercecore.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank(message = "Street must not be blank")
        @Size(max = 200, message = "Street must not exceed 200 characters")
        String street,

        @NotBlank(message = "City must not be blank")
        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @NotBlank(message = "State must not be blank")
        @Size(max = 100, message = "State must not exceed 100 characters")
        String state,

        @NotBlank(message = "Postal code must not be blank")
        @Size(max = 20, message = "Postal code must not exceed 20 characters")
        String postalCode,

        @NotBlank(message = "Country must not be blank")
        @Size(max = 100, message = "Country must not exceed 100 characters")
        String country,

        boolean isDefault
) {
}
