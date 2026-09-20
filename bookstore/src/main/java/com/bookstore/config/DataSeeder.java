package com.bookstore.config;

import com.bookstore.entity.Address;
import com.bookstore.entity.Book;
import com.bookstore.entity.Category;
import com.bookstore.entity.User;
import com.bookstore.entity.enums.BookFormat;
import com.bookstore.entity.enums.StockStatus;
import com.bookstore.repository.AddressRepository;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.CategoryRepository;
import com.bookstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Seeds the database with sample categories, books, and a demo user on
 * every application startup.  Each block is idempotent — existing records
 * are skipped, so re-runs never produce duplicates.
 *
 * Active on all profiles.  Disable by adding
 *   app.seed.enabled=false
 * or by excluding this bean in tests via @MockBean / @Profile.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    // ── Delivery date shown on the wireframe (Mon 21 Jul 2025 = day 5 from today)
    private static final int DELIVERY_DAYS = 5;

    // ── Demo user credentials
    private static final String DEMO_EMAIL    = "demo@bookstore.com";
    private static final String DEMO_PASSWORD = "Demo@1234";
    private static final String DEMO_NAME     = "Demo User";
    private static final String DEMO_PHONE    = "+91-9876543210";

    private final CategoryRepository categoryRepository;
    private final BookRepository     bookRepository;
    private final UserRepository     userRepository;
    private final AddressRepository  addressRepository;
    private final PasswordEncoder    passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("▶ DataSeeder starting …");
        Map<String, Category> categories = seedCategories();
        seedBooks(categories);
        seedDemoUser();
        log.info("✔ DataSeeder finished.");
    }

    // ─────────────────────────────────────────────────────────────
    // 1. CATEGORIES
    // ─────────────────────────────────────────────────────────────

    private Map<String, Category> seedCategories() {
        List<String[]> specs = List.of(
                new String[]{"Self-Help",      "self-help"},
                new String[]{"Science Fiction", "science-fiction"},
                new String[]{"Romance",         "romance"},
                new String[]{"Mystery",         "mystery"},
                new String[]{"Fantasy",         "fantasy"},
                new String[]{"Memoir",          "memoir"}
        );

        for (String[] spec : specs) {
            String name = spec[0];
            String slug = spec[1];
            categoryRepository.findByNameIgnoreCase(name).orElseGet(() -> {
                Category c = Category.builder().name(name).slug(slug).build();
                log.debug("  Seeding category: {}", name);
                return categoryRepository.save(c);
            });
        }

        // Return a name → Category lookup map for book wiring
        return categoryRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        c -> c.getName().toLowerCase(),
                        Function.identity()
                ));
    }

    // ─────────────────────────────────────────────────────────────
    // 2. BOOKS
    // ─────────────────────────────────────────────────────────────

    private void seedBooks(Map<String, Category> cats) {
        List<BookSpec> specs = List.of(
            new BookSpec(
                "The Art of Focus",
                "Arjun Patel",
                "A transformative guide to harnessing deep focus in a distracted world.",
                "978-0-000-00001-1",
                new BigDecimal("399.00"),
                BookFormat.Paperback,
                "self-help",
                new BigDecimal("4.8"),
                145,
                DELIVERY_DAYS,
                LocalDate.of(2023, 3, 15)
            ),
            new BookSpec(
                "The Art of Learning",
                "Raj Patel",
                "Unlock your learning potential with proven strategies for mastery.",
                "978-0-000-00002-8",
                new BigDecimal("259.00"),
                BookFormat.Paperback,
                "self-help",
                new BigDecimal("4.5"),
                98,
                DELIVERY_DAYS,
                LocalDate.of(2022, 7, 10)
            ),
            new BookSpec(
                "The Path to Success",
                "James Wright",
                "Step-by-step principles that have guided thousands to achieve their goals.",
                "978-0-000-00003-5",
                new BigDecimal("359.00"),
                BookFormat.Paperback,
                "self-help",
                new BigDecimal("4.7"),
                210,
                DELIVERY_DAYS,
                LocalDate.of(2021, 11, 1)
            ),
            new BookSpec(
                "The Midnight Hour",
                "James Adams",
                "A gripping mystery set in a fog-covered city where nothing is as it seems.",
                "978-0-000-00004-2",
                new BigDecimal("299.00"),
                BookFormat.Paperback,
                "mystery",
                new BigDecimal("4.6"),
                320,
                DELIVERY_DAYS,
                LocalDate.of(2023, 1, 20)
            ),
            new BookSpec(
                "Beneath the Stars",
                "Jessica Martin",
                "An epic romance spanning decades, continents, and two stubborn hearts.",
                "978-0-000-00005-9",
                new BigDecimal("499.00"),
                BookFormat.Hardcover,
                "romance",
                new BigDecimal("4.9"),
                450,
                DELIVERY_DAYS,
                LocalDate.of(2024, 2, 14)
            ),
            new BookSpec(
                "The Final Frontier",
                "Laura Mitchell",
                "Humanity's first faster-than-light voyage pushes the crew beyond their limits.",
                "978-0-000-00006-6",
                new BigDecimal("359.00"),
                BookFormat.Paperback,
                "science fiction",
                new BigDecimal("4.4"),
                80,
                DELIVERY_DAYS,
                LocalDate.of(2024, 5, 5)
            ),
            new BookSpec(
                "The Joy of Minimalism",
                "Daniel Reed",
                "Live lighter, think clearer — a practical roadmap to minimalist living.",
                "978-0-000-00007-3",
                new BigDecimal("149.00"),
                BookFormat.Paperback,
                "self-help",
                new BigDecimal("5.0"),
                145,
                DELIVERY_DAYS,
                LocalDate.of(2023, 9, 8)
            )
        );

        for (BookSpec s : specs) {
            if (bookRepository.findAll()
                    .stream()
                    .anyMatch(b -> b.getIsbn() != null && b.getIsbn().equals(s.isbn()))) {
                log.debug("  Skipping existing book: {}", s.title());
                continue;
            }

            Category category = cats.get(s.categoryKey());
            if (category == null) {
                log.warn("  Category not found for key '{}', skipping book '{}'", s.categoryKey(), s.title());
                continue;
            }

            Book book = Book.builder()
                    .title(s.title())
                    .author(s.author())
                    .description(s.description())
                    .isbn(s.isbn())
                    .price(s.price())
                    .format(s.format())
                    .category(category)
                    .rating(s.rating())
                    .soldCount(s.soldCount())
                    .tentativeDeliveryDays(s.deliveryDays())
                    .publishedDate(s.publishedDate())
                    .stockStatus(StockStatus.IN_STOCK)
                    .language("English")
                    .build();

            bookRepository.save(book);
            log.debug("  Seeded book: {}", s.title());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. DEMO USER
    // ─────────────────────────────────────────────────────────────

    private void seedDemoUser() {
        if (userRepository.existsByEmail(DEMO_EMAIL)) {
            log.debug("  Demo user already exists, skipping.");
            return;
        }

        User user = User.builder()
                .fullName(DEMO_NAME)
                .email(DEMO_EMAIL)
                .password(passwordEncoder.encode(DEMO_PASSWORD))
                .phone(DEMO_PHONE)
                .rewardPoints(new BigDecimal("100.00"))
                .build();

        User saved = userRepository.save(user);

        Address address = Address.builder()
                .user(saved)
                .firstLine("42 Maple Street")
                .secondLine("Apartment 7B")
                .city("Mumbai")
                .state("Maharashtra")
                .country("India")
                .pinCode("400001")
                .isDefault(true)
                .build();

        addressRepository.save(address);
        log.info("  Demo user seeded  →  email: {}  password: {}", DEMO_EMAIL, DEMO_PASSWORD);
    }

    // ─────────────────────────────────────────────────────────────
    // Internal record — keeps seedBooks() readable
    // ─────────────────────────────────────────────────────────────

    private record BookSpec(
            String title,
            String author,
            String description,
            String isbn,
            BigDecimal price,
            BookFormat format,
            String categoryKey,
            BigDecimal rating,
            int soldCount,
            int deliveryDays,
            LocalDate publishedDate
    ) {}
}
