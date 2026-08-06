package com.medora.auth.repository;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.auth.domain.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, Long> {

    Optional<UserProfileEntity> findByUserAccount(UserAccountEntity userAccount);
}
