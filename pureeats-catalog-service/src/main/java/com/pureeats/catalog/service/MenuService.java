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
import com.pureeats.catalog.repository.ItemCategoryRepository;
import com.pureeats.catalog.repository.ItemRepository;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.Item;
import com.pureeats.domain.entity.ItemCategory;
import com.pureeats.media.service.MediaAssetService;
import com.pureeats.media.storage.MediaUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuService {

    private static final String ITEM_IMAGE_OWNER_TYPE = "ITEM";

    private final ItemCategoryRepository itemCategoryRepository;
    private final ItemRepository itemRepository;
    private final RestaurantService restaurantService;
    private final MediaUrlResolver mediaUrlResolver;
    private final MediaAssetService mediaAssetService;

    @Transactional(readOnly = true)
    public List<ItemResponse> getMenu(Long restaurantId) {
        return itemRepository.findByRestaurantIdAndIsActiveTrue(restaurantId.intValue()).stream()
                .map(this::toItemResponse).toList();
    }

    @Transactional(readOnly = true)
    public ItemResponse getItem(Long itemId) {
        return toItemResponse(findItemOrThrow(itemId));
    }

    @Transactional(readOnly = true)
    public List<ItemCategoryResponse> listCategories(Long ownerUserId) {
        return itemCategoryRepository.findByUserId(ownerUserId.intValue()).stream()
                .map(this::toCategoryResponse).toList();
    }

    /** Admin listing - every item, optionally scoped to one restaurant and/or a name search. */
    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> listItemsPaged(Long restaurantId, String search, Pageable pageable) {
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
    }

    /** Admin update - no ownership check, unlike the store-owner-scoped methods above. */
    @Transactional
    public ItemCategoryResponse updateCategoryAsAdmin(Long categoryId, ItemCategoryRequest request) {
        ItemCategory category = itemCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Item category not found: " + categoryId));
        category.setName(request.name());
        category.setUpdatedAt(LocalDateTime.now());
        return toCategoryResponse(itemCategoryRepository.save(category));
    }

    @Transactional
    public void deleteCategoryAsAdmin(Long categoryId) {
        ItemCategory category = itemCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Item category not found: " + categoryId));
        itemCategoryRepository.delete(category);
    }

    @Transactional
    public ItemResponse createItem(Long ownerUserId, Long restaurantId, ItemRequest request) {
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
        return toItemResponse(itemRepository.save(item));
    }

    @Transactional
    public ItemResponse updateItem(Long ownerUserId, Long itemId, ItemRequest request) {
        Item item = findItemOrThrow(itemId);
        restaurantService.assertOwnership(ownerUserId, item.getRestaurantId().longValue());

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
        return toItemResponse(itemRepository.save(item));
    }

    @Transactional
    public void setItemEnabled(Long ownerUserId, Long itemId, boolean enabled) {
        Item item = findItemOrThrow(itemId);
        restaurantService.assertOwnership(ownerUserId, item.getRestaurantId().longValue());
        item.setIsActive(enabled);
        item.setUpdatedAt(LocalDateTime.now());
        itemRepository.save(item);
    }

    /** Admin create - no ownership check, restaurantId comes from the request itself. */
    @Transactional
    public ItemResponse createItemAsAdmin(AdminItemCreateRequest request) {
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
        return toItemResponse(itemRepository.save(item));
    }

    /** Admin partial update - only non-null fields are applied, no ownership check. */
    @Transactional
    public ItemResponse patchItemAsAdmin(Long itemId, ItemPatchRequest request) {
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
        return toItemResponse(itemRepository.save(item));
    }

    @Transactional
    public void deleteItemAsAdmin(Long itemId) {
        itemRepository.delete(findItemOrThrow(itemId));
    }

    /** Verifies each row's restaurantId/itemCategoryId before saving it; a bad row is skipped, not fatal to the batch. */
    @Transactional
    public ItemBulkUploadResponse bulkCreateItems(List<AdminItemCreateRequest> rows) {
        List<ItemBulkRowResult> results = new ArrayList<>();
        int successCount = 0;
        for (int i = 0; i < rows.size(); i++) {
            try {
                ItemResponse created = createItemAsAdmin(rows.get(i));
                results.add(new ItemBulkRowResult(i, true, "Created", created.id()));
                successCount++;
            } catch (Exception e) {
                results.add(new ItemBulkRowResult(i, false, e.getMessage(), null));
            }
        }
        return new ItemBulkUploadResponse(rows.size(), successCount, rows.size() - successCount, results);
    }

    @Transactional
    public ItemImageResponse uploadItemImage(Long itemId, MultipartFile file, Long uploadedBy) {
        Item item = findItemOrThrow(itemId);
        String storageKey = mediaAssetService.upload(file, ITEM_IMAGE_OWNER_TYPE, itemId, uploadedBy).storageKey();
        item.setImage(storageKey);
        item.setUpdatedAt(LocalDateTime.now());
        itemRepository.save(item);
        return new ItemImageResponse(mediaUrlResolver.resolve(storageKey));
    }

    private void validateRestaurantAndCategory(Long restaurantId, Long itemCategoryId) {
        if (!restaurantService.exists(restaurantId)) {
            throw new ResourceNotFoundException("Restaurant not found: " + restaurantId);
        }
        if (itemCategoryId == null || !itemCategoryRepository.existsById(itemCategoryId)) {
            throw new ResourceNotFoundException("Item category not found: " + itemCategoryId);
        }
    }

    private ItemCategory assertCategoryOwnership(Long ownerUserId, Long categoryId) {
        ItemCategory category = itemCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Item category not found: " + categoryId));
        if (!category.getUserId().equals(ownerUserId.intValue())) {
            throw new com.pureeats.domain.common.exception.ForbiddenException("This category does not belong to you");
        }
        return category;
    }

    Item findItemOrThrow(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));
    }

    private ItemCategoryResponse toCategoryResponse(ItemCategory c) {
        return new ItemCategoryResponse(c.getId(), c.getName(), Boolean.TRUE.equals(c.getIsEnabled()));
    }

    private ItemResponse toItemResponse(Item i) {
        return new ItemResponse(i.getId(), i.getRestaurantId().longValue(), i.getItemCategoryId().longValue(),
                i.getName(), i.getPrice(), i.getOldPrice(), mediaUrlResolver.resolve(i.getImage()), i.getDesc(),
                Boolean.TRUE.equals(i.getIsRecommended()), Boolean.TRUE.equals(i.getIsPopular()),
                Boolean.TRUE.equals(i.getIsNew()), Boolean.TRUE.equals(i.getIsVeg()), Boolean.TRUE.equals(i.getIsActive()));
    }
}
