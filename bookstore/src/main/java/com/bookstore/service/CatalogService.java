package com.bookstore.service;

import com.bookstore.dto.response.BookDetailResponse;
import com.bookstore.dto.response.BookSummaryResponse;
import com.bookstore.dto.response.CategoryResponse;
import com.bookstore.dto.response.PagedResponse;
import com.bookstore.entity.Book;
import com.bookstore.entity.enums.BookFormat;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;

    public PagedResponse<BookSummaryResponse> listBooks(
            String category, String search, String language,
            BookFormat format, BigDecimal minPrice, BigDecimal maxPrice,
            String sortBy, int page, int pageSize) {

        Pageable pageable = buildPageable(sortBy, page, pageSize);

        Page<Book> bookPage;

        if (search != null && !search.isBlank()) {
            bookPage = bookRepository.searchByKeyword(search.trim(), pageable);
        } else if (category != null && !category.isBlank()) {
            bookPage = bookRepository.findByCategorySlug(category, pageable);
        } else if (language != null && !language.isBlank()) {
            bookPage = bookRepository.findByLanguageIgnoreCase(language, pageable);
        } else if (format != null) {
            bookPage = bookRepository.findByFormat(format, pageable);
        } else if (minPrice != null && maxPrice != null) {
            bookPage = bookRepository.findByPriceBetween(minPrice, maxPrice, pageable);
        } else {
            bookPage = bookRepository.findAll(pageable);
        }

        return toPagedResponse(bookPage);
    }

    public BookDetailResponse getBookById(UUID bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book", "id", bookId));
        return toBookDetailResponse(book);
    }

    public PagedResponse<BookSummaryResponse> getRelatedBooks(UUID bookId, int pageSize) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book", "id", bookId));

        Pageable pageable = PageRequest.of(0, pageSize);
        List<Book> related = new ArrayList<>();

        if (book.getCategory() != null) {
            related = bookRepository.findRelatedByCategory(
                    book.getCategory().getCategoryId(), bookId, pageable);
        }

        if (related.isEmpty()) {
            related = bookRepository.findRelatedByAuthor(book.getAuthor(), bookId, pageable);
        }

        List<BookSummaryResponse> data = related.stream()
                .map(this::toBookSummaryResponse)
                .collect(Collectors.toList());

        return PagedResponse.<BookSummaryResponse>builder()
                .data(data)
                .currentPage(1)
                .pageSize(pageSize)
                .totalItems(data.size())
                .totalPages(1)
                .build();
    }

    public PagedResponse<BookSummaryResponse> getBestsellers(int page, int pageSize) {
        Page<Book> bookPage = bookRepository.findBestsellers(PageRequest.of(page - 1, pageSize));
        return toPagedResponse(bookPage);
    }

    public PagedResponse<BookSummaryResponse> getNewLaunches(int page, int pageSize) {
        Page<Book> bookPage = bookRepository.findNewLaunches(PageRequest.of(page - 1, pageSize));
        return toPagedResponse(bookPage);
    }

    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .map(c -> CategoryResponse.builder()
                        .categoryId(c.getCategoryId())
                        .name(c.getName())
                        .slug(c.getSlug())
                        .build())
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private Pageable buildPageable(String sortBy, int page, int pageSize) {
        Sort sort = switch (sortBy == null ? "" : sortBy) {
            case "price_asc"    -> Sort.by("price").ascending();
            case "price_desc"   -> Sort.by("price").descending();
            case "rating_desc"  -> Sort.by("rating").descending();
            case "newest"       -> Sort.by("createdAt").descending();
            case "bestselling"  -> Sort.by("soldCount").descending();
            default             -> Sort.by("createdAt").descending();
        };
        return PageRequest.of(Math.max(page - 1, 0), pageSize, sort);
    }

    private PagedResponse<BookSummaryResponse> toPagedResponse(Page<Book> bookPage) {
        List<BookSummaryResponse> data = bookPage.getContent().stream()
                .map(this::toBookSummaryResponse)
                .collect(Collectors.toList());

        return PagedResponse.<BookSummaryResponse>builder()
                .data(data)
                .currentPage(bookPage.getNumber() + 1)
                .pageSize(bookPage.getSize())
                .totalItems(bookPage.getTotalElements())
                .totalPages(bookPage.getTotalPages())
                .build();
    }

    private BookSummaryResponse toBookSummaryResponse(Book book) {
        return BookSummaryResponse.builder()
                .bookId(book.getBookId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .coverImageUrl(book.getCoverImageUrl())
                .price(book.getPrice())
                .format(book.getFormat())
                .language(book.getLanguage())
                .rating(book.getRating())
                .soldCount(book.getSoldCount())
                .stockStatus(book.getStockStatus())
                .categoryName(book.getCategory() != null ? book.getCategory().getName() : null)
                .publishedDate(book.getPublishedDate())
                .build();
    }

    private BookDetailResponse toBookDetailResponse(Book book) {
        LocalDate tentativeDeliveryDate = LocalDate.now()
                .plusDays(book.getTentativeDeliveryDays() != null ? book.getTentativeDeliveryDays() : 5);

        return BookDetailResponse.builder()
                .bookId(book.getBookId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .description(book.getDescription())
                .isbn(book.getIsbn())
                .price(book.getPrice())
                .publisher(book.getPublisher())
                .format(book.getFormat())
                .language(book.getLanguage())
                .pageCount(book.getPageCount())
                .publishedDate(book.getPublishedDate())
                .coverImageUrl(book.getCoverImageUrl())
                .rating(book.getRating())
                .soldCount(book.getSoldCount())
                .stockStatus(book.getStockStatus())
                .categoryName(book.getCategory() != null ? book.getCategory().getName() : null)
                .tentativeDeliveryDate(tentativeDeliveryDate)
                .build();
    }
}
