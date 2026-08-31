package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.AddonCategoryRequest;
import com.pureeats.catalog.dto.AddonCategoryResponse;
import com.pureeats.catalog.dto.AddonRequest;
import com.pureeats.catalog.dto.AddonResponse;
import com.pureeats.catalog.repository.AddonCategoryRepository;
import com.pureeats.catalog.repository.AddonRepository;
import com.pureeats.domain.common.exception.ForbiddenException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.Addon;
import com.pureeats.domain.entity.AddonCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddonService {

    private final AddonCategoryRepository addonCategoryRepository;
    private final AddonRepository addonRepository;

    @Transactional(readOnly = true)
    public List<AddonCategoryResponse> listCategories(Long ownerUserId) {
        log.debug("Listing addon categories for owner {}", ownerUserId);
        return addonCategoryRepository.findByUserId(ownerUserId.intValue()).stream()
                .map(c -> new AddonCategoryResponse(c.getId(), c.getName(), c.getType())).toList();
    }

    @Transactional
    public AddonCategoryResponse createCategory(Long ownerUserId, AddonCategoryRequest request) {
        log.info("Creating addon category '{}' for owner {}", request.name(), ownerUserId);
        AddonCategory category = new AddonCategory();
        category.setName(request.name());
        category.setType(request.type());
        category.setUserId(ownerUserId.intValue());
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        category = addonCategoryRepository.save(category);
        log.info("Addon category {} created for owner {}", category.getId(), ownerUserId);
        return new AddonCategoryResponse(category.getId(), category.getName(), category.getType());
    }

    @Transactional(readOnly = true)
    public List<AddonResponse> listAddons(Long addonCategoryId) {
        log.debug("Listing active addons for category {}", addonCategoryId);
        return addonRepository.findByAddonCategoryIdAndIsActiveTrue(addonCategoryId.intValue()).stream()
                .map(this::toResponse).toList();
    }

    /** Admin listing - every addon category, regardless of owner. */
    @Transactional(readOnly = true)
    public PageResponse<AddonCategoryResponse> listCategoriesPaged(Pageable pageable) {
        log.debug("Admin listing addon categories, page {} size {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<AddonCategory> page = addonCategoryRepository.findAll(pageable);
        return PageResponse.of(page.getContent().stream()
                .map(c -> new AddonCategoryResponse(c.getId(), c.getName(), c.getType())).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Admin listing - every addon, optionally scoped to one category, active or not. */
    @Transactional(readOnly = true)
    public PageResponse<AddonResponse> listAddonsPaged(Long addonCategoryId, Pageable pageable) {
        log.debug("Admin listing addons, category {} page {} size {}", addonCategoryId, pageable.getPageNumber(), pageable.getPageSize());
        Page<Addon> page = addonRepository.findPage(addonCategoryId != null ? addonCategoryId.intValue() : null, pageable);
        return PageResponse.of(page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    public AddonResponse createAddon(Long ownerUserId, AddonRequest request) {
        log.info("Creating addon '{}' in category {} for owner {}", request.name(), request.addonCategoryId(), ownerUserId);
        assertCategoryOwnership(ownerUserId, request.addonCategoryId());

        Addon addon = new Addon();
        addon.setName(request.name());
        addon.setPrice(request.price());
        addon.setAddonCategoryId(request.addonCategoryId().intValue());
        addon.setUserId(ownerUserId.intValue());
        addon.setIsActive(true);
        addon.setCreatedAt(LocalDateTime.now());
        addon.setUpdatedAt(LocalDateTime.now());
        AddonResponse response = toResponse(addonRepository.save(addon));
        log.info("Addon {} created for owner {}", response.id(), ownerUserId);
        return response;
    }

    @Transactional
    public void setAddonEnabled(Long ownerUserId, Long addonId, boolean enabled) {
        Addon addon = addonRepository.findById(addonId)
                .orElseThrow(() -> {
                    log.warn("Addon {} not found while owner {} tried to {} it", addonId, ownerUserId, enabled ? "enable" : "disable");
                    return new ResourceNotFoundException("Addon not found: " + addonId);
                });
        if (!addon.getUserId().equals(ownerUserId.intValue())) {
            log.warn("Owner {} attempted to {} addon {} owned by user {}", ownerUserId, enabled ? "enable" : "disable", addonId, addon.getUserId());
            throw new ForbiddenException("This addon does not belong to you");
        }
        addon.setIsActive(enabled);
        addon.setUpdatedAt(LocalDateTime.now());
        addonRepository.save(addon);
        log.info("Addon {} {} by owner {}", addonId, enabled ? "enabled" : "disabled", ownerUserId);
    }

    /** Admin update - no ownership check, unlike the store-owner-scoped methods above. */
    @Transactional
    public AddonCategoryResponse updateCategoryAsAdmin(Long categoryId, AddonCategoryRequest request) {
        log.info("Admin updating addon category {}", categoryId);
        AddonCategory category = addonCategoryRepository.findById(categoryId)
                .orElseThrow(() -> {
                    log.warn("Admin update failed - addon category {} not found", categoryId);
                    return new ResourceNotFoundException("Addon category not found: " + categoryId);
                });
        category.setName(request.name());
        category.setType(request.type());
        category.setUpdatedAt(LocalDateTime.now());
        category = addonCategoryRepository.save(category);
        return new AddonCategoryResponse(category.getId(), category.getName(), category.getType());
    }

    @Transactional
    public void deleteCategoryAsAdmin(Long categoryId) {
        log.info("Admin deleting addon category {}", categoryId);
        AddonCategory category = addonCategoryRepository.findById(categoryId)
                .orElseThrow(() -> {
                    log.warn("Admin delete failed - addon category {} not found", categoryId);
                    return new ResourceNotFoundException("Addon category not found: " + categoryId);
                });
        addonCategoryRepository.delete(category);
    }

    @Transactional
    public AddonResponse updateAddonAsAdmin(Long addonId, AddonRequest request) {
        log.info("Admin updating addon {}", addonId);
        Addon addon = addonRepository.findById(addonId)
                .orElseThrow(() -> {
                    log.warn("Admin update failed - addon {} not found", addonId);
                    return new ResourceNotFoundException("Addon not found: " + addonId);
                });
        addon.setName(request.name());
        addon.setPrice(request.price());
        addon.setAddonCategoryId(request.addonCategoryId().intValue());
        addon.setUpdatedAt(LocalDateTime.now());
        return toResponse(addonRepository.save(addon));
    }

    @Transactional
    public void deleteAddonAsAdmin(Long addonId) {
        log.info("Admin deleting addon {}", addonId);
        Addon addon = addonRepository.findById(addonId)
                .orElseThrow(() -> {
                    log.warn("Admin delete failed - addon {} not found", addonId);
                    return new ResourceNotFoundException("Addon not found: " + addonId);
                });
        addonRepository.delete(addon);
    }

    private void assertCategoryOwnership(Long ownerUserId, Long addonCategoryId) {
        AddonCategory category = addonCategoryRepository.findById(addonCategoryId)
                .orElseThrow(() -> {
                    log.warn("Addon category {} not found while checking ownership for owner {}", addonCategoryId, ownerUserId);
                    return new ResourceNotFoundException("Addon category not found: " + addonCategoryId);
                });
        if (!category.getUserId().equals(ownerUserId.intValue())) {
            log.warn("Owner {} attempted to use addon category {} owned by user {}", ownerUserId, addonCategoryId, category.getUserId());
            throw new ForbiddenException("This addon category does not belong to you");
        }
    }

    private AddonResponse toResponse(Addon a) {
        return new AddonResponse(a.getId(), a.getAddonCategoryId().longValue(), a.getName(), a.getPrice(),
                Boolean.TRUE.equals(a.getIsActive()));
    }
}
