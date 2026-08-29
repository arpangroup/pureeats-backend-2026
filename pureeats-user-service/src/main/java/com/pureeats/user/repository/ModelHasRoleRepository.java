package com.pureeats.user.repository;

import com.pureeats.domain.entity.ModelHasRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelHasRoleRepository extends JpaRepository<ModelHasRole, ModelHasRole.ModelHasRoleId> {
    List<ModelHasRole> findByModelTypeAndModelId(String modelType, Long modelId);

    boolean existsByRoleId(Long roleId);
}
