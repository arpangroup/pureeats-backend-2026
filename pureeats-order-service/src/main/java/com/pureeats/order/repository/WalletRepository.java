package com.pureeats.order.repository;

import com.pureeats.domain.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByHolderTypeAndHolderId(String holderType, Long holderId);
}
