package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.AdminPromoSliderRequest;
import com.pureeats.catalog.dto.AdminPromoSliderResponse;
import com.pureeats.catalog.dto.AdminRestaurantCategorySliderRequest;
import com.pureeats.catalog.dto.AdminRestaurantCategorySliderResponse;
import com.pureeats.catalog.dto.AdminSlideRequest;
import com.pureeats.catalog.dto.AdminSlideResponse;
import com.pureeats.catalog.dto.SlideImageResponse;
import com.pureeats.catalog.repository.PromoSliderRepository;
import com.pureeats.catalog.repository.RestaurantCategorySliderRepository;
import com.pureeats.catalog.repository.SlideRepository;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.PromoSlider;
import com.pureeats.domain.entity.RestaurantCategorySlider;
import com.pureeats.domain.entity.Slide;
import com.pureeats.media.service.MediaAssetService;
import com.pureeats.media.storage.MediaUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Admin CRUD over promo sliders, store-category sliders, and the slides inside each. */
@Service
@RequiredArgsConstructor
public class AdminSliderService {

    private static final String SLIDE_IMAGE_OWNER_TYPE = "SLIDE";
    private static final Map<String, Integer> SIZE_TO_INT = Map.of("small", 1, "medium", 2, "large", 3);
    private static final Map<Integer, String> INT_TO_SIZE = Map.of(1, "small", 2, "medium", 3, "large");
    private static final String CATEGORY_SLIDER_TYPE = "category";

    private final PromoSliderRepository promoSliderRepository;
    private final RestaurantCategorySliderRepository restaurantCategorySliderRepository;
    private final SlideRepository slideRepository;
    private final MediaUrlResolver mediaUrlResolver;
    private final MediaAssetService mediaAssetService;

    // ---- Promo sliders ----

