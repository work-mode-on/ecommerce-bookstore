package com.bookstore.controller;

import com.bookstore.dto.request.AddressRequest;
import com.bookstore.dto.response.AddressResponse;
import com.bookstore.dto.response.UserResponse;
import com.bookstore.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile and address management")
@SecurityRequirement(name = "BearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    @Operation(summary = "Get user profile by ID")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping("/{id}/addresses")
    @Operation(summary = "List all saved addresses for a user")
    public ResponseEntity<List<AddressResponse>> getAddresses(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getAddressesByUserId(id));
    }

    @PostMapping("/{id}/addresses")
    @Operation(summary = "Add a new address for a user")
    public ResponseEntity<AddressResponse> addAddress(@PathVariable UUID id,
                                                       @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.addAddress(id, request));
    }
}
