package com.bookstore.service;

import com.bookstore.dto.request.ProcessPaymentRequest;
import com.bookstore.dto.response.PaymentResponse;
import com.bookstore.entity.Order;
import com.bookstore.entity.Payment;
import com.bookstore.entity.enums.OrderStatus;
import com.bookstore.entity.enums.PaymentMethod;
import com.bookstore.entity.enums.PaymentStatus;
import com.bookstore.exception.BadRequestException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.repository.OrderRepository;
import com.bookstore.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentResponse processPayment(ProcessPaymentRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.getOrderId()));

        if (order.getStatus() == OrderStatus.Cancelled) {
            throw new BadRequestException("Cannot process payment for a cancelled order");
        }

        // Validate method-specific fields
        validatePaymentDetails(request);

        // Simulate gateway call – always succeeds in this implementation
        PaymentStatus status = simulateGateway(request.getPaymentMethod());

        String transactionId = generateTransactionId();
        String receiptUrl = "https://api.bookstore.com/receipts/" + transactionId + ".pdf";

        Payment payment = Payment.builder()
                .transactionId(transactionId)
                .order(order)
                .paymentMethod(request.getPaymentMethod())
                .status(status)
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .receiptUrl(receiptUrl)
                .build();

        paymentRepository.save(payment);

        // Advance order status upon successful payment
        if (status == PaymentStatus.Success) {
            order.setStatus(OrderStatus.Processing);
            orderRepository.save(order);
        }

        return PaymentResponse.builder()
                .transactionId(transactionId)
                .orderId(order.getOrderId())
                .paymentStatus(status)
                .paymentMethod(request.getPaymentMethod())
                .amount(request.getAmount())
                .currency(payment.getCurrency())
                .transactionTimestamp(payment.getCreatedAt() != null
                        ? payment.getCreatedAt() : LocalDateTime.now())
                .receiptUrl(receiptUrl)
                .build();
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void validatePaymentDetails(ProcessPaymentRequest request) {
        PaymentMethod method = request.getPaymentMethod();
        if ((method == PaymentMethod.CreditCard || method == PaymentMethod.DebitCard)) {
            if (request.getCardNumber() == null || request.getCardHolderName() == null
                    || request.getExpiryMonth() == null || request.getExpiryYear() == null
                    || request.getCvv() == null) {
                throw new BadRequestException("Card details are required for " + method + " payments");
            }
        }
        if (method == PaymentMethod.UPI && (request.getUpiId() == null || request.getUpiId().isBlank())) {
            throw new BadRequestException("UPI ID is required for UPI payments");
        }
        if (method == PaymentMethod.Wallet
                && (request.getWalletProvider() == null || request.getWalletProvider().isBlank())) {
            throw new BadRequestException("Wallet provider is required for Wallet payments");
        }
    }

    /**
     * Simulated payment gateway.
     * In a production implementation this would call the actual payment provider SDK.
     */
    private PaymentStatus simulateGateway(PaymentMethod method) {
        // All methods succeed in simulation; extend with real provider logic here
        return PaymentStatus.Success;
    }

    private String generateTransactionId() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String unique = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "TXN-" + timestamp + "-" + unique;
    }
}
