package com.medora.auth.repository;

import com.medora.auth.domain.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, Long> {

    Optional<UserAccountEntity> findByPublicId(UUID publicId);

    boolean existsByMobile(String mobile);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select u
            from UserAccountEntity u
            where u.mobile = :account
               or lower(coalesce(u.email, '')) = lower(:account)
            """)
    Optional<UserAccountEntity> findByAccount(@Param("account") String account);
}
