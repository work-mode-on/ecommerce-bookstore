package com.bookstore.service;

import com.bookstore.dto.request.AddCartItemRequest;
import com.bookstore.dto.request.UpdateCartItemRequest;
import com.bookstore.dto.response.CartResponse;
import com.bookstore.entity.Book;
import com.bookstore.entity.Cart;
import com.bookstore.entity.CartItem;
import com.bookstore.entity.User;
import com.bookstore.entity.enums.StockStatus;
import com.bookstore.exception.BadRequestException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.exception.UnprocessableEntityException;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.CartItemRepository;
import com.bookstore.repository.CartRepository;
import com.bookstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    @Value("${app.cart.free-shipping-threshold}")
    private BigDecimal freeShippingThreshold;

    @Value("${app.cart.shipping-charge}")
    private BigDecimal shippingCharge;

    @Value("${app.cart.tax-rate}")
    private BigDecimal taxRate;

    @Transactional(readOnly = true)
    public CartResponse getCart(UUID userId) {
        Cart cart = getOrCreateCart(userId);
        return toCartResponse(cart);
    }

    @Transactional
    public CartResponse addItem(UUID userId, AddCartItemRequest request) {
        Cart cart = getOrCreateCart(userId);

        Book book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book", "id", request.getBookId()));

        if (book.getStockStatus() == StockStatus.OUT_OF_STOCK) {
            throw new UnprocessableEntityException("Book '" + book.getTitle() + "' is currently out of stock");
        }

        // If same book+format already in cart, just increment quantity
        cartItemRepository.findByCartCartIdAndBookBookIdAndFormat(
                        cart.getCartId(), book.getBookId(), request.getFormat())
                .ifPresentOrElse(
                        existing -> {
                            existing.setQuantity(existing.getQuantity() + request.getQuantity());
                            existing.setLineTotal(book.getPrice().multiply(
                                    BigDecimal.valueOf(existing.getQuantity())));
                            cartItemRepository.save(existing);
                        },
                        () -> {
                            CartItem item = CartItem.builder()
                                    .cart(cart)
                                    .book(book)
                                    .format(request.getFormat())
                                    .quantity(request.getQuantity())
                                    .unitPrice(book.getPrice())
                                    .lineTotal(book.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())))
                                    .build();
                            cart.getItems().add(cartItemRepository.save(item));
                        });

        recalculateTotals(cart);
        return toCartResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse updateItem(UUID userId, UUID itemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateCart(userId);

        CartItem item = cartItemRepository.findByCartCartIdAndItemId(cart.getCartId(), itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", itemId));

        item.setQuantity(request.getQuantity());
        item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
        cartItemRepository.save(item);

        recalculateTotals(cart);
        return toCartResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse removeItem(UUID userId, UUID itemId) {
        Cart cart = getOrCreateCart(userId);

        CartItem item = cartItemRepository.findByCartCartIdAndItemId(cart.getCartId(), itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", itemId));

        cart.getItems().remove(item);
        cartItemRepository.delete(item);

        recalculateTotals(cart);
        return toCartResponse(cartRepository.save(cart));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private Cart getOrCreateCart(UUID userId) {
        return cartRepository.findByUserUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
                    Cart newCart = Cart.builder().user(user).build();
                    return cartRepository.save(newCart);
                });
    }

    private void recalculateTotals(Cart cart) {
        // Reload items from DB to get fresh data
        BigDecimal subtotal = cart.getItems().stream()
                .map(CartItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal delivery = subtotal.compareTo(freeShippingThreshold) >= 0
                ? BigDecimal.ZERO
                : shippingCharge;

        BigDecimal tax = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(delivery).add(tax).subtract(cart.getDiscount());

        cart.setSubtotal(subtotal);
        cart.setDeliveryCharge(delivery);
        cart.setTax(tax);
        cart.setTotal(total.max(BigDecimal.ZERO));
    }

    private CartResponse toCartResponse(Cart cart) {
        List<CartResponse.CartItemResponse> items = cart.getItems().stream()
                .map(i -> CartResponse.CartItemResponse.builder()
                        .itemId(i.getItemId())
                        .bookId(i.getBook().getBookId())
                        .title(i.getBook().getTitle())
                        .author(i.getBook().getAuthor())
                        .coverImageUrl(i.getBook().getCoverImageUrl())
                        .format(i.getFormat())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .lineTotal(i.getLineTotal())
                        .build())
                .collect(Collectors.toList());

        return CartResponse.builder()
                .cartId(cart.getCartId())
                .userId(cart.getUser().getUserId())
                .items(items)
                .subtotal(cart.getSubtotal())
                .estimatedDeliveryCharge(cart.getDeliveryCharge())
                .tax(cart.getTax())
                .discount(cart.getDiscount())
                .total(cart.getTotal())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }
}
