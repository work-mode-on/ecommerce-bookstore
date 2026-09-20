package com.bookstore.dto.response;

import com.bookstore.entity.enums.BookFormat;
import com.bookstore.entity.enums.OrderStatus;
import com.bookstore.entity.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private UUID orderId;
    private UUID userId;
    private OrderStatus status;
    private List<OrderItemResponse> items;
    private AddressResponse deliveryAddress;
    private PaymentMethod paymentMethod;
    private BigDecimal subtotal;
    private BigDecimal deliveryCharge;
    private BigDecimal tax;
    private BigDecimal discount;
    private BigDecimal total;
    private String appliedCouponCode;
    private Integer redeemedGiftPoints;
    private String trackingNumber;
    private LocalDateTime cancellationDeadline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private UUID bookId;
        private String title;
        private String author;
        private BookFormat format;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
    }
}
