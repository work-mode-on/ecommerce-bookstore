package com.bookstore.repository;

import com.bookstore.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    Optional<CartItem> findByCartCartIdAndItemId(UUID cartId, UUID itemId);

    Optional<CartItem> findByCartCartIdAndBookBookIdAndFormat(
            UUID cartId, UUID bookId,
            com.bookstore.entity.enums.BookFormat format);
}
