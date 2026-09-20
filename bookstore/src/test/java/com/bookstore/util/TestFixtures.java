package com.bookstore.util;

import com.bookstore.entity.*;
import com.bookstore.entity.enums.BookFormat;
import com.bookstore.entity.enums.OrderStatus;
import com.bookstore.entity.enums.PaymentMethod;
import com.bookstore.entity.enums.StockStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Centralized test fixture factory — all test data lives here so every test
 * class is kept free of repetitive builder boilerplate.
 */
public final class TestFixtures {

    private TestFixtures() {}

    // -----------------------------------------------------------------------
    // Fixed UUIDs – stable across tests so assertions can reference them
    // -----------------------------------------------------------------------
    public static final UUID USER_ID       = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    public static final UUID ADDRESS_ID    = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    public static final UUID BOOK_ID_1     = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    public static final UUID BOOK_ID_2     = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    public static final UUID BOOK_ID_OOS   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000099");
    public static final UUID CATEGORY_ID   = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    public static final UUID CART_ID       = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
    public static final UUID CART_ITEM_ID  = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    public static final UUID ORDER_ID      = UUID.fromString("ffffffff-0000-0000-0000-000000000001");

    // -----------------------------------------------------------------------
    // Category
    // -----------------------------------------------------------------------
    public static Category category() {
        Category c = new Category();
        c.setCategoryId(CATEGORY_ID);
        c.setName("Science Fiction");
        c.setSlug("science-fiction");
        return c;
    }

    // -----------------------------------------------------------------------
    // Book helpers
    // -----------------------------------------------------------------------
    public static Book book1() {
        return Book.builder()
                .bookId(BOOK_ID_1)
                .title("Dune")
                .author("Frank Herbert")
                .description("A science-fiction epic set on the desert planet Arrakis.")
                .isbn("978-0441013593")
                .price(new BigDecimal("14.99"))
                .publisher("Ace Books")
                .format(BookFormat.Paperback)
                .language("English")
                .pageCount(412)
                .publishedDate(LocalDate.of(1965, 8, 1))
                .coverImageUrl("https://cdn.bookstore.com/covers/dune.jpg")
                .rating(new BigDecimal("4.80"))
                .soldCount(50000)
                .stockStatus(StockStatus.IN_STOCK)
                .category(category())
                .tentativeDeliveryDays(3)
                .createdAt(LocalDateTime.now().minusDays(30))
                .build();
    }

    public static Book book2() {
        return Book.builder()
                .bookId(BOOK_ID_2)
                .title("Foundation")
                .author("Isaac Asimov")
                .description("The fall and rise of galactic civilization.")
                .isbn("978-0553293357")
                .price(new BigDecimal("12.99"))
                .publisher("Spectra")
                .format(BookFormat.Paperback)
                .language("English")
                .pageCount(296)
                .publishedDate(LocalDate.of(1951, 6, 1))
                .coverImageUrl("https://cdn.bookstore.com/covers/foundation.jpg")
                .rating(new BigDecimal("4.70"))
                .soldCount(45000)
                .stockStatus(StockStatus.IN_STOCK)
                .category(category())
                .tentativeDeliveryDays(3)
                .createdAt(LocalDateTime.now().minusDays(20))
                .build();
    }

    public static Book outOfStockBook() {
        return Book.builder()
                .bookId(BOOK_ID_OOS)
                .title("Sold Out Novel")
                .author("Unknown Author")
                .price(new BigDecimal("9.99"))
                .format(BookFormat.Hardcover)
                .language("English")
                .stockStatus(StockStatus.OUT_OF_STOCK)
                .category(category())
                .tentativeDeliveryDays(5)
                .soldCount(0)
                .rating(BigDecimal.ZERO)
                .build();
    }

    // -----------------------------------------------------------------------
    // User & Address
    // -----------------------------------------------------------------------
    public static User user() {
        User u = new User();
        u.setUserId(USER_ID);
        u.setFullName("Jane Doe");
        u.setEmail("jane.doe@example.com");
        u.setPassword("$2a$10$hashedpassword");
        u.setPhone("+44-7911-123456");
        u.setAddresses(new ArrayList<>());
        return u;
    }

