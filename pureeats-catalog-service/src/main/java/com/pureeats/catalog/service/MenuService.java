package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.AdminItemCreateRequest;
import com.pureeats.catalog.dto.ItemBulkRowResult;
import com.pureeats.catalog.dto.ItemBulkUploadResponse;
import com.pureeats.catalog.dto.ItemCategoryRequest;
import com.pureeats.catalog.dto.ItemCategoryResponse;
import com.pureeats.catalog.dto.ItemImageResponse;
import com.pureeats.catalog.dto.ItemPatchRequest;
import com.pureeats.catalog.dto.ItemRequest;
import com.pureeats.catalog.dto.ItemResponse;
import com.pureeats.catalog.dto.RecommendedItemResponse;
import com.pureeats.catalog.dto.RestaurantSummaryResponse;
import com.pureeats.catalog.repository.AddonCategoryItemRepository;
import com.pureeats.catalog.repository.AddonCategoryRepository;
import com.pureeats.catalog.repository.ItemCategoryRepository;
import com.pureeats.catalog.repository.ItemRepository;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.AddonCategoryItem;
import com.pureeats.domain.entity.Item;
import com.pureeats.domain.entity.ItemCategory;
import com.pureeats.media.service.MediaAssetService;
import com.pureeats.media.storage.MediaUrlResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    private static final String ITEM_IMAGE_OWNER_TYPE = "ITEM";

    /** Cache name for the customer-facing dish menu ({@link #getMenu}) - dishes change rarely relative to how often they're browsed. Every write path below evicts the whole cache rather than surgically patching one restaurant's entry, same simple all-entries approach {@code RestaurantService} uses for the restaurant list. */
    static final String MENU_CACHE = "restaurantMenus";

    private final ItemCategoryRepository itemCategoryRepository;
    private final ItemRepository itemRepository;
    private final RestaurantService restaurantService;
    private final MediaUrlResolver mediaUrlResolver;
    private final MediaAssetService mediaAssetService;
    private final AddonCategoryRepository addonCategoryRepository;
    private final AddonCategoryItemRepository addonCategoryItemRepository;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = MENU_CACHE, key = "#restaurantId")
    public List<ItemResponse> getMenu(Long restaurantId) {
        return itemRepository.findByRestaurantIdAndIsActiveTrue(restaurantId.intValue()).stream()
                .map(this::toItemResponse).toList();
    }

    @Transactional(readOnly = true)
    public ItemResponse getItem(Long itemId) {
        return toItemResponse(findItemOrThrow(itemId));
    }

    /**
     * Cross-restaurant recommended items for the Home page. No geo-filtering (the restaurant listing
     * endpoints don't do that either yet) — just recommended + active items whose restaurant is still
     * active and accepted. Overfetches 3x the requested limit since some recommended items may belong
     * to a restaurant that's since gone inactive, then trims to the caller's limit after filtering.
     */
    @Transactional(readOnly = true)
    public List<RecommendedItemResponse> listRecommendedItems(int limit) {
        int fetchSize = Math.min(Math.max(limit, 1) * 3, 90);
        List<Item> items = itemRepository.findByIsRecommendedTrueAndIsActiveTrueOrderByIdDesc(PageRequest.of(0, fetchSize)).getContent();
        return withRestaurantContext(items, limit);
    }

    /**
     * Cross-restaurant dish name search — the Search page's "Dishes" tab (mirrors
     * {@code /restaurants/search}'s restaurant-name search, one level down at the item level). Same
     * overfetch-then-filter shape as {@link #listRecommendedItems} — a blank query returns nothing
     * rather than the whole catalog, matching RestaurantService.search's contract.
     */
    @Transactional(readOnly = true)
    public List<RecommendedItemResponse> searchItems(String query, int limit) {
        if (query == null || query.isBlank()) return List.of();
        int fetchSize = Math.min(Math.max(limit, 1) * 3, 90);
        List<Item> items = itemRepository.findByNameContainingIgnoreCaseAndIsActiveTrueOrderByIdDesc(query, PageRequest.of(0, fetchSize)).getContent();
        return withRestaurantContext(items, limit);
    }

    /** Shared by listRecommendedItems/searchItems: attaches each item's restaurant context, drops any whose restaurant is no longer active/accepted, and trims to the caller's limit. */
    private List<RecommendedItemResponse> withRestaurantContext(List<Item> items, int limit) {
        List<Long> restaurantIds = items.stream().map(i -> i.getRestaurantId().longValue()).distinct().toList();
        Map<Long, RestaurantSummaryResponse> restaurants = restaurantService.summariesByIds(restaurantIds);
        return items.stream()
                .map(i -> Map.entry(i, restaurants.get(i.getRestaurantId().longValue())))
                .filter(e -> e.getValue() != null && e.getValue().isActive() && e.getValue().isAccepted())
                .limit(limit)
                .map(e -> toRecommendedResponse(e.getKey(), e.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ItemCategoryResponse> listCategories(Long ownerUserId) {
        return itemCategoryRepository.findByUserId(ownerUserId.intValue()).stream()
                .map(this::toCategoryResponse).toList();
    }

    /** Admin listing - every item, optionally scoped to one restaurant and/or a name search. */
    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> listItemsPaged(Long restaurantId, String search, Pageable pageable) {
        log.debug("Admin listing items, restaurant {} search '{}' page {}", restaurantId, search, pageable.getPageNumber());
        Page<Item> page = itemRepository.findPage(restaurantId != null ? restaurantId.intValue() : null, search, pageable);
        return PageResponse.of(page.getContent().stream().map(this::toItemResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Admin listing - every item category, enabled or not. */
    @Transactional(readOnly = true)
    public PageResponse<ItemCategoryResponse> listCategoriesPaged(Pageable pageable) {
        Page<ItemCategory> page = itemCategoryRepository.findAll(pageable);
        return PageResponse.of(page.getContent().stream().map(this::toCategoryResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    public ItemCategoryResponse createCategory(Long ownerUserId, ItemCategoryRequest request) {
        ItemCategory category = new ItemCategory();
        category.setName(request.name());
        category.setUserId(ownerUserId.intValue());
        category.setIsEnabled(true);
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        return toCategoryResponse(itemCategoryRepository.save(category));
    }

    @Transactional
    public void setCategoryEnabled(Long ownerUserId, Long categoryId, boolean enabled) {
        ItemCategory category = assertCategoryOwnership(ownerUserId, categoryId);
        category.setIsEnabled(enabled);
        category.setUpdatedAt(LocalDateTime.now());
        itemCategoryRepository.save(category);
        log.info("Item category {} {} by owner {}", categoryId, enabled ? "enabled" : "disabled", ownerUserId);
    }

    /** Admin update - no ownership check, unlike the store-owner-scoped methods above. */
    @Transactional
    public ItemCategoryResponse updateCategoryAsAdmin(Long categoryId, ItemCategoryRequest request) {
        log.info("Admin updating item category {}", categoryId);
        ItemCategory category = itemCategoryRepository.findById(categoryId)
                .orElseThrow(() -> {
                    log.warn("Admin update failed - item category {} not found", categoryId);
                    return new ResourceNotFoundException("Item category not found: " + categoryId);
                });
        category.setName(request.name());
        category.setUpdatedAt(LocalDateTime.now());
        return toCategoryResponse(itemCategoryRepository.save(category));
    }

    @Transactional
    public void deleteCategoryAsAdmin(Long categoryId) {
        log.info("Admin deleting item category {}", categoryId);
        ItemCategory category = itemCategoryRepository.findById(categoryId)
                .orElseThrow(() -> {
                    log.warn("Admin delete failed - item category {} not found", categoryId);
                    return new ResourceNotFoundException("Item category not found: " + categoryId);
                });
        itemCategoryRepository.delete(category);
    }

    @Transactional
    @CacheEvict(cacheNames = MENU_CACHE, allEntries = true)
    public ItemResponse createItem(Long ownerUserId, Long restaurantId, ItemRequest request) {
        log.info("Creating item '{}' for restaurant {} by owner {}", request.name(), restaurantId, ownerUserId);
        restaurantService.assertOwnership(ownerUserId, restaurantId);

        Item item = new Item();
        item.setRestaurantId(restaurantId.intValue());
        item.setItemCategoryId(request.itemCategoryId().intValue());
        item.setName(request.name());
        item.setPrice(request.price());
        item.setOldPrice(request.oldPrice() != null ? request.oldPrice() : BigDecimal.ZERO);
        item.setImage(request.image());
        item.setDesc(request.desc());
        item.setIsRecommended(request.isRecommended());
        item.setIsPopular(request.isPopular());
        item.setIsVeg(request.isVeg());
        item.setIsNew(true);
        item.setIsActive(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        Item saved = itemRepository.save(item);
        if (request.addonCategoryIds() != null) {
            replaceAddonCategoryLinks(saved.getId(), request.addonCategoryIds());
        }
        ItemResponse response = toItemResponse(saved);
        log.info("Item {} created for restaurant {}", response.id(), restaurantId);
        return response;
    }

    @Transactional
    @CacheEvict(cacheNames = MENU_CACHE, allEntries = true)
    public ItemResponse updateItem(Long ownerUserId, Long itemId, ItemRequest request) {
        log.info("Owner {} updating item {}", ownerUserId, itemId);
        Item item = findItemOrThrow(itemId);
        restaurantService.assertOwnership(ownerUserId, item.getRestaurantId().longValue());

        if (request.price() != null && item.getPrice() != null && request.price().compareTo(item.getPrice()) != 0) {
            log.info("Item {} price updated from {} to {}", itemId, item.getPrice(), request.price());
        }
        item.setItemCategoryId(request.itemCategoryId().intValue());
        item.setName(request.name());
        item.setPrice(request.price());
        if (request.oldPrice() != null) {
            item.setOldPrice(request.oldPrice());
        }
        item.setImage(request.image());
        item.setDesc(request.desc());
        item.setIsRecommended(request.isRecommended());
        item.setIsPopular(request.isPopular());
        item.setIsVeg(request.isVeg());
        item.setUpdatedAt(LocalDateTime.now());
        Item saved = itemRepository.save(item);
        if (request.addonCategoryIds() != null) {
            replaceAddonCategoryLinks(saved.getId(), request.addonCategoryIds());
        }
        return toItemResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = MENU_CACHE, allEntries = true)
    public void setItemEnabled(Long ownerUserId, Long itemId, boolean enabled) {
        Item item = findItemOrThrow(itemId);
        restaurantService.assertOwnership(ownerUserId, item.getRestaurantId().longValue());
        item.setIsActive(enabled);
        item.setUpdatedAt(LocalDateTime.now());
        itemRepository.save(item);
        log.info("Item {} {} by owner {}", itemId, enabled ? "enabled" : "disabled", ownerUserId);
    }

    /** Admin create - no ownership check, restaurantId comes from the request itself. */
    @Transactional
    @CacheEvict(cacheNames = MENU_CACHE, allEntries = true)
    public ItemResponse createItemAsAdmin(AdminItemCreateRequest request) {
        log.info("Admin creating item '{}' for restaurant {}", request.name(), request.restaurantId());
        validateRestaurantAndCategory(request.restaurantId(), request.itemCategoryId());

        Item item = new Item();
        item.setRestaurantId(request.restaurantId().intValue());
        item.setItemCategoryId(request.itemCategoryId().intValue());
        item.setName(request.name());
        item.setPrice(request.price());
        item.setOldPrice(request.oldPrice() != null ? request.oldPrice() : BigDecimal.ZERO);
        item.setImage(request.image());
        item.setDesc(request.desc());
        item.setIsRecommended(request.isRecommended());
        item.setIsPopular(request.isPopular());
        item.setIsVeg(request.isVeg());
        item.setIsNew(true);
        item.setIsActive(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        Item saved = itemRepository.save(item);
        if (request.addonCategoryIds() != null) {
            replaceAddonCategoryLinks(saved.getId(), request.addonCategoryIds());
        }
        return toItemResponse(saved);
    }

    /** Admin partial update - only non-null fields are applied, no ownership check. */
    @Transactional
    @CacheEvict(cacheNames = MENU_CACHE, allEntries = true)
    public ItemResponse patchItemAsAdmin(Long itemId, ItemPatchRequest request) {
        log.info("Admin patching item {}", itemId);
        Item item = findItemOrThrow(itemId);
        if (request.itemCategoryId() != null) item.setItemCategoryId(request.itemCategoryId().intValue());
        if (request.name() != null) item.setName(request.name());
        if (request.price() != null) item.setPrice(request.price());
        if (request.oldPrice() != null) item.setOldPrice(request.oldPrice());
        if (request.image() != null) item.setImage(request.image());
        if (request.desc() != null) item.setDesc(request.desc());
        if (request.isRecommended() != null) item.setIsRecommended(request.isRecommended());
        if (request.isPopular() != null) item.setIsPopular(request.isPopular());
        if (request.isVeg() != null) item.setIsVeg(request.isVeg());
        if (request.isActive() != null) item.setIsActive(request.isActive());
        item.setUpdatedAt(LocalDateTime.now());
        Item saved = itemRepository.save(item);
        if (request.addonCategoryIds() != null) {
            replaceAddonCategoryLinks(saved.getId(), request.addonCategoryIds());
        }
        return toItemResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = MENU_CACHE, allEntries = true)
    public void deleteItemAsAdmin(Long itemId) {
        log.info("Admin deleting item {}", itemId);
        itemRepository.delete(findItemOrThrow(itemId));
    }

    /**
     * Verifies each row's restaurantId/itemCategoryId before saving it; a bad row is skipped, not fatal to the batch.
     * Evicts the menu cache itself, in addition to {@link #createItemAsAdmin} doing the same per-row - the per-row
     * eviction never actually fires here since it's a same-class method call, invisible to Spring's proxy-based AOP.
     */
    @Transactional
    @CacheEvict(cacheNames = MENU_CACHE, allEntries = true)
    public ItemBulkUploadResponse bulkCreateItems(List<AdminItemCreateRequest> rows) {
        log.info("Bulk-creating {} items", rows.size());
        List<ItemBulkRowResult> results = new ArrayList<>();
        int successCount = 0;
        for (int i = 0; i < rows.size(); i++) {
            try {
                ItemResponse created = createItemAsAdmin(rows.get(i));
                results.add(new ItemBulkRowResult(i, true, "Created", created.id()));
                successCount++;
            } catch (Exception e) {
                log.warn("Bulk-create row {} failed: {}", i, e.getMessage());
                results.add(new ItemBulkRowResult(i, false, e.getMessage(), null));
            }
        }
        log.info("Bulk-create finished: {}/{} rows succeeded", successCount, rows.size());
        return new ItemBulkUploadResponse(rows.size(), successCount, rows.size() - successCount, results);
    }

    @Transactional
    @CacheEvict(cacheNames = MENU_CACHE, allEntries = true)
    public ItemImageResponse uploadItemImage(Long itemId, MultipartFile file, Long uploadedBy) {
        log.info("Uploading image for item {} by user {}", itemId, uploadedBy);
        Item item = findItemOrThrow(itemId);
        String storageKey = mediaAssetService.upload(file, ITEM_IMAGE_OWNER_TYPE, itemId, uploadedBy).storageKey();
        item.setImage(storageKey);
        item.setUpdatedAt(LocalDateTime.now());
        itemRepository.save(item);
        return new ItemImageResponse(mediaUrlResolver.resolve(storageKey));
    }

    private void validateRestaurantAndCategory(Long restaurantId, Long itemCategoryId) {
        if (!restaurantService.exists(restaurantId)) {
            log.warn("Restaurant {} not found while validating item creation", restaurantId);
            throw new ResourceNotFoundException("Restaurant not found: " + restaurantId);
        }
        if (itemCategoryId == null || !itemCategoryRepository.existsById(itemCategoryId)) {
            log.warn("Item category {} not found while validating item creation", itemCategoryId);
            throw new ResourceNotFoundException("Item category not found: " + itemCategoryId);
        }
    }

    private ItemCategory assertCategoryOwnership(Long ownerUserId, Long categoryId) {
        ItemCategory category = itemCategoryRepository.findById(categoryId)
                .orElseThrow(() -> {
                    log.warn("Item category {} not found while checking ownership for owner {}", categoryId, ownerUserId);
                    return new ResourceNotFoundException("Item category not found: " + categoryId);
                });
        if (!category.getUserId().equals(ownerUserId.intValue())) {
            log.warn("Owner {} attempted to use item category {} owned by user {}", ownerUserId, categoryId, category.getUserId());
            throw new com.pureeats.domain.common.exception.ForbiddenException("This category does not belong to you");
        }
        return category;
    }

    Item findItemOrThrow(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> {
                    log.warn("Item {} not found", itemId);
                    return new ResourceNotFoundException("Item not found: " + itemId);
                });
    }

    /** Validates every id refers to a real addon category, then swaps the item's addon-category links for exactly this set. */
    private void replaceAddonCategoryLinks(Long itemId, List<Long> addonCategoryIds) {
        List<Long> distinctIds = addonCategoryIds.stream().distinct().toList();
        for (Long addonCategoryId : distinctIds) {
            if (!addonCategoryRepository.existsById(addonCategoryId)) {
                throw new BadRequestException("addonCategoryIds: no such addon category " + addonCategoryId);
            }
        }
        addonCategoryItemRepository.deleteAll(addonCategoryItemRepository.findByItemId(itemId));
        LocalDateTime now = LocalDateTime.now();
        List<AddonCategoryItem> links = distinctIds.stream().map(addonCategoryId -> {
            AddonCategoryItem link = new AddonCategoryItem();
            link.setItemId(itemId);
            link.setAddonCategoryId(addonCategoryId);
            link.setCreatedAt(now);
            link.setUpdatedAt(now);
            return link;
        }).toList();
        addonCategoryItemRepository.saveAll(links);
    }

    private List<Long> addonCategoryIdsFor(Long itemId) {
        return addonCategoryItemRepository.findByItemId(itemId).stream()
                .map(AddonCategoryItem::getAddonCategoryId)
                .toList();
    }

    private ItemCategoryResponse toCategoryResponse(ItemCategory c) {
        return new ItemCategoryResponse(c.getId(), c.getName(), Boolean.TRUE.equals(c.getIsEnabled()));
    }

    private RecommendedItemResponse toRecommendedResponse(Item i, RestaurantSummaryResponse restaurant) {
        return new RecommendedItemResponse(i.getId(), i.getRestaurantId().longValue(), restaurant.name(), restaurant.image(),
                i.getItemCategoryId().longValue(), i.getName(), i.getPrice(), i.getOldPrice(),
                mediaUrlResolver.resolve(i.getImage()), i.getDesc(), Boolean.TRUE.equals(i.getIsVeg()));
    }

    private ItemResponse toItemResponse(Item i) {
        return new ItemResponse(i.getId(), i.getRestaurantId().longValue(), i.getItemCategoryId().longValue(),
                i.getName(), i.getPrice(), i.getOldPrice(), mediaUrlResolver.resolve(i.getImage()), i.getDesc(),
                Boolean.TRUE.equals(i.getIsRecommended()), Boolean.TRUE.equals(i.getIsPopular()),
                Boolean.TRUE.equals(i.getIsNew()), Boolean.TRUE.equals(i.getIsVeg()), Boolean.TRUE.equals(i.getIsActive()),
                addonCategoryIdsFor(i.getId()));
    }
}
