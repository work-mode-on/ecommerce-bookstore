package com.bookstore.dto.response;

import com.bookstore.entity.enums.BookFormat;
import com.bookstore.entity.enums.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookDetailResponse {
    private UUID bookId;
    private String title;
    private String author;
    private String description;
    private String isbn;
    private BigDecimal price;
    private String publisher;
    private BookFormat format;
    private String language;
    private Integer pageCount;
    private LocalDate publishedDate;
    private String coverImageUrl;
    private BigDecimal rating;
    private Integer soldCount;
    private StockStatus stockStatus;
    private String categoryName;
    private LocalDate tentativeDeliveryDate;
}
