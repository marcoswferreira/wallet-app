package com.financial.wallet.repository;

import com.financial.wallet.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Default findById uses OPTIMISTIC locking (Hibernate tracks @Version
     * automatically).
     * Suitable for high-throughput scenarios — no DB row lock is acquired.
     * The @Version field detects concurrent modifications and throws
     * OptimisticLockException.
     */
    Optional<Account> findById(UUID id);

    /**
     * Alternative: Pessimistic write lock (SELECT ... FOR UPDATE).
     * Use when you need guaranteed serialization at the DB level (lower
     * throughput).
     * Uncomment and use in AccountService.withdraw if contention is very high.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
