package com.bookstore.controller;

import com.bookstore.dto.response.ShippingEstimateResponse;
import com.bookstore.service.ShippingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
@Validated
@Tag(name = "Shipping", description = "Delivery estimation")
public class ShippingController {

    private final ShippingService shippingService;

    @GetMapping("/estimate")
    @Operation(summary = "Estimate delivery charge and date for a postal code")
    public ResponseEntity<ShippingEstimateResponse> estimate(
            @RequestParam @NotBlank(message = "Pin code is required") String pinCode,
            @RequestParam(required = false) BigDecimal orderValue) {
        return ResponseEntity.ok(shippingService.estimate(pinCode, orderValue));
    }
}
