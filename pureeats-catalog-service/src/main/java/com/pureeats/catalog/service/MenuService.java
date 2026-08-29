package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.ItemCategoryRequest;
import com.pureeats.catalog.dto.ItemCategoryResponse;
import com.pureeats.catalog.dto.ItemRequest;
import com.pureeats.catalog.dto.ItemResponse;
import com.pureeats.catalog.repository.ItemCategoryRepository;
import com.pureeats.catalog.repository.ItemRepository;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.Item;
import com.pureeats.domain.entity.ItemCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final ItemCategoryRepository itemCategoryRepository;
    private final ItemRepository itemRepository;
    private final RestaurantService restaurantService;

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
                i.getName(), i.getPrice(), i.getOldPrice(), i.getImage(), i.getDesc(),
                Boolean.TRUE.equals(i.getIsRecommended()), Boolean.TRUE.equals(i.getIsPopular()),
                Boolean.TRUE.equals(i.getIsNew()), Boolean.TRUE.equals(i.getIsVeg()), Boolean.TRUE.equals(i.getIsActive()));
    }
}
