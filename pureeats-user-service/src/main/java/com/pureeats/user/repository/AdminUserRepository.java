package com.pureeats.user.repository;

import com.pureeats.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Admin-panel user listing. {@code Role} isn't a column on {@code users} - it's derived from the
 * legacy Spatie {@code model_has_roles}/{@code roles} pivot (see {@link com.pureeats.user.service.RoleService}),
 * so filtering by role means joining through it here rather than a plain {@code findByRole}.
 */
public interface AdminUserRepository extends JpaRepository<User, Long> {

    @Query(value = """
            SELECT u FROM User u
            WHERE u.id IN (
                SELECT mhr.modelId FROM ModelHasRole mhr, Role r
                WHERE mhr.roleId = r.id AND r.name = :roleName AND mhr.modelType = :modelType
            )
            AND (:search IS NULL OR :search = ''
                 OR LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR u.phone LIKE CONCAT('%', :search, '%'))
            """,
            countQuery = """
            SELECT COUNT(u) FROM User u
            WHERE u.id IN (
                SELECT mhr.modelId FROM ModelHasRole mhr, Role r
                WHERE mhr.roleId = r.id AND r.name = :roleName AND mhr.modelType = :modelType
            )
            AND (:search IS NULL OR :search = ''
                 OR LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR u.phone LIKE CONCAT('%', :search, '%'))
            """)
    Page<User> findByRoleName(@Param("roleName") String roleName, @Param("modelType") String modelType,
                               @Param("search") String search, Pageable pageable);
}
