package com.bookstore.repository;

import com.bookstore.entity.Book;
import com.bookstore.entity.enums.BookFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface BookRepository extends JpaRepository<Book, UUID>, JpaSpecificationExecutor<Book> {

    // Category filter
    Page<Book> findByCategoryCategoryId(UUID categoryId, Pageable pageable);

    Page<Book> findByCategorySlug(String slug, Pageable pageable);

    // Keyword search across title and author
    @Query("SELECT b FROM Book b WHERE " +
           "LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.author) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Book> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // Language filter
    Page<Book> findByLanguageIgnoreCase(String language, Pageable pageable);

    // Format filter
    Page<Book> findByFormat(BookFormat format, Pageable pageable);

    // Price range
    Page<Book> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    // Bestsellers – top books ordered by sold count
    @Query("SELECT b FROM Book b ORDER BY b.soldCount DESC")
    Page<Book> findBestsellers(Pageable pageable);

    // New launches – ordered by published / created date
    @Query("SELECT b FROM Book b ORDER BY b.createdAt DESC")
    Page<Book> findNewLaunches(Pageable pageable);

    // Related books by same category (excluding current book)
    @Query("SELECT b FROM Book b WHERE b.category.categoryId = :categoryId AND b.bookId <> :bookId ORDER BY b.rating DESC")
    List<Book> findRelatedByCategory(@Param("categoryId") UUID categoryId,
                                     @Param("bookId") UUID bookId,
                                     Pageable pageable);

    // Related books by same author (excluding current book)
    @Query("SELECT b FROM Book b WHERE LOWER(b.author) = LOWER(:author) AND b.bookId <> :bookId ORDER BY b.rating DESC")
    List<Book> findRelatedByAuthor(@Param("author") String author,
                                   @Param("bookId") UUID bookId,
                                   Pageable pageable);
}
