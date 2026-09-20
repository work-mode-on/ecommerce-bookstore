package com.bookstore.repository;

import com.bookstore.entity.Order;
import com.bookstore.entity.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByUserUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<Order> findByUserUserIdAndStatus(UUID userId, OrderStatus status);

    Optional<Order> findByOrderIdAndUserUserId(UUID orderId, UUID userId);
}
