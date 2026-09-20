package com.bookstore.dto.request;

import com.bookstore.entity.enums.BookFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AddCartItemRequest {

    @NotNull(message = "Book ID is required")
    private UUID bookId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "Format is required")
    private BookFormat format;
}
