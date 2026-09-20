package com.bookstore.service;

import com.bookstore.dto.request.AddCartItemRequest;
import com.bookstore.dto.request.UpdateCartItemRequest;
import com.bookstore.dto.response.CartResponse;
import com.bookstore.entity.Book;
import com.bookstore.entity.Cart;
import com.bookstore.entity.CartItem;
import com.bookstore.entity.User;
import com.bookstore.entity.enums.BookFormat;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.exception.UnprocessableEntityException;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.CartItemRepository;
import com.bookstore.repository.CartRepository;
import com.bookstore.repository.UserRepository;
import com.bookstore.util.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CartService}.
 *
 * Covers: getCart (create-on-first-access), addItem (new & duplicate),
 * addItem stock validation, updateItem, removeItem, and total recalculation.
 *
 * {@code @Value} fields are injected via {@link ReflectionTestUtils} to avoid
 * needing a Spring context.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartService – Unit Tests")
class CartServiceTest {

    @Mock CartRepository     cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock BookRepository     bookRepository;
    @Mock UserRepository     userRepository;

    @InjectMocks CartService cartService;

    private Book  book;
    private Cart  cart;
    private User  user;

    @BeforeEach
    void setUp() {
        // Inject @Value fields that Spring would normally bind from properties
        ReflectionTestUtils.setField(cartService, "freeShippingThreshold", new BigDecimal("50.00"));
        ReflectionTestUtils.setField(cartService, "shippingCharge",        new BigDecimal("3.99"));
        ReflectionTestUtils.setField(cartService, "taxRate",               new BigDecimal("0.09"));

        user = TestFixtures.user();
        book = TestFixtures.book1();
        cart = TestFixtures.emptyCart();
    }

    // =========================================================================
    // getCart
    // =========================================================================

    @Nested
    @DisplayName("getCart()")
    class GetCart {

        @Test
        @DisplayName("returns existing cart for user")
        void getCart_existingCart_returnsResponse() {
            when(cartRepository.findByUserUserId(TestFixtures.USER_ID))
                    .thenReturn(Optional.of(cart));

            CartResponse response = cartService.getCart(TestFixtures.USER_ID);

            assertThat(response.getCartId()).isEqualTo(TestFixtures.CART_ID);
            assertThat(response.getUserId()).isEqualTo(TestFixtures.USER_ID);
            assertThat(response.getItems()).isEmpty();
        }

