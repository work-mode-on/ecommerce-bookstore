package com.bookstore.dto.request;

import com.bookstore.entity.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ProcessPaymentRequest {

    @NotNull(message = "Order ID is required")
    private UUID orderId;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    private String currency = "USD";

    // Credit/Debit Card fields
    private String cardNumber;
    private String cardHolderName;
    private Integer expiryMonth;
    private Integer expiryYear;
    private String cvv;

    // UPI field
    private String upiId;

    // Wallet field
    private String walletProvider;
}
