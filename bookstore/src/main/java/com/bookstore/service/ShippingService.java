package com.bookstore.service;

import com.bookstore.dto.response.ShippingEstimateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class ShippingService {

    @Value("${app.cart.free-shipping-threshold}")
    private BigDecimal freeShippingThreshold;

    @Value("${app.cart.shipping-charge}")
    private BigDecimal defaultShippingCharge;

    /**
     * Returns a shipping estimate for the given postal code.
     * In a production system this would call a logistics provider API.
     */
    public ShippingEstimateResponse estimate(String pinCode, BigDecimal orderValue) {
        // Simulate non-serviceable PIN codes (e.g. starting with "00")
        boolean serviceable = !pinCode.startsWith("00");

        if (!serviceable) {
            return ShippingEstimateResponse.builder()
                    .pinCode(pinCode)
                    .deliveryCharge(BigDecimal.ZERO)
                    .tentativeDeliveryDate(null)
                    .serviceable(false)
                    .deliveryPartner("N/A")
                    .estimatedDays(0)
                    .build();
        }

        // Free shipping above threshold
        BigDecimal charge = (orderValue != null && orderValue.compareTo(freeShippingThreshold) >= 0)
                ? BigDecimal.ZERO
                : defaultShippingCharge;

        // Simple heuristic: domestic (<=6 chars) = 3 days, international = 7 days
        int estimatedDays = pinCode.replace(" ", "").length() <= 6 ? 3 : 7;
        LocalDate deliveryDate = LocalDate.now().plusDays(estimatedDays);

        String partner = estimatedDays <= 3 ? "FedEx Express" : "FedEx International";

        return ShippingEstimateResponse.builder()
                .pinCode(pinCode)
                .deliveryCharge(charge)
                .tentativeDeliveryDate(deliveryDate)
                .serviceable(true)
                .deliveryPartner(partner)
                .estimatedDays(estimatedDays)
                .build();
    }
}