    @Transactional(readOnly = true)
    public PageResponse<AdminPromoSliderResponse> listPromoSliders(String search, Pageable pageable) {
        Page<PromoSlider> page = promoSliderRepository.findPage(search, pageable);
        return PageResponse.of(page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminPromoSliderResponse getPromoSlider(Long id) {
        return toResponse(findPromoSliderOrThrow(id));
    }

    @Transactional
    public AdminPromoSliderResponse createPromoSlider(AdminPromoSliderRequest request) {
        PromoSlider slider = new PromoSlider();
        applyPromoSlider(slider, request);
        slider.setCreatedAt(LocalDateTime.now());
        slider.setUpdatedAt(LocalDateTime.now());
        return toResponse(promoSliderRepository.save(slider));
    }

    @Transactional
    public AdminPromoSliderResponse updatePromoSlider(Long id, AdminPromoSliderRequest request) {
        PromoSlider slider = findPromoSliderOrThrow(id);
        applyPromoSlider(slider, request);
        slider.setUpdatedAt(LocalDateTime.now());
        return toResponse(promoSliderRepository.save(slider));
    }

    @Transactional
    public void deletePromoSlider(Long id) {
        promoSliderRepository.delete(findPromoSliderOrThrow(id));
    }

    private void applyPromoSlider(PromoSlider slider, AdminPromoSliderRequest request) {
        slider.setName(request.name());
        slider.setIsActive(request.isActive() == null || request.isActive());
        slider.setLocationId(request.locationId());
        slider.setPositionId(request.positionId() != null ? request.positionId() : 1);
        slider.setSize(SIZE_TO_INT.getOrDefault(request.size(), 2));
    }

    private PromoSlider findPromoSliderOrThrow(Long id) {
        return promoSliderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promo slider not found: " + id));
    }

    private AdminPromoSliderResponse toResponse(PromoSlider s) {
        return new AdminPromoSliderResponse(s.getId(), s.getName(), Boolean.TRUE.equals(s.getIsActive()),
                s.getLocationId(), s.getPositionId(), INT_TO_SIZE.getOrDefault(s.getSize(), "medium"),
                s.getCreatedAt(), s.getUpdatedAt());
    }

    // ---- Restaurant category sliders ----

    @Transactional(readOnly = true)
    public PageResponse<AdminRestaurantCategorySliderResponse> listCategorySliders(String search, Pageable pageable) {
        Page<RestaurantCategorySlider> page = restaurantCategorySliderRepository.findPage(search, pageable);
        return PageResponse.of(page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminRestaurantCategorySliderResponse getCategorySlider(Long id) {
        return toResponse(findCategorySliderOrThrow(id));
    }

    @Transactional
    public AdminRestaurantCategorySliderResponse createCategorySlider(AdminRestaurantCategorySliderRequest request) {
        RestaurantCategorySlider slider = new RestaurantCategorySlider();
        slider.setName(request.name());
        slider.setIsActive(request.isActive() == null || request.isActive());
        slider.setCategoriesIds("");
        slider.setCreatedAt(LocalDateTime.now());
        slider.setUpdatedAt(LocalDateTime.now());
        return toResponse(restaurantCategorySliderRepository.save(slider));
    }

    @Transactional
    public AdminRestaurantCategorySliderResponse updateCategorySlider(Long id, AdminRestaurantCategorySliderRequest request) {
        RestaurantCategorySlider slider = findCategorySliderOrThrow(id);
        slider.setName(request.name());
        slider.setIsActive(request.isActive() == null || request.isActive());
        slider.setUpdatedAt(LocalDateTime.now());
        return toResponse(restaurantCategorySliderRepository.save(slider));
    }

    @Transactional
    public void deleteCategorySlider(Long id) {
        restaurantCategorySliderRepository.delete(findCategorySliderOrThrow(id));
    }

    private RestaurantCategorySlider findCategorySliderOrThrow(Long id) {
        return restaurantCategorySliderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category slider not found: " + id));
    }

    private AdminRestaurantCategorySliderResponse toResponse(RestaurantCategorySlider s) {
        return new AdminRestaurantCategorySliderResponse(s.getId(), s.getName(), Boolean.TRUE.equals(s.getIsActive()),
                s.getCreatedAt(), s.getUpdatedAt());
    }

    // ---- Slides ----

    @Transactional(readOnly = true)
    public List<AdminSlideResponse> listSlides(String sliderType, Long sliderId) {
        List<Slide> slides = CATEGORY_SLIDER_TYPE.equals(sliderType)
                ? slideRepository.findByRestaurantCategorySliderIdOrderByPositionIdAsc(sliderId.intValue())
                : slideRepository.findByPromoSliderIdOrderByPositionIdAsc(sliderId.intValue());
        return slides.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> countsBySliderType(String sliderType) {
        List<Slide> all = CATEGORY_SLIDER_TYPE.equals(sliderType)
                ? slideRepository.findByRestaurantCategorySliderIdIsNotNull()
                : slideRepository.findByPromoSliderIdIsNotNull();
        Map<Long, Long> counts = new HashMap<>();
        for (Slide s : all) {
            Long sliderId = CATEGORY_SLIDER_TYPE.equals(sliderType)
                    ? s.getRestaurantCategorySliderId().longValue()
                    : s.getPromoSliderId().longValue();
            counts.merge(sliderId, 1L, Long::sum);
        }
        return counts;
    }

    @Transactional
    public AdminSlideResponse createSlide(AdminSlideRequest request) {
        Slide slide = new Slide();
        applySlide(slide, request);
        slide.setCreatedAt(LocalDateTime.now());
        slide.setUpdatedAt(LocalDateTime.now());
        return toResponse(slideRepository.save(slide));
    }

    @Transactional
    public AdminSlideResponse updateSlide(Long id, AdminSlideRequest request) {
        Slide slide = findSlideOrThrow(id);
        applySlide(slide, request);
        slide.setUpdatedAt(LocalDateTime.now());
        return toResponse(slideRepository.save(slide));
    }

    @Transactional
    public void deleteSlide(Long id) {
        slideRepository.delete(findSlideOrThrow(id));
    }

    @Transactional
    public SlideImageResponse uploadSlideImage(Long slideId, MultipartFile file, Long uploadedBy) {
        Slide slide = findSlideOrThrow(slideId);
        String storageKey = mediaAssetService.upload(file, SLIDE_IMAGE_OWNER_TYPE, slideId, uploadedBy).storageKey();
        slide.setImage(storageKey);
        slide.setUpdatedAt(LocalDateTime.now());
        slideRepository.save(slide);
        return new SlideImageResponse(mediaUrlResolver.resolve(storageKey));
    }

    private Slide findSlideOrThrow(Long id) {
        return slideRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Slide not found: " + id));
    }

    private void applySlide(Slide slide, AdminSlideRequest request) {
        if (CATEGORY_SLIDER_TYPE.equals(request.sliderType())) {
            slide.setRestaurantCategorySliderId(request.sliderId().intValue());
            slide.setPromoSliderId(null);
        } else {
            slide.setPromoSliderId(request.sliderId().intValue());
            slide.setRestaurantCategorySliderId(null);
        }
        slide.setUniqueId(request.uniqueId());
        slide.setName(request.name());
        slide.setDescription(request.description());
        if (request.image() != null) {
            slide.setImage(request.image());
        }
        slide.setImagePlaceholder(request.imagePlaceholder());
        slide.setLinkType(request.linkType());
        slide.setCategoryId(request.categoryId() != null ? request.categoryId().intValue() : null);
        slide.setRestaurantId(request.restaurantId() != null ? request.restaurantId().intValue() : null);
        slide.setUrl(request.url());
        slide.setPositionId(request.positionId() != null ? request.positionId() : 1);
        slide.setIsActive(request.isActive() == null || request.isActive());
    }

    private AdminSlideResponse toResponse(Slide s) {
        boolean isCategory = s.getRestaurantCategorySliderId() != null;
        Long sliderId = isCategory
                ? s.getRestaurantCategorySliderId().longValue()
                : (s.getPromoSliderId() != null ? s.getPromoSliderId().longValue() : null);
        return new AdminSlideResponse(s.getId(), isCategory ? CATEGORY_SLIDER_TYPE : "promo", sliderId, s.getUniqueId(),
                s.getName(), s.getDescription(), mediaUrlResolver.resolve(s.getImage()), s.getImagePlaceholder(),
                s.getLinkType(), s.getCategoryId() != null ? s.getCategoryId().longValue() : null,
                s.getRestaurantId() != null ? s.getRestaurantId().longValue() : null, s.getUrl(),
                s.getPositionId(), Boolean.TRUE.equals(s.getIsActive()), s.getCreatedAt(), s.getUpdatedAt());
    }
}
