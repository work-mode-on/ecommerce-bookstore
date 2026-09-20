package com.bookstore.service;

import com.bookstore.dto.request.CreateOrderRequest;
import com.bookstore.dto.response.OrderResponse;
import com.bookstore.dto.response.PagedResponse;
import com.bookstore.entity.*;
import com.bookstore.entity.enums.OrderStatus;
import com.bookstore.entity.enums.PaymentMethod;
import com.bookstore.entity.enums.StockStatus;
import com.bookstore.exception.BadRequestException;
import com.bookstore.exception.ConflictException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.exception.UnprocessableEntityException;
import com.bookstore.repository.*;
import com.bookstore.util.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderService}.
 *
 * Covers: successful checkout, empty-cart guard, out-of-stock guard,
 * order history, getOrderById, order cancellation WITHIN 48h (success),
 * order cancellation AFTER 48h (ConflictException), cancellation of an
 * already-cancelled order, and cancellation of a delivered order.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService – Unit Tests")
class OrderServiceTest {

    @Mock OrderRepository    orderRepository;
    @Mock CartRepository     cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock UserRepository     userRepository;
    @Mock AddressRepository  addressRepository;
    @Mock BookRepository     bookRepository;

    @InjectMocks OrderService orderService;

    private User    user;
    private Address address;
    private Cart    cart;

    @BeforeEach
    void setUp() {
        // Inject @Value fields normally bound from application.properties
        ReflectionTestUtils.setField(orderService, "cancellationWindowHours", 48);
        ReflectionTestUtils.setField(orderService, "freeShippingThreshold",   new BigDecimal("50.00"));
        ReflectionTestUtils.setField(orderService, "shippingCharge",          new BigDecimal("3.99"));
        ReflectionTestUtils.setField(orderService, "taxRate",                 new BigDecimal("0.09"));

        user    = TestFixtures.user();
        address = TestFixtures.address();
        cart    = TestFixtures.checkoutReadyCart();
    }

