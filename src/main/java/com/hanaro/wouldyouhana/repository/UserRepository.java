package com.hanaro.wouldyouhana.repository;

import com.hanaro.wouldyouhana.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<Customer, Long> {
}
