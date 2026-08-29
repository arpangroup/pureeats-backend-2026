package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.RestaurantCategoryResponse;
import com.pureeats.catalog.dto.RestaurantSummaryResponse;
import com.pureeats.catalog.repository.RestaurantCategoryRepository;
import com.pureeats.catalog.repository.RestaurantCategoryRestaurantRepository;
import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.entity.RestaurantCategoryRestaurant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RestaurantCategoryService {

    private final RestaurantCategoryRepository restaurantCategoryRepository;
    private final RestaurantCategoryRestaurantRepository restaurantCategoryRestaurantRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public List<RestaurantCategoryResponse> listActive() {
        return restaurantCategoryRepository.findByIsActiveTrue().stream()
                .map(c -> new RestaurantCategoryResponse(c.getId(), c.getName())).toList();
    }

    @Transactional(readOnly = true)
    public List<RestaurantSummaryResponse> restaurantsInCategory(Long categoryId) {
        List<Long> restaurantIds = restaurantCategoryRestaurantRepository.findByRestaurantCategoryId(categoryId)
                .stream().map(RestaurantCategoryRestaurant::getRestaurantId).toList();
        return restaurantRepository.findAllById(restaurantIds).stream()
                .map(r -> new RestaurantSummaryResponse(r.getId(), r.getName(), r.getSlug(), r.getImage(),
                        r.getRating(), r.getDeliveryTime(), r.getPriceRange(), Boolean.TRUE.equals(r.getIsPureveg()),
                        Boolean.TRUE.equals(r.getIsActive()), Boolean.TRUE.equals(r.getIsAccepted()),
                        r.getMinOrderPrice(), r.getDeliveryCharges()))
                .toList();
    }
}