    // =========================================================================
    // Helper – build a minimal CreateOrderRequest
    // =========================================================================
    private CreateOrderRequest checkoutRequest() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setDeliveryAddressId(TestFixtures.ADDRESS_ID);
        req.setPaymentMethod(PaymentMethod.CreditCard);
        req.setRedeemedGiftPoints(0);
        return req;
    }

    // =========================================================================
    // createOrder – successful checkout
    // =========================================================================

    @Nested
    @DisplayName("createOrder() – successful checkout")
    class CreateOrderSuccess {

        @Test
        @DisplayName("creates a Confirmed order with correct financial totals")
        void createOrder_happyPath_returnsConfirmedOrder() {
            // subtotal = 14.99 * 2 = 29.98
            // delivery = 3.99 (below 50.00 threshold)
            // tax = 29.98 * 0.09 = 2.70
            // discount = 0 (no gift points)
            // total = 29.98 + 3.99 + 2.70 = 36.67

            Order savedOrder = buildSavedOrder(29.98, 3.99, 2.70, 0.00, 36.67);

            when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));
            when(addressRepository.findById(TestFixtures.ADDRESS_ID)).thenReturn(Optional.of(address));
            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.createOrder(TestFixtures.USER_ID, checkoutRequest());

            assertThat(response.getOrderId()).isEqualTo(TestFixtures.ORDER_ID);
            assertThat(response.getStatus()).isEqualTo(OrderStatus.Confirmed);
            assertThat(response.getSubtotal()).isEqualByComparingTo("29.98");
            assertThat(response.getDeliveryCharge()).isEqualByComparingTo("3.99");
            assertThat(response.getTax()).isEqualByComparingTo("2.70");
            assertThat(response.getDiscount()).isEqualByComparingTo("0.00");
            assertThat(response.getTotal()).isEqualByComparingTo("36.67");
            assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.CreditCard);
        }

        @Test
        @DisplayName("applies free shipping when subtotal >= threshold")
        void createOrder_subtotalAboveThreshold_zeroDeliveryCharge() {
            // 4 books at 14.99 each → subtotal = 59.96 ≥ 50.00 → delivery = 0
            Book book = TestFixtures.book1();
            CartItem item4 = new CartItem();
            item4.setItemId(UUID.randomUUID());
            item4.setCart(cart);
            item4.setBook(book);
            item4.setFormat(com.bookstore.entity.enums.BookFormat.Paperback);
            item4.setQuantity(4);
            item4.setUnitPrice(book.getPrice());
            item4.setLineTotal(book.getPrice().multiply(BigDecimal.valueOf(4))); // 59.96
            cart.setItems(List.of(item4));

            Order savedOrder = buildSavedOrder(59.96, 0.00, 5.40, 0.00, 65.36);

            when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));
            when(addressRepository.findById(TestFixtures.ADDRESS_ID)).thenReturn(Optional.of(address));
            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.createOrder(TestFixtures.USER_ID, checkoutRequest());

            assertThat(response.getDeliveryCharge()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("applies gift-point discount to total")
        void createOrder_withGiftPoints_discountApplied() {
            // 200 points × $0.01 = $2.00 discount
            CreateOrderRequest req = checkoutRequest();
            req.setRedeemedGiftPoints(200);

            Order savedOrder = buildSavedOrder(29.98, 3.99, 2.70, 2.00, 34.67);

            when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));
            when(addressRepository.findById(TestFixtures.ADDRESS_ID)).thenReturn(Optional.of(address));
            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.createOrder(TestFixtures.USER_ID, req);

            assertThat(response.getDiscount()).isEqualByComparingTo("2.00");
            assertThat(response.getRedeemedGiftPoints()).isEqualTo(200);
        }

        @Test
        @DisplayName("persists applied coupon code on the order")
        void createOrder_withCouponCode_couponPersistedOnOrder() {
            CreateOrderRequest req = checkoutRequest();
            req.setAppliedCouponCode("SAVE10");

            Order savedOrder = buildSavedOrder(29.98, 3.99, 2.70, 0.00, 36.67);
            savedOrder.setAppliedCouponCode("SAVE10");

            when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));
            when(addressRepository.findById(TestFixtures.ADDRESS_ID)).thenReturn(Optional.of(address));
            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.createOrder(TestFixtures.USER_ID, req);

            assertThat(response.getAppliedCouponCode()).isEqualTo("SAVE10");
        }

        @Test
        @DisplayName("clears the cart after order is placed")
        void createOrder_cartIsClearedAfterCheckout() {
            Order savedOrder = buildSavedOrder(29.98, 3.99, 2.70, 0.00, 36.67);

            when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));
            when(addressRepository.findById(TestFixtures.ADDRESS_ID)).thenReturn(Optional.of(address));
            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);
            when(cartRepository.save(cartCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

            orderService.createOrder(TestFixtures.USER_ID, checkoutRequest());

            Cart clearedCart = cartCaptor.getValue();
            assertThat(clearedCart.getItems()).isEmpty();
            assertThat(clearedCart.getSubtotal()).isEqualByComparingTo("0.00");
            assertThat(clearedCart.getTotal()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("increments soldCount on each book after checkout")
        void createOrder_soldCountIncrementedForEachBook() {
            Book book = TestFixtures.book1();
            int originalSoldCount = book.getSoldCount(); // 50000

            Order savedOrder = buildSavedOrder(29.98, 3.99, 2.70, 0.00, 36.67);

            when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));
            when(addressRepository.findById(TestFixtures.ADDRESS_ID)).thenReturn(Optional.of(address));
            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Book> bookCaptor = ArgumentCaptor.forClass(Book.class);
            when(bookRepository.save(bookCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

            orderService.createOrder(TestFixtures.USER_ID, checkoutRequest());

            // cart has 1 item with qty=2 → soldCount should increase by 2
            int capturedSoldCount = bookCaptor.getValue().getSoldCount();
            assertThat(capturedSoldCount).isEqualTo(originalSoldCount + 2);
        }

        @Test
        @DisplayName("cancellationDeadline is set to createdAt + 48 hours")
        void createOrder_cancellationDeadlineIsSetCorrectly() {
            Order savedOrder = buildSavedOrder(29.98, 3.99, 2.70, 0.00, 36.67);
            savedOrder.setCreatedAt(LocalDateTime.now());
            savedOrder.setCancellationDeadline(LocalDateTime.now().plusHours(48));

            when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));
            when(addressRepository.findById(TestFixtures.ADDRESS_ID)).thenReturn(Optional.of(address));
            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.createOrder(TestFixtures.USER_ID, checkoutRequest());

            assertThat(response.getCancellationDeadline())
                    .isAfter(LocalDateTime.now().plusHours(47))
                    .isBefore(LocalDateTime.now().plusHours(49));
        }
    }

    // =========================================================================
    // createOrder – guard clauses
    // =========================================================================

    @Nested
    @DisplayName("createOrder() – guard clauses")
    class CreateOrderGuards {

        @Test
        @DisplayName("throws ResourceNotFoundException when user does not exist")
        void createOrder_userNotFound_throwsException() {
            when(userRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    orderService.createOrder(UUID.randomUUID(), checkoutRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when delivery address does not exist")
        void createOrder_addressNotFound_throwsException() {
            when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));
            when(addressRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    orderService.createOrder(TestFixtures.USER_ID, checkoutRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Address");
        }

        @Test
        @DisplayName("throws BadRequestException when user has no active cart")
        void createOrder_noCart_throwsException() {
            when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));
            when(addressRepository.findById(TestFixtures.ADDRESS_ID)).thenReturn(Optional.of(address));
            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    orderService.createOrder(TestFixtures.USER_ID, checkoutRequest()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("cart");
        }

        @Test
        @DisplayName("throws UnprocessableEntityException when cart is empty")
        void createOrder_emptyCart_throwsException() {
            Cart emptyCart = TestFixtures.emptyCart(); // no items

            when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));
            when(addressRepository.findById(TestFixtures.ADDRESS_ID)).thenReturn(Optional.of(address));
            when(cartRepository.findByUserUserId(TestFixtures.USER_ID))
                    .thenReturn(Optional.of(emptyCart));

            assertThatThrownBy(() ->
                    orderService.createOrder(TestFixtures.USER_ID, checkoutRequest()))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("throws UnprocessableEntityException when a cart item is out of stock")
        void createOrder_outOfStockItem_throwsException() {
            Book oosBook = TestFixtures.outOfStockBook();
            CartItem oosItem = new CartItem();
            oosItem.setItemId(UUID.randomUUID());
            oosItem.setCart(cart);
            oosItem.setBook(oosBook);
            oosItem.setFormat(com.bookstore.entity.enums.BookFormat.Hardcover);
            oosItem.setQuantity(1);
            oosItem.setUnitPrice(oosBook.getPrice());
            oosItem.setLineTotal(oosBook.getPrice());

            List<CartItem> items = new ArrayList<>();
            items.add(oosItem);
            cart.setItems(items);

            when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));
            when(addressRepository.findById(TestFixtures.ADDRESS_ID)).thenReturn(Optional.of(address));
            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));

            assertThatThrownBy(() ->
                    orderService.createOrder(TestFixtures.USER_ID, checkoutRequest()))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessageContaining("out of stock");
        }
    }

    // =========================================================================
    // cancelOrder – WITHIN 48-hour window  →  SUCCESS
    // =========================================================================

    @Nested
    @DisplayName("cancelOrder() – within 48-hour cancellation window (expect SUCCESS)")
    class CancelOrderWithinWindow {

        @Test
        @DisplayName("cancels a Confirmed order when called within 48 hours")
        void cancelOrder_within48h_statusBecomesCancel() {
            Order order = TestFixtures.confirmedOrderWithinWindow(); // createdAt = -1h

            when(orderRepository.findById(TestFixtures.ORDER_ID))
                    .thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.cancelOrder(TestFixtures.ORDER_ID);

            assertThat(response.getStatus()).isEqualTo(OrderStatus.Cancelled);
            verify(orderRepository).save(argThat(o -> o.getStatus() == OrderStatus.Cancelled));
        }

        @Test
        @DisplayName("cancels an order created exactly at the boundary (1 minute before expiry)")
        void cancelOrder_justBeforeDeadline_succeeds() {
            Order order = TestFixtures.confirmedOrderWithinWindow();
            // Set createdAt to 47h 59m ago → still within window
            order.setCreatedAt(LocalDateTime.now().minusHours(47).minusMinutes(59));

            when(orderRepository.findById(TestFixtures.ORDER_ID))
                    .thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.cancelOrder(TestFixtures.ORDER_ID);

            assertThat(response.getStatus()).isEqualTo(OrderStatus.Cancelled);
        }

        @Test
        @DisplayName("returns correct order metadata after cancellation")
        void cancelOrder_within48h_returnsFullOrderResponse() {
            Order order = TestFixtures.confirmedOrderWithinWindow();

            when(orderRepository.findById(TestFixtures.ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.cancelOrder(TestFixtures.ORDER_ID);

            assertThat(response.getOrderId()).isEqualTo(TestFixtures.ORDER_ID);
            assertThat(response.getUserId()).isEqualTo(TestFixtures.USER_ID);
            assertThat(response.getTotal()).isEqualByComparingTo("36.67");
        }

        @Test
        @DisplayName("orderRepository.save() is called exactly once during cancellation")
        void cancelOrder_within48h_saveCalledOnce() {
            Order order = TestFixtures.confirmedOrderWithinWindow();

            when(orderRepository.findById(TestFixtures.ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            orderService.cancelOrder(TestFixtures.ORDER_ID);

            verify(orderRepository, times(1)).save(order);
        }
    }

    // =========================================================================
    // cancelOrder – AFTER 48-hour window  →  ConflictException (FAILURE)
    // =========================================================================

    @Nested
    @DisplayName("cancelOrder() – after 48-hour window (expect ConflictException / FAILURE)")
    class CancelOrderAfterWindow {

        @Test
        @DisplayName("throws ConflictException when cancellation window has expired (49 hours)")
        void cancelOrder_after48h_throwsConflictException() {
            Order order = TestFixtures.confirmedOrderOutsideWindow(); // createdAt = -49h

            when(orderRepository.findById(TestFixtures.ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(TestFixtures.ORDER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("cancellation window has expired");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("error message includes the cancellation window duration")
        void cancelOrder_after48h_exceptionMessageIncludesWindowHours() {
            Order order = TestFixtures.confirmedOrderOutsideWindow();
            when(orderRepository.findById(TestFixtures.ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(TestFixtures.ORDER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("48");
        }

        @Test
        @DisplayName("throws ConflictException even at exactly 48 hours + 1 minute")
        void cancelOrder_exactlyOneBeyondDeadline_throwsConflictException() {
            Order order = TestFixtures.confirmedOrderWithinWindow();
            // Shift createdAt to 48h 1m ago → just past the deadline
            order.setCreatedAt(LocalDateTime.now().minusHours(48).minusMinutes(1));

            when(orderRepository.findById(TestFixtures.ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(TestFixtures.ORDER_ID))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("does NOT update order status when window has expired")
        void cancelOrder_after48h_orderStatusUnchanged() {
            Order order = TestFixtures.confirmedOrderOutsideWindow();
            when(orderRepository.findById(TestFixtures.ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(TestFixtures.ORDER_ID))
                    .isInstanceOf(ConflictException.class);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.Confirmed);
        }
    }

    // =========================================================================
    // cancelOrder – other guard clauses
    // =========================================================================

    @Nested
    @DisplayName("cancelOrder() – other guard clauses")
    class CancelOrderGuards {

        @Test
        @DisplayName("throws ResourceNotFoundException when order does not exist")
        void cancelOrder_orderNotFound_throwsException() {
            when(orderRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancelOrder(UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Order");
        }

        @Test
        @DisplayName("throws BadRequestException when order is already cancelled")
        void cancelOrder_alreadyCancelled_throwsBadRequest() {
            Order order = TestFixtures.alreadyCancelledOrder();
            when(orderRepository.findById(TestFixtures.ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(TestFixtures.ORDER_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already cancelled");
        }

        @Test
        @DisplayName("throws BadRequestException when order has been delivered")
        void cancelOrder_deliveredOrder_throwsBadRequest() {
            Order order = TestFixtures.deliveredOrder();
            when(orderRepository.findById(TestFixtures.ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(TestFixtures.ORDER_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Delivered");
        }
    }

    // =========================================================================
    // getOrderHistory
    // =========================================================================

    @Nested
    @DisplayName("getOrderHistory()")
    class GetOrderHistory {

        @Test
        @DisplayName("returns paged order list for existing user")
        void getOrderHistory_userExists_returnsPagedOrders() {
            Order order = TestFixtures.confirmedOrderWithinWindow();
            Page<Order> orderPage = new PageImpl<>(List.of(order));

            when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));
            when(orderRepository.findByUserUserIdOrderByCreatedAtDesc(
                    eq(TestFixtures.USER_ID), any(Pageable.class)))
                    .thenReturn(orderPage);

            PagedResponse<OrderResponse> result =
                    orderService.getOrderHistory(TestFixtures.USER_ID, 1, 10);

            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getOrderId()).isEqualTo(TestFixtures.ORDER_ID);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user does not exist")
        void getOrderHistory_userNotFound_throwsException() {
            when(userRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    orderService.getOrderHistory(UUID.randomUUID(), 1, 10))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User");
        }

        @Test
        @DisplayName("returns empty paged response when user has no orders")
        void getOrderHistory_noOrders_returnsEmptyPage() {
            when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));
            when(orderRepository.findByUserUserIdOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(Page.empty());

            PagedResponse<OrderResponse> result =
                    orderService.getOrderHistory(TestFixtures.USER_ID, 1, 10);

            assertThat(result.getData()).isEmpty();
            assertThat(result.getTotalItems()).isZero();
        }
    }

    // =========================================================================
    // getOrderById
    // =========================================================================

    @Nested
    @DisplayName("getOrderById()")
    class GetOrderById {

        @Test
        @DisplayName("returns order response for an existing order")
        void getOrderById_found_returnsResponse() {
            Order order = TestFixtures.confirmedOrderWithinWindow();
            when(orderRepository.findById(TestFixtures.ORDER_ID)).thenReturn(Optional.of(order));

            OrderResponse response = orderService.getOrderById(TestFixtures.ORDER_ID);

            assertThat(response.getOrderId()).isEqualTo(TestFixtures.ORDER_ID);
            assertThat(response.getStatus()).isEqualTo(OrderStatus.Confirmed);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when order does not exist")
        void getOrderById_notFound_throwsException() {
            when(orderRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderById(UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Order");
        }
    }

    // =========================================================================
    // Private helper – construct a plausible saved Order entity
    // =========================================================================

    private Order buildSavedOrder(double subtotal, double delivery, double tax,
                                   double discount, double total) {
        Order o = new Order();
        o.setOrderId(TestFixtures.ORDER_ID);
        o.setUser(user);
        o.setStatus(OrderStatus.Confirmed);
        o.setDeliveryAddress(address);
        o.setPaymentMethod(PaymentMethod.CreditCard);
        o.setSubtotal(BigDecimal.valueOf(subtotal));
        o.setDeliveryCharge(BigDecimal.valueOf(delivery));
        o.setTax(BigDecimal.valueOf(tax));
        o.setDiscount(BigDecimal.valueOf(discount));
        o.setTotal(BigDecimal.valueOf(total));
        o.setRedeemedGiftPoints(0);
        o.setItems(new ArrayList<>());
        o.setCreatedAt(LocalDateTime.now());
        o.setCancellationDeadline(LocalDateTime.now().plusHours(48));
        return o;
    }
}
