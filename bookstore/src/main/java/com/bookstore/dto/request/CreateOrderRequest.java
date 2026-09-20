package com.bookstore.dto.request;

import com.bookstore.entity.enums.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateOrderRequest {

    @NotNull(message = "Delivery address ID is required")
    private UUID deliveryAddressId;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    private String appliedCouponCode;

    @Min(value = 0, message = "Redeemed gift points cannot be negative")
    private Integer redeemedGiftPoints = 0;
}
