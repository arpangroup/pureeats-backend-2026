package com.pureeats.user.repository;

import com.pureeats.user.entity.SecurityBlockEntry;
import com.pureeats.user.enums.BlockStatus;
import com.pureeats.user.enums.BlockType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SecurityBlockEntryRepository extends JpaRepository<SecurityBlockEntry, Long> {

    @Query("select e from SecurityBlockEntry e where e.blockType = :type and e.value = :value "
            + "and e.status = :status and (e.expiresAt is null or e.expiresAt > :now)")
    List<SecurityBlockEntry> findActive(@Param("type") BlockType type, @Param("value") String value,
                                         @Param("status") BlockStatus status, @Param("now") LocalDateTime now);
}
