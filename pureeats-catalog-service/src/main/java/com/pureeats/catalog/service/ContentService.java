package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.*;
import com.pureeats.catalog.repository.*;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.Slide;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {

    private final PageRepository pageRepository;
    private final SettingRepository settingRepository;
    private final PromoSliderRepository promoSliderRepository;
    private final SlideRepository slideRepository;
    private final TranslationRepository translationRepository;
    private final PaymentGatewayRepository paymentGatewayRepository;

    @Transactional(readOnly = true)
    public List<PageResponse> listPages() {
        return pageRepository.findAll().stream()
                .map(p -> new PageResponse(p.getId(), p.getName(), p.getSlug(), p.getBody())).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse getPage(String slug) {
        log.debug("Fetching CMS page '{}'", slug);
        var page = pageRepository.findBySlug(slug)
                .orElseThrow(() -> {
                    log.warn("CMS page '{}' not found", slug);
                    return new ResourceNotFoundException("Page not found: " + slug);
                });
        return new PageResponse(page.getId(), page.getName(), page.getSlug(), page.getBody());
    }

    @Transactional(readOnly = true)
    public Map<String, String> getPublicSettings() {
        Map<String, String> settings = new HashMap<>();
        settingRepository.findAll().forEach(s -> settings.put(s.getKey(), s.getValue()));
        return settings;
    }

    @Transactional(readOnly = true)
    public List<PromoSliderResponse> listPromoSliders() {
        return promoSliderRepository.findByIsActiveTrue().stream()
                .map(slider -> new PromoSliderResponse(slider.getId(), slider.getName(), slider.getPositionId(),
                        slider.getSize(), slideRepository.findByPromoSliderIdAndIsActiveTrue(slider.getId().intValue())
                        .stream().map(this::toSlideResponse).toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LanguageResponse> listLanguages() {
        return translationRepository.findByIsActiveTrue().stream()
                .map(t -> new LanguageResponse(t.getId(), t.getLanguageName(), Boolean.TRUE.equals(t.getIsDefault())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentGatewayResponse> listPaymentGateways() {
        return paymentGatewayRepository.findByIsActiveTrue().stream()
                .map(g -> new PaymentGatewayResponse(g.getId(), g.getName(), g.getLogo())).toList();
    }

    private SlideResponse toSlideResponse(Slide s) {
        return new SlideResponse(s.getId(), s.getName(), s.getImage(), s.getImagePlaceholder(), s.getUrl());
    }
}
