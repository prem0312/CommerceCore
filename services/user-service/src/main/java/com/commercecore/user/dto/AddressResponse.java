package com.commercecore.user.dto;

import com.commercecore.user.model.Address;

import java.time.Instant;

public record AddressResponse(
        Long id,
        String street,
        String city,
        String state,
        String postalCode,
        String country,
        boolean isDefault,
        Instant createdAt
) {
    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getStreet(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.isDefault(),
                address.getCreatedAt()
        );
    }
}
