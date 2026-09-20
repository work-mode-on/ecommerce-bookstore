package com.bookstore.service;

import com.bookstore.dto.response.BookDetailResponse;
import com.bookstore.dto.response.BookSummaryResponse;
import com.bookstore.dto.response.CategoryResponse;
import com.bookstore.dto.response.PagedResponse;
import com.bookstore.entity.Book;
import com.bookstore.entity.Category;
import com.bookstore.entity.enums.BookFormat;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.CategoryRepository;
import com.bookstore.util.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CatalogService}.
 *
 * Verifies: keyword search, category filtering, price-range filtering,
 * getBookById (found / not-found), getRelatedBooks, getBestsellers,
 * getNewLaunches, and listCategories.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogService – Unit Tests")
class CatalogServiceTest {

    @Mock BookRepository bookRepository;
    @Mock CategoryRepository categoryRepository;

    @InjectMocks CatalogService catalogService;

    private Book book1;
    private Book book2;
    private Category category;

    @BeforeEach
    void setUp() {
        book1    = TestFixtures.book1();
        book2    = TestFixtures.book2();
        category = TestFixtures.category();
    }

    // =========================================================================
    // listBooks – dispatching logic
    // =========================================================================

    @Nested
    @DisplayName("listBooks()")
    class ListBooks {

        @Test
        @DisplayName("returns all books when no filter is provided")
        void listBooks_noFilter_returnsAll() {
            Page<Book> page = new PageImpl<>(List.of(book1, book2));
            when(bookRepository.findAll(any(Pageable.class))).thenReturn(page);

            PagedResponse<BookSummaryResponse> result =
                    catalogService.listBooks(null, null, null, null, null, null, null, 1, 10);

            assertThat(result.getData()).hasSize(2);
            assertThat(result.getTotalItems()).isEqualTo(2);
            assertThat(result.getCurrentPage()).isEqualTo(1);
            verify(bookRepository).findAll(any(Pageable.class));
            verifyNoMoreInteractions(bookRepository);
        }

        @Test
        @DisplayName("delegates to searchByKeyword when 'search' param is set")
        void listBooks_withSearch_callsKeywordSearch() {
            Page<Book> page = new PageImpl<>(List.of(book1));
            when(bookRepository.searchByKeyword(eq("dune"), any(Pageable.class))).thenReturn(page);

            PagedResponse<BookSummaryResponse> result =
                    catalogService.listBooks(null, "dune", null, null, null, null, null, 1, 10);

            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getTitle()).isEqualTo("Dune");
            verify(bookRepository).searchByKeyword(eq("dune"), any(Pageable.class));
            verify(bookRepository, never()).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("search is prioritised over category when both are supplied")
        void listBooks_searchTakesPriorityOverCategory() {
            Page<Book> page = new PageImpl<>(List.of(book1));
            when(bookRepository.searchByKeyword(anyString(), any(Pageable.class))).thenReturn(page);

            catalogService.listBooks("science-fiction", "dune", null, null, null, null, null, 1, 10);

            verify(bookRepository).searchByKeyword(anyString(), any(Pageable.class));
            verify(bookRepository, never()).findByCategorySlug(anyString(), any(Pageable.class));
        }

        @Test
        @DisplayName("delegates to findByCategorySlug when 'category' slug is set")
        void listBooks_withCategory_callsCategoryFilter() {
            Page<Book> page = new PageImpl<>(List.of(book1, book2));
            when(bookRepository.findByCategorySlug(eq("science-fiction"), any(Pageable.class)))
                    .thenReturn(page);

            PagedResponse<BookSummaryResponse> result =
                    catalogService.listBooks("science-fiction", null, null, null, null, null, null, 1, 10);

            assertThat(result.getData()).hasSize(2);
            verify(bookRepository).findByCategorySlug(eq("science-fiction"), any(Pageable.class));
        }

        @Test
        @DisplayName("delegates to findByLanguageIgnoreCase when 'language' is set")
        void listBooks_withLanguage_callsLanguageFilter() {
            Page<Book> page = new PageImpl<>(List.of(book1));
            when(bookRepository.findByLanguageIgnoreCase(eq("English"), any(Pageable.class)))
                    .thenReturn(page);

            catalogService.listBooks(null, null, "English", null, null, null, null, 1, 10);

            verify(bookRepository).findByLanguageIgnoreCase(eq("English"), any(Pageable.class));
        }

        @Test
        @DisplayName("delegates to findByFormat when 'format' is set")
        void listBooks_withFormat_callsFormatFilter() {
            Page<Book> page = new PageImpl<>(List.of(book1));
            when(bookRepository.findByFormat(eq(BookFormat.Paperback), any(Pageable.class)))
                    .thenReturn(page);

            catalogService.listBooks(null, null, null, BookFormat.Paperback, null, null, null, 1, 10);

            verify(bookRepository).findByFormat(eq(BookFormat.Paperback), any(Pageable.class));
        }

