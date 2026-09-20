package com.bookstore.dto.response;

import com.bookstore.entity.enums.PaymentMethod;
import com.bookstore.entity.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private String transactionId;
    private UUID orderId;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime transactionTimestamp;
    private String receiptUrl;
}
