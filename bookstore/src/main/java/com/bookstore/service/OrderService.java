package com.bookstore.service;

import com.bookstore.dto.request.CreateOrderRequest;
import com.bookstore.dto.response.AddressResponse;
import com.bookstore.dto.response.OrderResponse;
import com.bookstore.dto.response.PagedResponse;
import com.bookstore.entity.*;
import com.bookstore.entity.enums.OrderStatus;
import com.bookstore.entity.enums.StockStatus;
import com.bookstore.exception.BadRequestException;
import com.bookstore.exception.ConflictException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.exception.UnprocessableEntityException;
import com.bookstore.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final BookRepository bookRepository;

    @Value("${app.order.cancellation-window-hours}")
    private int cancellationWindowHours;

    @Value("${app.cart.free-shipping-threshold}")
    private BigDecimal freeShippingThreshold;

    @Value("${app.cart.shipping-charge}")
    private BigDecimal shippingCharge;

    @Value("${app.cart.tax-rate}")
    private BigDecimal taxRate;

    // Gift point value: 1 point = $0.01
    private static final BigDecimal GIFT_POINT_VALUE = new BigDecimal("0.01");

    @Transactional
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Address deliveryAddress = addressRepository.findById(request.getDeliveryAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", request.getDeliveryAddressId()));

        Cart cart = cartRepository.findByUserUserId(userId)
                .orElseThrow(() -> new BadRequestException("No active cart found for user"));

        if (cart.getItems().isEmpty()) {
            throw new UnprocessableEntityException("Cart is empty. Add items before placing an order.");
        }

        // Validate stock
        for (CartItem cartItem : cart.getItems()) {
            if (cartItem.getBook().getStockStatus() == StockStatus.OUT_OF_STOCK) {
                throw new UnprocessableEntityException(
                        "Book '" + cartItem.getBook().getTitle() + "' is out of stock. Remove it from cart to proceed.");
            }
        }

        // Build order items
        List<OrderItem> orderItems = cart.getItems().stream()
                .map(ci -> OrderItem.builder()
                        .book(ci.getBook())
                        .format(ci.getFormat())
                        .quantity(ci.getQuantity())
                        .unitPrice(ci.getUnitPrice())
                        .lineTotal(ci.getLineTotal())
                        .build())
                .collect(Collectors.toList());

        // Calculate totals
        BigDecimal subtotal = orderItems.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal delivery = subtotal.compareTo(freeShippingThreshold) >= 0
                ? BigDecimal.ZERO : shippingCharge;

        BigDecimal tax = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);

        // Gift points discount
        BigDecimal giftDiscount = BigDecimal.ZERO;
        int redeemedPoints = request.getRedeemedGiftPoints() != null ? request.getRedeemedGiftPoints() : 0;
        if (redeemedPoints > 0) {
            giftDiscount = BigDecimal.valueOf(redeemedPoints).multiply(GIFT_POINT_VALUE);
        }

        BigDecimal total = subtotal.add(delivery).add(tax).subtract(giftDiscount).max(BigDecimal.ZERO);

        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.Confirmed)
                .deliveryAddress(deliveryAddress)
                .paymentMethod(request.getPaymentMethod())
                .subtotal(subtotal)
                .deliveryCharge(delivery)
                .tax(tax)
                .discount(giftDiscount)
                .total(total)
                .appliedCouponCode(request.getAppliedCouponCode())
                .redeemedGiftPoints(redeemedPoints)
                .cancellationDeadline(LocalDateTime.now().plusHours(cancellationWindowHours))
                .build();

        Order savedOrder = orderRepository.save(order);

        // Associate order items with the saved order
        orderItems.forEach(item -> item.setOrder(savedOrder));
        savedOrder.getItems().addAll(orderItems);
        orderRepository.save(savedOrder);

        // Update sold counts
        orderItems.forEach(item -> {
            Book book = item.getBook();
            book.setSoldCount(book.getSoldCount() + item.getQuantity());
            bookRepository.save(book);
        });

        // Clear the cart
        cart.getItems().clear();
        cart.setSubtotal(BigDecimal.ZERO);
        cart.setDeliveryCharge(BigDecimal.ZERO);
        cart.setTax(BigDecimal.ZERO);
        cart.setDiscount(BigDecimal.ZERO);
        cart.setTotal(BigDecimal.ZERO);
        cartRepository.save(cart);

        return toOrderResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getOrderHistory(UUID userId, int page, int pageSize) {
        findUserOrThrow(userId);
        Page<Order> orderPage = orderRepository.findByUserUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page - 1, pageSize));

        List<OrderResponse> data = orderPage.getContent().stream()
                .map(this::toOrderResponse)
                .collect(Collectors.toList());

        return PagedResponse.<OrderResponse>builder()
                .data(data)
                .currentPage(orderPage.getNumber() + 1)
                .pageSize(orderPage.getSize())
                .totalItems(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        return toOrderResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getStatus() == OrderStatus.Cancelled) {
            throw new BadRequestException("Order is already cancelled");
        }

        if (order.getStatus() == OrderStatus.Delivered) {
            throw new BadRequestException("Delivered orders cannot be cancelled");
        }

        // Business rule: cancel only within 48 hours of order creation
        LocalDateTime deadline = order.getCreatedAt().plusHours(cancellationWindowHours);
        if (LocalDateTime.now().isAfter(deadline)) {
            throw new ConflictException(
                    "Order cannot be cancelled. The " + cancellationWindowHours +
                    "-hour cancellation window has expired.");
        }

        order.setStatus(OrderStatus.Cancelled);
        return toOrderResponse(orderRepository.save(order));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private OrderResponse toOrderResponse(Order order) {
        List<OrderResponse.OrderItemResponse> itemResponses = order.getItems().stream()
                .map(i -> OrderResponse.OrderItemResponse.builder()
                        .bookId(i.getBook().getBookId())
                        .title(i.getBook().getTitle())
                        .author(i.getBook().getAuthor())
                        .format(i.getFormat())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .lineTotal(i.getLineTotal())
                        .build())
                .collect(Collectors.toList());

        Address addr = order.getDeliveryAddress();
        AddressResponse addressResponse = AddressResponse.builder()
                .addressId(addr.getAddressId())
                .firstLine(addr.getFirstLine())
                .secondLine(addr.getSecondLine())
                .city(addr.getCity())
                .state(addr.getState())
                .country(addr.getCountry())
                .pinCode(addr.getPinCode())
                .isDefault(addr.isDefault())
                .build();

        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .userId(order.getUser().getUserId())
                .status(order.getStatus())
                .items(itemResponses)
                .deliveryAddress(addressResponse)
                .paymentMethod(order.getPaymentMethod())
                .subtotal(order.getSubtotal())
                .deliveryCharge(order.getDeliveryCharge())
                .tax(order.getTax())
                .discount(order.getDiscount())
                .total(order.getTotal())
                .appliedCouponCode(order.getAppliedCouponCode())
                .redeemedGiftPoints(order.getRedeemedGiftPoints())
                .trackingNumber(order.getTrackingNumber())
                .cancellationDeadline(order.getCancellationDeadline())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
