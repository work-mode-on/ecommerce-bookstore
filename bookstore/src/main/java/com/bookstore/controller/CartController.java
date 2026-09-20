package com.bookstore.controller;

import com.bookstore.dto.request.AddCartItemRequest;
import com.bookstore.dto.request.UpdateCartItemRequest;
import com.bookstore.dto.response.CartResponse;
import com.bookstore.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Shopping cart management")
@SecurityRequirement(name = "BearerAuth")
public class CartController {

    private final CartService cartService;

    @GetMapping("/{userId}")
    @Operation(summary = "Get active cart for a user")
    public ResponseEntity<CartResponse> getCart(@PathVariable UUID userId) {
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    @PostMapping("/{userId}/items")
    @Operation(summary = "Add a book to the cart")
    public ResponseEntity<CartResponse> addItem(@PathVariable UUID userId,
                                                 @Valid @RequestBody AddCartItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.addItem(userId, request));
    }

    @PutMapping("/{userId}/items/{itemId}")
    @Operation(summary = "Update quantity of a cart item")
    public ResponseEntity<CartResponse> updateItem(@PathVariable UUID userId,
                                                    @PathVariable UUID itemId,
                                                    @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(cartService.updateItem(userId, itemId, request));
    }

    @DeleteMapping("/{userId}/items/{itemId}")
    @Operation(summary = "Remove an item from the cart")
    public ResponseEntity<CartResponse> removeItem(@PathVariable UUID userId,
                                                    @PathVariable UUID itemId) {
        return ResponseEntity.ok(cartService.removeItem(userId, itemId));
    }
}
