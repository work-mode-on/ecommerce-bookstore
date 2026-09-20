package com.bookstore.repository;

import com.bookstore.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderOrderId(UUID orderId);

    Optional<Payment> findByTransactionId(String transactionId);
}
