package com.bookstore.dto.response;

import com.bookstore.entity.enums.BookFormat;
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
public class CartResponse {
    private UUID cartId;
    private UUID userId;
    private List<CartItemResponse> items;
    private BigDecimal subtotal;
    private BigDecimal estimatedDeliveryCharge;
    private BigDecimal tax;
    private BigDecimal discount;
    private BigDecimal total;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemResponse {
        private UUID itemId;
        private UUID bookId;
        private String title;
        private String author;
        private String coverImageUrl;
        private BookFormat format;
        private BigDecimal unitPrice;
        private Integer quantity;
        private BigDecimal lineTotal;
    }
}
