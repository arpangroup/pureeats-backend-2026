package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.*;
import com.pureeats.catalog.repository.*;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.Setting;
import com.pureeats.domain.entity.Slide;
import com.pureeats.media.storage.MediaUrlResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final MediaUrlResolver mediaUrlResolver;
    private final SettingSchemaService settingSchemaService;
    private final SettingHistoryService settingHistoryService;

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

    /**
     * Upserts each key - creates the row if it doesn't exist yet, updates it otherwise. Generic
     * key/value on purpose (see the {@link Setting} entity): every setting behind this endpoint is
     * currently just a bare admin-configurable string with no working integration reading it yet
     * (Stripe/PayPal/Twilio/etc. credential placeholders, display toggles, ...) - a dedicated typed
     * entity per feature would be the better call once one of those actually gets built out, at
     * which point that feature's own service is the natural place to move its settings onto real
     * columns. AppConfig (a single structured JSON blob under one key) is the other point on this
     * spectrum, for config that's already grown real shape (feature flags, Razorpay/Firebase creds).
     *
     * <p>Every incoming key is checked against {@link SettingSchemaService#validKeys()} first - the
     * same registry the admin panel renders its form fields from - and rejected wholesale (nothing
     * in the batch is saved) if any key isn't recognized. That's what makes the schema the actual
     * contract rather than just documentation: a client can't persist a setting the backend hasn't
     * declared, and conversely a field declared in the schema is always immediately saveable, no
     * separate allow-list to keep in sync.
     *
     * <p>{@code updatedBy} is the acting admin's user id (from the controller's
     * {@code @AuthenticationPrincipal}) - each key that actually changes value gets one row in
     * {@link SettingHistory} via {@link SettingHistoryService#record}, which itself no-ops for a key
     * whose "new" value is the same as what was already stored (a re-save with no real change logs
     * nothing).
     */
    @Transactional
    public Map<String, String> updateSettings(Map<String, String> updates, Long updatedBy) {
        Set<String> validKeys = settingSchemaService.validKeys();
        Set<String> unknownKeys = updates.keySet().stream().filter(key -> !validKeys.contains(key)).collect(Collectors.toSet());
        if (!unknownKeys.isEmpty()) {
            throw new BadRequestException("Unknown setting key(s): " + String.join(", ", unknownKeys));
        }
        updates.forEach((key, value) -> {
            Setting setting = settingRepository.findByKey(key).orElseGet(() -> {
                Setting created = new Setting();
                created.setKey(key);
                return created;
            });
            settingHistoryService.record(SettingHistoryService.SOURCE_SETTING, key, setting.getValue(), value, updatedBy);
            setting.setValue(value);
            settingRepository.save(setting);
        });
        return getPublicSettings();
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
        // s.getImage() is a storage key (see MediaAssetService/AdminSliderService.uploadSlideImage),
        // not a browsable URL - must go through MediaUrlResolver same as every other image field,
        // or the client gets a bare "slide/xxx.jpg" it can't load. (MediaUrlResolver also passes an
        // already-absolute URL/data: URI straight through, so this is safe for any older row that
        // has one of those stored directly instead of a key.)
        return new SlideResponse(s.getId(), s.getName(), mediaUrlResolver.resolve(s.getImage()), s.getImagePlaceholder(), resolveSlideUrl(s));
    }

    /**
     * The admin side models a slide's click target as {@code linkType} ("none"/"category"/
     * "restaurant"/"url") plus whichever of {@code categoryId}/{@code restaurantId}/{@code url}
     * matches - but the raw {@code url} column is only ever populated for {@code linkType == "url"}.
     * The customer app's {@code PromoSlider} only ever looks at a single {@code url} string (null =
     * not clickable), so for the category/restaurant cases we derive that string here from the id,
     * matching the customer app's own route shapes ({@code /restaurants/:id}, {@code /category/:id})
     * instead of ever exposing linkType/categoryId/restaurantId to the client.
     */
    private String resolveSlideUrl(Slide s) {
        if (s.getLinkType() == null) {
            return s.getUrl();
        }
        return switch (s.getLinkType()) {
            case "restaurant" -> s.getRestaurantId() != null ? "/restaurants/" + s.getRestaurantId() : null;
            case "category" -> s.getCategoryId() != null ? "/category/" + s.getCategoryId() : null;
            case "url" -> s.getUrl();
            default -> null;
        };
    }
}
