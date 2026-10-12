package com.envatobank.api.customer;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByEmailHash(String emailHash);

    Optional<Customer> findByNationalIdHash(String nationalIdHash);

    Optional<Customer> findByCustomerNo(String customerNo);

    boolean existsByEmailHash(String emailHash);

    boolean existsByNationalIdHash(String nationalIdHash);

    boolean existsByCustomerNo(String customerNo);
}