        @Test
        @DisplayName("delegates to findByPriceBetween when both minPrice and maxPrice are set")
        void listBooks_withPriceRange_callsPriceFilter() {
            Page<Book> page = new PageImpl<>(List.of(book1, book2));
            when(bookRepository.findByPriceBetween(
                    eq(new BigDecimal("5.00")), eq(new BigDecimal("20.00")), any(Pageable.class)))
                    .thenReturn(page);

            catalogService.listBooks(null, null, null, null,
                    new BigDecimal("5.00"), new BigDecimal("20.00"), null, 1, 10);

            verify(bookRepository).findByPriceBetween(
                    eq(new BigDecimal("5.00")), eq(new BigDecimal("20.00")), any(Pageable.class));
        }

        @Test
        @DisplayName("returns empty list when no books match the keyword search")
        void listBooks_keywordNoMatch_returnsEmpty() {
            when(bookRepository.searchByKeyword(anyString(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            PagedResponse<BookSummaryResponse> result =
                    catalogService.listBooks(null, "zzz-nonexistent", null, null, null, null, null, 1, 10);

            assertThat(result.getData()).isEmpty();
            assertThat(result.getTotalItems()).isZero();
        }

        @Test
        @DisplayName("sortBy 'price_asc' builds ascending price sort")
        void listBooks_sortByPriceAsc_appliesCorrectSort() {
            Page<Book> page = new PageImpl<>(List.of(book2, book1),
                    PageRequest.of(0, 10, Sort.by("price").ascending()), 2);
            when(bookRepository.findAll(any(Pageable.class))).thenReturn(page);

            PagedResponse<BookSummaryResponse> result =
                    catalogService.listBooks(null, null, null, null, null, null, "price_asc", 1, 10);

            assertThat(result.getData().get(0).getTitle()).isEqualTo("Foundation"); // cheaper first
        }

        @Test
        @DisplayName("pagination metadata is correctly propagated")
        void listBooks_paginationMetadata_isCorrect() {
            List<Book> books = List.of(book1, book2);
            Pageable pageable = PageRequest.of(1, 2);
            Page<Book> page = new PageImpl<>(books, pageable, 10);
            when(bookRepository.findAll(any(Pageable.class))).thenReturn(page);

            PagedResponse<BookSummaryResponse> result =
                    catalogService.listBooks(null, null, null, null, null, null, null, 2, 2);

            assertThat(result.getCurrentPage()).isEqualTo(2);
            assertThat(result.getPageSize()).isEqualTo(2);
            assertThat(result.getTotalItems()).isEqualTo(10);
            assertThat(result.getTotalPages()).isEqualTo(5);
        }
    }

    // =========================================================================
    // getBookById
    // =========================================================================

    @Nested
    @DisplayName("getBookById()")
    class GetBookById {

        @Test
        @DisplayName("returns full BookDetailResponse for an existing book")
        void getBookById_found_returnsDetail() {
            when(bookRepository.findById(TestFixtures.BOOK_ID_1)).thenReturn(Optional.of(book1));

            BookDetailResponse response = catalogService.getBookById(TestFixtures.BOOK_ID_1);

            assertThat(response.getBookId()).isEqualTo(TestFixtures.BOOK_ID_1);
            assertThat(response.getTitle()).isEqualTo("Dune");
            assertThat(response.getAuthor()).isEqualTo("Frank Herbert");
            assertThat(response.getIsbn()).isEqualTo("978-0441013593");
            assertThat(response.getPrice()).isEqualByComparingTo("14.99");
            assertThat(response.getPublisher()).isEqualTo("Ace Books");
            assertThat(response.getFormat()).isEqualTo(BookFormat.Paperback);
            assertThat(response.getCategoryName()).isEqualTo("Science Fiction");
            // tentativeDeliveryDate = today + tentativeDeliveryDays (3)
            assertThat(response.getTentativeDeliveryDate())
                    .isAfterOrEqualTo(java.time.LocalDate.now().plusDays(2));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when book does not exist")
        void getBookById_notFound_throwsException() {
            UUID unknownId = UUID.randomUUID();
            when(bookRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> catalogService.getBookById(unknownId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Book");
        }

        @Test
        @DisplayName("categoryName is null when book has no category")
        void getBookById_noCategoryOnBook_categoryNameIsNull() {
            book1.setCategory(null);
            when(bookRepository.findById(TestFixtures.BOOK_ID_1)).thenReturn(Optional.of(book1));

            BookDetailResponse response = catalogService.getBookById(TestFixtures.BOOK_ID_1);

            assertThat(response.getCategoryName()).isNull();
        }

        @Test
        @DisplayName("uses default 5 delivery days when tentativeDeliveryDays is null")
        void getBookById_nullDeliveryDays_defaultsToFive() {
            book1.setTentativeDeliveryDays(null);
            when(bookRepository.findById(TestFixtures.BOOK_ID_1)).thenReturn(Optional.of(book1));

            BookDetailResponse response = catalogService.getBookById(TestFixtures.BOOK_ID_1);

            assertThat(response.getTentativeDeliveryDate())
                    .isEqualTo(java.time.LocalDate.now().plusDays(5));
        }
    }

    // =========================================================================
    // getRelatedBooks
    // =========================================================================

    @Nested
    @DisplayName("getRelatedBooks()")
    class GetRelatedBooks {

        @Test
        @DisplayName("returns books from same category when available")
        void getRelatedBooks_sameCategoryExists_returnsRelated() {
            when(bookRepository.findById(TestFixtures.BOOK_ID_1)).thenReturn(Optional.of(book1));
            when(bookRepository.findRelatedByCategory(
                    eq(TestFixtures.CATEGORY_ID),
                    eq(TestFixtures.BOOK_ID_1),
                    any(Pageable.class)))
                    .thenReturn(List.of(book2));

            PagedResponse<BookSummaryResponse> result =
                    catalogService.getRelatedBooks(TestFixtures.BOOK_ID_1, 5);

            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getTitle()).isEqualTo("Foundation");
            verify(bookRepository, never()).findRelatedByAuthor(anyString(), any(), any());
        }

        @Test
        @DisplayName("falls back to same-author search when category yields no results")
        void getRelatedBooks_emptyCategoryResult_fallsBackToAuthor() {
            when(bookRepository.findById(TestFixtures.BOOK_ID_1)).thenReturn(Optional.of(book1));
            when(bookRepository.findRelatedByCategory(any(), any(), any()))
                    .thenReturn(List.of());
            when(bookRepository.findRelatedByAuthor(
                    eq("Frank Herbert"),
                    eq(TestFixtures.BOOK_ID_1),
                    any(Pageable.class)))
                    .thenReturn(List.of(book2));

            PagedResponse<BookSummaryResponse> result =
                    catalogService.getRelatedBooks(TestFixtures.BOOK_ID_1, 5);

            assertThat(result.getData()).hasSize(1);
            verify(bookRepository).findRelatedByAuthor(eq("Frank Herbert"), any(), any());
        }

        @Test
        @DisplayName("skips category search and goes straight to author when book has no category")
        void getRelatedBooks_noCategory_usesAuthorSearch() {
            book1.setCategory(null);
            when(bookRepository.findById(TestFixtures.BOOK_ID_1)).thenReturn(Optional.of(book1));
            when(bookRepository.findRelatedByAuthor(anyString(), any(), any()))
                    .thenReturn(List.of(book2));

            PagedResponse<BookSummaryResponse> result =
                    catalogService.getRelatedBooks(TestFixtures.BOOK_ID_1, 5);

            assertThat(result.getData()).hasSize(1);
            verify(bookRepository, never()).findRelatedByCategory(any(), any(), any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the source book does not exist")
        void getRelatedBooks_bookNotFound_throwsException() {
            UUID id = UUID.randomUUID();
            when(bookRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> catalogService.getRelatedBooks(id, 5))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // getBestsellers & getNewLaunches
    // =========================================================================

    @Nested
    @DisplayName("getBestsellers() and getNewLaunches()")
    class SpecialLists {

        @Test
        @DisplayName("getBestsellers returns books ordered by soldCount DESC")
        void getBestsellers_returnsBooksPage() {
            Page<Book> page = new PageImpl<>(List.of(book1, book2));
            when(bookRepository.findBestsellers(any(Pageable.class))).thenReturn(page);

            PagedResponse<BookSummaryResponse> result = catalogService.getBestsellers(1, 10);

            assertThat(result.getData()).hasSize(2);
            assertThat(result.getData().get(0).getSoldCount())
                    .isGreaterThanOrEqualTo(result.getData().get(1).getSoldCount());
            verify(bookRepository).findBestsellers(any(Pageable.class));
        }

        @Test
        @DisplayName("getNewLaunches delegates to findNewLaunches repository method")
        void getNewLaunches_returnsBooksPage() {
            Pageable pageable = PageRequest.of(0, 5);
            Page<Book> page = new PageImpl<>(List.of(book2, book1), pageable, 2);
            when(bookRepository.findNewLaunches(any(Pageable.class))).thenReturn(page);

            PagedResponse<BookSummaryResponse> result = catalogService.getNewLaunches(1, 5);

            assertThat(result.getData()).hasSize(2);
            assertThat(result.getPageSize()).isEqualTo(5);
            verify(bookRepository).findNewLaunches(any(Pageable.class));
        }
    }

    // =========================================================================
    // listCategories
    // =========================================================================

    @Nested
    @DisplayName("listCategories()")
    class ListCategories {

        @Test
        @DisplayName("returns all categories mapped to CategoryResponse")
        void listCategories_returnsMappedList() {
            when(categoryRepository.findAll()).thenReturn(List.of(category));

            List<CategoryResponse> result = catalogService.listCategories();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCategoryId()).isEqualTo(TestFixtures.CATEGORY_ID);
            assertThat(result.get(0).getName()).isEqualTo("Science Fiction");
            assertThat(result.get(0).getSlug()).isEqualTo("science-fiction");
        }

        @Test
        @DisplayName("returns empty list when no categories exist")
        void listCategories_noCategories_returnsEmpty() {
            when(categoryRepository.findAll()).thenReturn(List.of());

            assertThat(catalogService.listCategories()).isEmpty();
        }
    }
}
