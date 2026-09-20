package com.bookstore.controller;

import com.bookstore.config.SecurityConfig;
import com.bookstore.dto.response.BookDetailResponse;
import com.bookstore.dto.response.BookSummaryResponse;
import com.bookstore.dto.response.CategoryResponse;
import com.bookstore.dto.response.PagedResponse;
import com.bookstore.entity.enums.BookFormat;
import com.bookstore.entity.enums.StockStatus;
import com.bookstore.exception.GlobalExceptionHandler;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.security.JwtAuthFilter;
import com.bookstore.security.JwtUtils;
import com.bookstore.security.UserDetailsServiceImpl;
import com.bookstore.service.CatalogService;
import com.bookstore.util.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc slice tests for {@link CatalogController}.
 *
 * Uses {@code @WebMvcTest} to load only the web layer.
 * Security is kept active; public catalog endpoints should not need a token.
 */
@WebMvcTest(CatalogController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("CatalogController – MockMvc Tests")
class CatalogControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean CatalogService catalogService;

    // Security dependencies that SecurityConfig needs in the application context
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean JwtUtils jwtUtils;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private PagedResponse<BookSummaryResponse> singleBookPage;
    private BookDetailResponse bookDetail;

    @BeforeEach
    void setUp() {
        BookSummaryResponse summary = BookSummaryResponse.builder()
                .bookId(TestFixtures.BOOK_ID_1)
                .title("Dune")
                .author("Frank Herbert")
                .price(new BigDecimal("14.99"))
                .format(BookFormat.Paperback)
                .language("English")
                .rating(new BigDecimal("4.80"))
                .soldCount(50000)
                .stockStatus(StockStatus.IN_STOCK)
                .categoryName("Science Fiction")
                .publishedDate(LocalDate.of(1965, 8, 1))
                .build();

        singleBookPage = PagedResponse.<BookSummaryResponse>builder()
                .data(List.of(summary))
                .currentPage(1)
                .pageSize(10)
                .totalItems(1)
                .totalPages(1)
                .build();

        bookDetail = BookDetailResponse.builder()
                .bookId(TestFixtures.BOOK_ID_1)
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
                .rating(new BigDecimal("4.80"))
                .soldCount(50000)
                .stockStatus(StockStatus.IN_STOCK)
                .categoryName("Science Fiction")
                .tentativeDeliveryDate(LocalDate.now().plusDays(3))
                .build();
    }

    // =========================================================================
    // GET /api/books
    // =========================================================================

    @Nested
    @DisplayName("GET /api/books")
    class ListBooks {

        @Test
        @DisplayName("200 OK with no filters – returns paged list")
        void listBooks_noFilters_returns200() throws Exception {
            when(catalogService.listBooks(isNull(), isNull(), isNull(), isNull(),
                    isNull(), isNull(), isNull(), eq(1), eq(10)))
                    .thenReturn(singleBookPage);

            mockMvc.perform(get("/api/books").accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].title").value("Dune"))
                    .andExpect(jsonPath("$.currentPage").value(1))
                    .andExpect(jsonPath("$.totalItems").value(1));
        }

        @Test
        @DisplayName("200 OK with 'search' query param – passes to service")
        void listBooks_withSearchParam_returns200() throws Exception {
            when(catalogService.listBooks(isNull(), eq("dune"), isNull(), isNull(),
                    isNull(), isNull(), isNull(), eq(1), eq(10)))
                    .thenReturn(singleBookPage);

            mockMvc.perform(get("/api/books")
                            .param("search", "dune")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].title").value("Dune"));
        }

        @Test
        @DisplayName("200 OK with 'category' query param")
        void listBooks_withCategoryParam_returns200() throws Exception {
            when(catalogService.listBooks(eq("science-fiction"), isNull(), isNull(), isNull(),
                    isNull(), isNull(), isNull(), eq(1), eq(10)))
                    .thenReturn(singleBookPage);

            mockMvc.perform(get("/api/books")
                            .param("category", "science-fiction")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)));
        }

        @Test
        @DisplayName("200 OK with price range params")
        void listBooks_withPriceRange_returns200() throws Exception {
            when(catalogService.listBooks(isNull(), isNull(), isNull(), isNull(),
                    eq(new BigDecimal("5.00")), eq(new BigDecimal("20.00")),
                    isNull(), eq(1), eq(10)))
                    .thenReturn(singleBookPage);

            mockMvc.perform(get("/api/books")
                            .param("minPrice", "5.00")
                            .param("maxPrice", "20.00")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("200 OK with 'sortBy' and custom pagination params")
        void listBooks_withSortAndPagination_returns200() throws Exception {
            when(catalogService.listBooks(isNull(), isNull(), isNull(), isNull(),
                    isNull(), isNull(), eq("price_asc"), eq(2), eq(5)))
                    .thenReturn(singleBookPage);

            mockMvc.perform(get("/api/books")
                            .param("sortBy", "price_asc")
                            .param("page", "2")
                            .param("pageSize", "5")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("200 OK – empty result set returns valid empty paged response")
        void listBooks_emptyResult_returns200WithEmptyData() throws Exception {
            PagedResponse<BookSummaryResponse> empty = PagedResponse.<BookSummaryResponse>builder()
                    .data(List.of()).currentPage(1).pageSize(10).totalItems(0).totalPages(0).build();
            when(catalogService.listBooks(any(), any(), any(), any(), any(), any(), any(),
                    anyInt(), anyInt())).thenReturn(empty);

            mockMvc.perform(get("/api/books").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(0)))
                    .andExpect(jsonPath("$.totalItems").value(0));
        }
    }

    // =========================================================================
    // GET /api/books/bestsellers
    // =========================================================================

    @Nested
    @DisplayName("GET /api/books/bestsellers")
    class Bestsellers {

        @Test
        @DisplayName("200 OK – returns bestsellers list")
        void getBestsellers_returns200() throws Exception {
            when(catalogService.getBestsellers(1, 10)).thenReturn(singleBookPage);

            mockMvc.perform(get("/api/books/bestsellers").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].soldCount").value(50000));
        }

        @Test
        @DisplayName("200 OK – pagination params are forwarded")
        void getBestsellers_withPagination_returns200() throws Exception {
            when(catalogService.getBestsellers(2, 5)).thenReturn(singleBookPage);

            mockMvc.perform(get("/api/books/bestsellers")
                            .param("page", "2")
                            .param("pageSize", "5")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================================
    // GET /api/books/new-launches
    // =========================================================================

    @Nested
    @DisplayName("GET /api/books/new-launches")
    class NewLaunches {

        @Test
        @DisplayName("200 OK – returns new launches list")
        void getNewLaunches_returns200() throws Exception {
            when(catalogService.getNewLaunches(1, 10)).thenReturn(singleBookPage);

            mockMvc.perform(get("/api/books/new-launches").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)));
        }
    }

    // =========================================================================
    // GET /api/books/{id}
    // =========================================================================

    @Nested
    @DisplayName("GET /api/books/{id}")
    class GetBookById {

        @Test
        @DisplayName("200 OK – returns full book detail for existing book")
        void getBookById_found_returns200() throws Exception {
            when(catalogService.getBookById(TestFixtures.BOOK_ID_1)).thenReturn(bookDetail);

            mockMvc.perform(get("/api/books/{id}", TestFixtures.BOOK_ID_1)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookId").value(TestFixtures.BOOK_ID_1.toString()))
                    .andExpect(jsonPath("$.title").value("Dune"))
                    .andExpect(jsonPath("$.author").value("Frank Herbert"))
                    .andExpect(jsonPath("$.isbn").value("978-0441013593"))
                    .andExpect(jsonPath("$.price").value(14.99))
                    .andExpect(jsonPath("$.format").value("Paperback"))
                    .andExpect(jsonPath("$.categoryName").value("Science Fiction"))
                    .andExpect(jsonPath("$.tentativeDeliveryDate").isNotEmpty());
        }

        @Test
        @DisplayName("404 Not Found – when book does not exist")
        void getBookById_notFound_returns404() throws Exception {
            UUID unknownId = UUID.randomUUID();
            when(catalogService.getBookById(unknownId))
                    .thenThrow(new ResourceNotFoundException("Book", "id", unknownId));

            mockMvc.perform(get("/api/books/{id}", unknownId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
    }

    // =========================================================================
    // GET /api/books/{id}/related
    // =========================================================================

    @Nested
    @DisplayName("GET /api/books/{id}/related")
    class RelatedBooks {

        @Test
        @DisplayName("200 OK – returns related books")
        void getRelatedBooks_found_returns200() throws Exception {
            when(catalogService.getRelatedBooks(TestFixtures.BOOK_ID_1, 10))
                    .thenReturn(singleBookPage);

            mockMvc.perform(get("/api/books/{id}/related", TestFixtures.BOOK_ID_1)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("404 Not Found – when source book does not exist")
        void getRelatedBooks_sourceNotFound_returns404() throws Exception {
            UUID unknownId = UUID.randomUUID();
            when(catalogService.getRelatedBooks(unknownId, 10))
                    .thenThrow(new ResourceNotFoundException("Book", "id", unknownId));

            mockMvc.perform(get("/api/books/{id}/related", unknownId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // GET /api/categories
    // =========================================================================

    @Nested
    @DisplayName("GET /api/categories")
    class ListCategories {

        @Test
        @DisplayName("200 OK – returns list of categories")
        void listCategories_returns200() throws Exception {
            CategoryResponse cat = CategoryResponse.builder()
                    .categoryId(TestFixtures.CATEGORY_ID)
                    .name("Science Fiction")
                    .slug("science-fiction")
                    .build();
            when(catalogService.listCategories()).thenReturn(List.of(cat));

            mockMvc.perform(get("/api/categories").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name").value("Science Fiction"))
                    .andExpect(jsonPath("$[0].slug").value("science-fiction"));
        }

        @Test
        @DisplayName("200 OK – empty list when no categories exist")
        void listCategories_empty_returns200WithEmptyArray() throws Exception {
            when(catalogService.listCategories()).thenReturn(List.of());

            mockMvc.perform(get("/api/categories").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }
}
