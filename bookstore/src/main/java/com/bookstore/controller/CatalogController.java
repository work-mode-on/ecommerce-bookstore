package com.bookstore.controller;

import com.bookstore.dto.response.BookDetailResponse;
import com.bookstore.dto.response.BookSummaryResponse;
import com.bookstore.dto.response.CategoryResponse;
import com.bookstore.dto.response.PagedResponse;
import com.bookstore.entity.enums.BookFormat;
import com.bookstore.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Catalog", description = "Books, categories, bestsellers, and new launches")
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/books")
    @Operation(summary = "Search and filter books")
    public ResponseEntity<PagedResponse<BookSummaryResponse>> listBooks(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) BookFormat format,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(
                catalogService.listBooks(category, search, language, format, minPrice, maxPrice, sortBy, page, pageSize));
    }

    @GetMapping("/books/bestsellers")
    @Operation(summary = "Get bestselling books")
    public ResponseEntity<PagedResponse<BookSummaryResponse>> getBestsellers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(catalogService.getBestsellers(page, pageSize));
    }

    @GetMapping("/books/new-launches")
    @Operation(summary = "Get newly launched books")
    public ResponseEntity<PagedResponse<BookSummaryResponse>> getNewLaunches(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(catalogService.getNewLaunches(page, pageSize));
    }

    @GetMapping("/books/{id}")
    @Operation(summary = "Get detailed information for a specific book")
    public ResponseEntity<BookDetailResponse> getBookById(@PathVariable UUID id) {
        return ResponseEntity.ok(catalogService.getBookById(id));
    }

    @GetMapping("/books/{id}/related")
    @Operation(summary = "Get related books by category or author")
    public ResponseEntity<PagedResponse<BookSummaryResponse>> getRelatedBooks(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(catalogService.getRelatedBooks(id, pageSize));
    }

    @GetMapping("/categories")
    @Operation(summary = "List all book categories")
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        return ResponseEntity.ok(catalogService.listCategories());
    }
}
