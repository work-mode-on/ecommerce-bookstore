package com.bookstore.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressResponse {
    private UUID addressId;
    private String firstLine;
    private String secondLine;
    private String city;
    private String state;
    private String country;
    private String pinCode;
    private boolean isDefault;
}
