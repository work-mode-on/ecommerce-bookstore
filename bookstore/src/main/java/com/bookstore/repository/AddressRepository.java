package com.bookstore.repository;

import com.bookstore.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByUserUserId(UUID userId);

    boolean existsByUserUserIdAndIsDefault(UUID userId, boolean isDefault);
}
