package com.pureeats.user.security.blocklist;

import com.pureeats.user.entity.SecurityBlockEntry;
import com.pureeats.user.enums.BlockStatus;
import com.pureeats.user.enums.BlockType;
import com.pureeats.user.repository.SecurityBlockEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class JpaBlocklistService implements BlocklistService {

    private final SecurityBlockEntryRepository repository;

    @Override
    @Transactional(readOnly = true)
    public boolean isBlocked(BlockType type, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return !repository.findActive(type, value, BlockStatus.ACTIVE, LocalDateTime.now()).isEmpty();
    }

    @Override
    @Transactional
    public void block(BlockType type, String value, String reason, LocalDateTime expiresAt, String createdBy) {
        SecurityBlockEntry entry = new SecurityBlockEntry();
        entry.setBlockType(type);
        entry.setValue(value);
        entry.setReason(reason);
        entry.setStatus(BlockStatus.ACTIVE);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setExpiresAt(expiresAt);
        entry.setCreatedBy(createdBy);
        repository.save(entry);
    }
}