    public static Address address() {
        Address a = new Address();
        a.setAddressId(ADDRESS_ID);
        a.setUser(user());
        a.setFirstLine("42 Baker Street");
        a.setSecondLine("Apartment 3B");
        a.setCity("London");
        a.setState("England");
        a.setCountry("United Kingdom");
        a.setPinCode("NW1 6XE");
        a.setDefault(true);
        return a;
    }

    // -----------------------------------------------------------------------
    // Cart helpers
    // -----------------------------------------------------------------------
    public static CartItem cartItem(Cart cart, Book book) {
        CartItem item = new CartItem();
        item.setItemId(CART_ITEM_ID);
        item.setCart(cart);
        item.setBook(book);
        item.setFormat(BookFormat.Paperback);
        item.setQuantity(2);
        item.setUnitPrice(book.getPrice());
        item.setLineTotal(book.getPrice().multiply(BigDecimal.valueOf(2)));
        return item;
    }

    public static Cart emptyCart() {
        Cart cart = new Cart();
        cart.setCartId(CART_ID);
        cart.setUser(user());
        cart.setItems(new ArrayList<>());
        cart.setSubtotal(BigDecimal.ZERO);
        cart.setDeliveryCharge(BigDecimal.ZERO);
        cart.setTax(BigDecimal.ZERO);
        cart.setDiscount(BigDecimal.ZERO);
        cart.setTotal(BigDecimal.ZERO);
        return cart;
    }

    public static Cart cartWithOneItem() {
        Cart cart = emptyCart();
        CartItem item = cartItem(cart, book1());
        cart.getItems().add(item);
        // Subtotal = 14.99 * 2 = 29.98
        cart.setSubtotal(new BigDecimal("29.98"));
        cart.setDeliveryCharge(new BigDecimal("3.99"));
        cart.setTax(new BigDecimal("2.70"));
        cart.setTotal(new BigDecimal("36.67"));
        return cart;
    }

    // -----------------------------------------------------------------------
    // Order helpers
    // -----------------------------------------------------------------------

    /** Order created NOW – cancellation is allowed (within 48 h). */
    public static Order confirmedOrderWithinWindow() {
        Order o = new Order();
        o.setOrderId(ORDER_ID);
        o.setUser(user());
        o.setStatus(OrderStatus.Confirmed);
        o.setDeliveryAddress(address());
        o.setPaymentMethod(PaymentMethod.CreditCard);
        o.setSubtotal(new BigDecimal("29.98"));
        o.setDeliveryCharge(new BigDecimal("3.99"));
        o.setTax(new BigDecimal("2.70"));
        o.setDiscount(BigDecimal.ZERO);
        o.setTotal(new BigDecimal("36.67"));
        o.setRedeemedGiftPoints(0);
        o.setItems(new ArrayList<>());
        // Created just now → well within 48-hour window
        o.setCreatedAt(LocalDateTime.now().minusHours(1));
        o.setCancellationDeadline(LocalDateTime.now().plusHours(47));
        return o;
    }

    /** Order created 49 hours ago – cancellation window has expired. */
    public static Order confirmedOrderOutsideWindow() {
        Order o = confirmedOrderWithinWindow();
        o.setCreatedAt(LocalDateTime.now().minusHours(49));
        o.setCancellationDeadline(LocalDateTime.now().minusHours(1));
        return o;
    }

    public static Order alreadyCancelledOrder() {
        Order o = confirmedOrderWithinWindow();
        o.setStatus(OrderStatus.Cancelled);
        return o;
    }

    public static Order deliveredOrder() {
        Order o = confirmedOrderWithinWindow();
        o.setStatus(OrderStatus.Delivered);
        return o;
    }

    /** Cart with one in-stock item, ready for checkout. */
    public static Cart checkoutReadyCart() {
        Cart cart = emptyCart();
        Book book = book1();
        CartItem item = new CartItem();
        item.setItemId(CART_ITEM_ID);
        item.setCart(cart);
        item.setBook(book);
        item.setFormat(BookFormat.Paperback);
        item.setQuantity(2);
        item.setUnitPrice(book.getPrice());
        item.setLineTotal(book.getPrice().multiply(BigDecimal.valueOf(2))); // 29.98
        List<CartItem> items = new ArrayList<>();
        items.add(item);
        cart.setItems(items);
        return cart;
    }
}
