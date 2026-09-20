package com.bookstore.service;

import com.bookstore.dto.request.AddressRequest;
import com.bookstore.dto.response.AddressResponse;
import com.bookstore.dto.response.UserResponse;
import com.bookstore.entity.Address;
import com.bookstore.entity.User;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.repository.AddressRepository;
import com.bookstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;

    public UserResponse getUserById(UUID userId) {
        User user = findUserOrThrow(userId);
        return toUserResponse(user);
    }

    public List<AddressResponse> getAddressesByUserId(UUID userId) {
        findUserOrThrow(userId);
        return addressRepository.findByUserUserId(userId)
                .stream()
                .map(this::toAddressResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AddressResponse addAddress(UUID userId, AddressRequest request) {
        User user = findUserOrThrow(userId);

        // If this is being set as default, unset existing default
        if (request.isDefault()) {
            addressRepository.findByUserUserId(userId)
                    .stream()
                    .filter(Address::isDefault)
                    .forEach(a -> {
                        a.setDefault(false);
                        addressRepository.save(a);
                    });
        }

        Address address = Address.builder()
                .user(user)
                .firstLine(request.getFirstLine())
                .secondLine(request.getSecondLine())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .pinCode(request.getPinCode())
                .isDefault(request.isDefault())
                .build();

        return toAddressResponse(addressRepository.save(address));
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .build();
    }

    private AddressResponse toAddressResponse(Address address) {
        return AddressResponse.builder()
                .addressId(address.getAddressId())
                .firstLine(address.getFirstLine())
                .secondLine(address.getSecondLine())
                .city(address.getCity())
                .state(address.getState())
                .country(address.getCountry())
                .pinCode(address.getPinCode())
                .isDefault(address.isDefault())
                .build();
    }
}