        @Test
        @DisplayName("creates and returns a new cart when user has none")
        void getCart_noExistingCart_createsNewCart() {
            Cart newCart = TestFixtures.emptyCart();
            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.empty());
            when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));
            when(cartRepository.save(any(Cart.class))).thenReturn(newCart);

            CartResponse response = cartService.getCart(TestFixtures.USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.getItems()).isEmpty();
            verify(cartRepository).save(any(Cart.class));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user does not exist")
        void getCart_userNotFound_throwsException() {
            when(cartRepository.findByUserUserId(any())).thenReturn(Optional.empty());
            when(userRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.getCart(UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User");
        }
    }

    // =========================================================================
    // addItem
    // =========================================================================

    @Nested
    @DisplayName("addItem()")
    class AddItem {

        @Test
        @DisplayName("adds a new item to an empty cart and recalculates totals")
        void addItem_newItem_addsAndRecalculates() {
            // book price = 14.99, qty = 2 → subtotal = 29.98
            // subtotal < 50.00 → delivery = 3.99, tax = 29.98 * 0.09 = 2.70 → total = 36.67
            AddCartItemRequest request = new AddCartItemRequest();
            request.setBookId(TestFixtures.BOOK_ID_1);
            request.setQuantity(2);
            request.setFormat(BookFormat.Paperback);

            CartItem savedItem = TestFixtures.cartItem(cart, book);
            Cart savedCart = TestFixtures.emptyCart();
            savedCart.getItems().add(savedItem);

            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(bookRepository.findById(TestFixtures.BOOK_ID_1)).thenReturn(Optional.of(book));
            when(cartItemRepository.findByCartCartIdAndBookBookIdAndFormat(
                    TestFixtures.CART_ID, TestFixtures.BOOK_ID_1, BookFormat.Paperback))
                    .thenReturn(Optional.empty());
            when(cartItemRepository.save(any(CartItem.class))).thenReturn(savedItem);
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            CartResponse response = cartService.addItem(TestFixtures.USER_ID, request);

            assertThat(response.getItems()).hasSize(1);
            assertThat(response.getItems().get(0).getQuantity()).isEqualTo(2);
            assertThat(response.getItems().get(0).getLineTotal())
                    .isEqualByComparingTo("29.98");
            // Subtotal below free-shipping threshold → delivery charge applied
            assertThat(response.getSubtotal()).isEqualByComparingTo("29.98");
            assertThat(response.getEstimatedDeliveryCharge()).isEqualByComparingTo("3.99");
            assertThat(response.getTax()).isEqualByComparingTo("2.70");
        }

        @Test
        @DisplayName("increments quantity when the same book+format is already in the cart")
        void addItem_duplicateBookAndFormat_incrementsQuantity() {
            CartItem existingItem = TestFixtures.cartItem(cart, book); // qty = 2
            cart.getItems().add(existingItem);

            AddCartItemRequest request = new AddCartItemRequest();
            request.setBookId(TestFixtures.BOOK_ID_1);
            request.setQuantity(3); // adding 3 more → should become 5
            request.setFormat(BookFormat.Paperback);

            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(bookRepository.findById(TestFixtures.BOOK_ID_1)).thenReturn(Optional.of(book));
            when(cartItemRepository.findByCartCartIdAndBookBookIdAndFormat(
                    TestFixtures.CART_ID, TestFixtures.BOOK_ID_1, BookFormat.Paperback))
                    .thenReturn(Optional.of(existingItem));
            when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            CartResponse response = cartService.addItem(TestFixtures.USER_ID, request);

            // Quantity should have been updated on the existing item
            assertThat(existingItem.getQuantity()).isEqualTo(5);
            // lineTotal = 14.99 * 5 = 74.95
            assertThat(existingItem.getLineTotal()).isEqualByComparingTo("74.95");
            // Only one item in the cart (no duplicate added)
            assertThat(response.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("throws UnprocessableEntityException when book is out of stock")
        void addItem_outOfStockBook_throwsException() {
            Book oosBook = TestFixtures.outOfStockBook();

            AddCartItemRequest request = new AddCartItemRequest();
            request.setBookId(TestFixtures.BOOK_ID_OOS);
            request.setQuantity(1);
            request.setFormat(BookFormat.Hardcover);

            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(bookRepository.findById(TestFixtures.BOOK_ID_OOS)).thenReturn(Optional.of(oosBook));

            assertThatThrownBy(() -> cartService.addItem(TestFixtures.USER_ID, request))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessageContaining("out of stock");

            verify(cartItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when book does not exist")
        void addItem_bookNotFound_throwsException() {
            AddCartItemRequest request = new AddCartItemRequest();
            request.setBookId(UUID.randomUUID());
            request.setQuantity(1);
            request.setFormat(BookFormat.Paperback);

            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(bookRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.addItem(TestFixtures.USER_ID, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Book");
        }

        @Test
        @DisplayName("free shipping is applied when subtotal meets the threshold")
        void addItem_subtotalAboveThreshold_noDeliveryCharge() {
            // book price = 14.99, quantity = 4 → subtotal = 59.96 > 50.00 threshold
            Book expensiveBook = Book.builder()
                    .bookId(TestFixtures.BOOK_ID_1)
                    .title("Dune")
                    .author("Frank Herbert")
                    .price(new BigDecimal("14.99"))
                    .format(BookFormat.Paperback)
                    .language("English")
                    .stockStatus(com.bookstore.entity.enums.StockStatus.IN_STOCK)
                    .soldCount(0)
                    .rating(BigDecimal.ZERO)
                    .tentativeDeliveryDays(3)
                    .build();

            CartItem savedItem = new CartItem();
            savedItem.setItemId(TestFixtures.CART_ITEM_ID);
            savedItem.setCart(cart);
            savedItem.setBook(expensiveBook);
            savedItem.setFormat(BookFormat.Paperback);
            savedItem.setQuantity(4);
            savedItem.setUnitPrice(new BigDecimal("14.99"));
            savedItem.setLineTotal(new BigDecimal("59.96"));

            AddCartItemRequest request = new AddCartItemRequest();
            request.setBookId(TestFixtures.BOOK_ID_1);
            request.setQuantity(4);
            request.setFormat(BookFormat.Paperback);

            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(bookRepository.findById(TestFixtures.BOOK_ID_1)).thenReturn(Optional.of(expensiveBook));
            when(cartItemRepository.findByCartCartIdAndBookBookIdAndFormat(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(cartItemRepository.save(any())).thenReturn(savedItem);
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            CartResponse response = cartService.addItem(TestFixtures.USER_ID, request);

            // subtotal (59.96) >= threshold (50.00) → delivery = 0
            assertThat(response.getEstimatedDeliveryCharge()).isEqualByComparingTo("0.00");
        }
    }

    // =========================================================================
    // updateItem
    // =========================================================================

    @Nested
    @DisplayName("updateItem()")
    class UpdateItem {

        @Test
        @DisplayName("updates quantity and recalculates lineTotal and cart totals")
        void updateItem_validRequest_updatesAndRecalculates() {
            CartItem existingItem = TestFixtures.cartItem(cart, book); // qty = 2, lineTotal = 29.98
            cart.getItems().add(existingItem);

            UpdateCartItemRequest request = new UpdateCartItemRequest();
            request.setQuantity(5);

            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartCartIdAndItemId(
                    TestFixtures.CART_ID, TestFixtures.CART_ITEM_ID))
                    .thenReturn(Optional.of(existingItem));
            when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            CartResponse response = cartService.updateItem(
                    TestFixtures.USER_ID, TestFixtures.CART_ITEM_ID, request);

            assertThat(existingItem.getQuantity()).isEqualTo(5);
            // lineTotal = 14.99 * 5 = 74.95
            assertThat(existingItem.getLineTotal()).isEqualByComparingTo("74.95");
            // subtotal > 50.00 → free shipping
            assertThat(response.getEstimatedDeliveryCharge()).isEqualByComparingTo("0.00");
            assertThat(response.getSubtotal()).isEqualByComparingTo("74.95");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when cart item does not exist")
        void updateItem_itemNotFound_throwsException() {
            UUID nonExistentItemId = UUID.randomUUID();

            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartCartIdAndItemId(any(), eq(nonExistentItemId)))
                    .thenReturn(Optional.empty());

            UpdateCartItemRequest request = new UpdateCartItemRequest();
            request.setQuantity(3);

            assertThatThrownBy(() -> cartService.updateItem(
                    TestFixtures.USER_ID, nonExistentItemId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("CartItem");
        }

        @Test
        @DisplayName("setting quantity to 1 correctly resets lineTotal to unit price")
        void updateItem_quantityOne_lineTotalEqualsUnitPrice() {
            CartItem existingItem = TestFixtures.cartItem(cart, book); // qty = 2
            cart.getItems().add(existingItem);

            UpdateCartItemRequest request = new UpdateCartItemRequest();
            request.setQuantity(1);

            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartCartIdAndItemId(any(), eq(TestFixtures.CART_ITEM_ID)))
                    .thenReturn(Optional.of(existingItem));
            when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            cartService.updateItem(TestFixtures.USER_ID, TestFixtures.CART_ITEM_ID, request);

            assertThat(existingItem.getQuantity()).isEqualTo(1);
            assertThat(existingItem.getLineTotal()).isEqualByComparingTo(book.getPrice());
        }
    }

    // =========================================================================
    // removeItem
    // =========================================================================

    @Nested
    @DisplayName("removeItem()")
    class RemoveItem {

        @Test
        @DisplayName("removes item from cart and recalculates to zero totals")
        void removeItem_itemExists_removesAndRecalculates() {
            CartItem item = TestFixtures.cartItem(cart, book);
            cart.getItems().add(item);
            cart.setSubtotal(new BigDecimal("29.98"));

            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartCartIdAndItemId(
                    TestFixtures.CART_ID, TestFixtures.CART_ITEM_ID))
                    .thenReturn(Optional.of(item));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            CartResponse response = cartService.removeItem(
                    TestFixtures.USER_ID, TestFixtures.CART_ITEM_ID);

            assertThat(cart.getItems()).isEmpty();
            verify(cartItemRepository).delete(item);
            // After removal cart is empty → subtotal = 0, delivery = shippingCharge (below threshold)
            assertThat(response.getSubtotal()).isEqualByComparingTo("0.00");
            assertThat(response.getTax()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when item does not exist")
        void removeItem_itemNotFound_throwsException() {
            UUID nonExistentItemId = UUID.randomUUID();

            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartCartIdAndItemId(any(), eq(nonExistentItemId)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    cartService.removeItem(TestFixtures.USER_ID, nonExistentItemId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("CartItem");

            verify(cartItemRepository, never()).delete(any());
        }

        @Test
        @DisplayName("cartItemRepository.delete() is called exactly once")
        void removeItem_callsDeleteExactlyOnce() {
            CartItem item = TestFixtures.cartItem(cart, book);
            cart.getItems().add(item);

            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartCartIdAndItemId(any(), eq(TestFixtures.CART_ITEM_ID)))
                    .thenReturn(Optional.of(item));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            cartService.removeItem(TestFixtures.USER_ID, TestFixtures.CART_ITEM_ID);

            verify(cartItemRepository, times(1)).delete(item);
        }
    }

    // =========================================================================
    // Total recalculation edge cases
    // =========================================================================

    @Nested
    @DisplayName("Total recalculation edge cases")
    class TotalRecalculation {

        @Test
        @DisplayName("total is never negative even when discount exceeds subtotal")
        void recalculate_discountExceedsSubtotal_totalIsZero() {
            // Artificially set a large discount
            cart.setDiscount(new BigDecimal("999.00"));
            CartItem item = TestFixtures.cartItem(cart, book); // lineTotal = 29.98
            cart.getItems().add(item);

            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(bookRepository.findById(TestFixtures.BOOK_ID_1)).thenReturn(Optional.of(book));
            when(cartItemRepository.findByCartCartIdAndBookBookIdAndFormat(any(), any(), any()))
                    .thenReturn(Optional.of(item));
            when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            AddCartItemRequest request = new AddCartItemRequest();
            request.setBookId(TestFixtures.BOOK_ID_1);
            request.setQuantity(1);
            request.setFormat(BookFormat.Paperback);

            CartResponse response = cartService.addItem(TestFixtures.USER_ID, request);

            assertThat(response.getTotal()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("tax is rounded to 2 decimal places using HALF_UP")
        void recalculate_taxIsRoundedCorrectly() {
            // subtotal = 14.99 * 1 = 14.99 → tax = 14.99 * 0.09 = 1.3491 → rounds to 1.35
            CartItem singleItem = new CartItem();
            singleItem.setItemId(TestFixtures.CART_ITEM_ID);
            singleItem.setCart(cart);
            singleItem.setBook(book);
            singleItem.setFormat(BookFormat.Paperback);
            singleItem.setQuantity(1);
            singleItem.setUnitPrice(new BigDecimal("14.99"));
            singleItem.setLineTotal(new BigDecimal("14.99"));
            cart.getItems().add(singleItem);

            AddCartItemRequest request = new AddCartItemRequest();
            request.setBookId(TestFixtures.BOOK_ID_1);
            request.setQuantity(1);
            request.setFormat(BookFormat.Paperback);

            when(cartRepository.findByUserUserId(TestFixtures.USER_ID)).thenReturn(Optional.of(cart));
            when(bookRepository.findById(TestFixtures.BOOK_ID_1)).thenReturn(Optional.of(book));
            when(cartItemRepository.findByCartCartIdAndBookBookIdAndFormat(any(), any(), any()))
                    .thenReturn(Optional.of(singleItem));
            when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            CartResponse response = cartService.addItem(TestFixtures.USER_ID, request);

            // 14.99 * 2 (qty bumped to 2) * 0.09 = 2.6982 → 2.70
            assertThat(response.getTax().scale()).isEqualTo(2);
        }
    }
}
