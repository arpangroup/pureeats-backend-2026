package com.pureeats.user.repository;

import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Admin-panel user listing. {@code Role} isn't a column on {@code users} - it's derived from the
 * legacy Spatie {@code model_has_roles}/{@code roles} pivot (see {@link com.pureeats.user.service.RoleService}),
 * so filtering by role means joining through it here rather than a plain {@code findByRole}.
 *
 * <p>Status filtering (see {@link com.pureeats.user.service.AdminUserService#listUsers}): the
 * three boolean/list params exist because {@code account_status} can be NULL for rows that
 * predate the column (treated as ACTIVE everywhere else in this codebase, e.g.
 * {@code AuthenticationService#assertAccountUsable}) - {@code matchAllStatuses} bypasses status
 * filtering entirely ("All"), {@code statuses} is the explicit allow-list otherwise, and
 * {@code includeNullAsActive} additionally matches NULL rows only when ACTIVE is one of the
 * requested statuses.
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
            AND (:matchAllStatuses = true
                 OR u.accountStatus IN :statuses
                 OR (:includeNullAsActive = true AND u.accountStatus IS NULL))
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
            AND (:matchAllStatuses = true
                 OR u.accountStatus IN :statuses
                 OR (:includeNullAsActive = true AND u.accountStatus IS NULL))
            """)
    Page<User> findByRoleNameAndStatus(@Param("roleName") String roleName, @Param("modelType") String modelType,
                                        @Param("search") String search, @Param("matchAllStatuses") boolean matchAllStatuses,
                                        @Param("statuses") List<AccountStatus> statuses, @Param("includeNullAsActive") boolean includeNullAsActive,
                                        Pageable pageable);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);
}
