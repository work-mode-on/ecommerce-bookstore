package com.bookstore.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingEstimateResponse {
    private String pinCode;
    private BigDecimal deliveryCharge;
    private LocalDate tentativeDeliveryDate;
    private boolean serviceable;
    private String deliveryPartner;
    private Integer estimatedDays;
}
