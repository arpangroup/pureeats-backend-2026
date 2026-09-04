package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.RestaurantCategoryRequest;
import com.pureeats.catalog.dto.RestaurantCategoryResponse;
import com.pureeats.catalog.dto.RestaurantSummaryResponse;
import com.pureeats.catalog.repository.RestaurantCategoryRepository;
import com.pureeats.catalog.repository.RestaurantCategoryRestaurantRepository;
import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.entity.RestaurantCategory;
import com.pureeats.domain.entity.RestaurantCategoryRestaurant;
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
public class RestaurantCategoryService {

    private final RestaurantCategoryRepository restaurantCategoryRepository;
    private final RestaurantCategoryRestaurantRepository restaurantCategoryRestaurantRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantService restaurantService;

    @Transactional(readOnly = true)
    public List<RestaurantCategoryResponse> listActive() {
        return restaurantCategoryRepository.findByIsActiveTrue().stream().map(this::toResponse).toList();
    }

    /** Admin listing - all categories, active or not. */
    @Transactional(readOnly = true)
    public PageResponse<RestaurantCategoryResponse> listPaged(Pageable pageable) {
        Page<RestaurantCategory> page = restaurantCategoryRepository.findAll(pageable);
        return PageResponse.of(page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    public RestaurantCategoryResponse create(RestaurantCategoryRequest request) {
        log.info("Creating restaurant category '{}'", request.name());
        String name = request.name().trim();
        if (restaurantCategoryRepository.existsByNameIgnoreCase(name)) {
            log.warn("Rejected duplicate restaurant category name '{}'", name);
            throw new BadRequestException("A cuisine category named '" + name + "' already exists");
        }
        RestaurantCategory category = new RestaurantCategory();
        category.setName(name);
        category.setIsActive(request.isActive() == null || request.isActive());
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        RestaurantCategoryResponse response = toResponse(restaurantCategoryRepository.save(category));
        log.info("Restaurant category {} created", response.id());
        return response;
    }

    @Transactional
    public RestaurantCategoryResponse update(Long id, RestaurantCategoryRequest request) {
        log.info("Updating restaurant category {}", id);
        RestaurantCategory category = findOrThrow(id);
        String name = request.name().trim();
        if (restaurantCategoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            log.warn("Rejected duplicate restaurant category name '{}' on update of {}", name, id);
            throw new BadRequestException("A cuisine category named '" + name + "' already exists");
        }
        category.setName(name);
        if (request.isActive() != null) {
            category.setIsActive(request.isActive());
        }
        category.setUpdatedAt(LocalDateTime.now());
        return toResponse(restaurantCategoryRepository.save(category));
    }

    @Transactional
    public void delete(Long id) {
        log.info("Deleting restaurant category {}", id);
        restaurantCategoryRepository.delete(findOrThrow(id));
    }

    private RestaurantCategory findOrThrow(Long id) {
        return restaurantCategoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Restaurant category {} not found", id);
                    return new ResourceNotFoundException("Restaurant category not found: " + id);
                });
    }

    private RestaurantCategoryResponse toResponse(RestaurantCategory c) {
        return new RestaurantCategoryResponse(c.getId(), c.getName(), Boolean.TRUE.equals(c.getIsActive()));
    }

    @Transactional(readOnly = true)
    public List<RestaurantSummaryResponse> restaurantsInCategory(Long categoryId) {
        log.debug("Listing restaurants in category {}", categoryId);
        List<Long> restaurantIds = restaurantCategoryRestaurantRepository.findByRestaurantCategoryId(categoryId)
                .stream().map(RestaurantCategoryRestaurant::getRestaurantId).toList();
        return restaurantRepository.findAllById(restaurantIds).stream()
                .map(restaurantService::toSummary)
                .toList();
    }
}
