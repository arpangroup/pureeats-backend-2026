package com.pureeats.app.seeder;

import com.pureeats.catalog.repository.PaymentGatewayRepository;
import com.pureeats.catalog.repository.PromoSliderRepository;
import com.pureeats.catalog.repository.SlideRepository;
import com.pureeats.domain.entity.PaymentGateway;
import com.pureeats.domain.entity.PromoSlider;
import com.pureeats.domain.entity.Slide;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Seeds one Home page promo slider with 3 banner slides, so GET /api/v1/promo-sliders has
 * something to return in {@code uat}/live mode — nothing seeded this before (only the admin CRUD
 * endpoints in AdminSliderService wrote these tables), which is why the Home page's promo slider
 * was empty until an admin manually created one.
 * <p>
 * The 3 banner images live at {@code uploads/slide/<uuid>.jpg} (same convention
 * MediaAssetService/AdminSliderService.uploadSlideImage uses: storage key {@code "slide/<uuid>.ext"},
 * resolved to a URL via MediaUrlResolver, never a bare key returned to the client — see
 * ContentService.toSlideResponse). Idempotent like the other demo seeders: guarded on
 * {@link Slide#getUniqueId()}, a natural key that exists specifically for this purpose, so it's
 * safe to leave running on every restart.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(4)
public class DemoContentSeeder implements ApplicationRunner {

    private static final String SLIDER_NAME = "Demo Home Banners";

    private record BannerSeed(String uniqueId, String name, String description, String imageFile, int position) {
    }

    private static final List<BannerSeed> BANNERS = List.of(
            new BannerSeed("demo-promo-slide-1", "50% OFF Your First Order", "Use code WELCOME50 at checkout",
                    "9f1324b8-aa1d-4689-8882-231036a8d62c.jpg", 1),
            new BannerSeed("demo-promo-slide-2", "Free Delivery This Weekend", "No minimum order, every restaurant",
                    "a204799e-6535-4dfa-ab64-ee1bac2aab7b.jpg", 2),
            new BannerSeed("demo-promo-slide-3", "New Restaurants Just Added", "Explore fresh flavors near you",
                    "cdb23e9a-bcac-43a2-a105-3e657cc03a1f.jpg", 3)
    );

    /** Null {@code code} means "not wired to an actual checkout flow yet" - the customer app shows these disabled rather than as a selectable payment option (see CheckoutPage.tsx). */
    private record GatewaySeed(String code, String name, String description) {
    }

    /**
     * COD/WALLET/UPI/RAZORPAY match the customer app's PaymentMode exactly and are real, selectable
     * checkout options. Razorpay is genuinely implemented (RazorpayService, signature verification,
     * the works) - unlike the placeholders below, it's only greyed out on Checkout when an admin
     * hasn't set a key yet (Settings → Payment Gateways → Razorpay / RazorpayConfigPanel), not
     * because the flow doesn't exist. The rest (Stripe/PayPal/PayStack/PayTm/PayUmoney/CCAvenue) are
     * placeholders for gateways not integrated at all yet - listed so admin can see what's planned.
     */
    private static final List<GatewaySeed> GATEWAYS = List.of(
            new GatewaySeed("COD", "Cash on Delivery", "Pay with cash when your order arrives"),
            new GatewaySeed("WALLET", "PureEats Wallet", "Pay using your wallet balance"),
            new GatewaySeed("UPI", "UPI", "Pay via GPay, PhonePe, Paytm & more"),
            new GatewaySeed("RAZORPAY", "Razorpay", "Cards, UPI, netbanking and wallets"),
            new GatewaySeed(null, "Stripe", "International cards - not yet integrated"),
            new GatewaySeed(null, "PayPal", "Not yet integrated"),
            new GatewaySeed(null, "PayStack", "Not yet integrated"),
            new GatewaySeed(null, "PayTm", "Not yet integrated"),
            new GatewaySeed(null, "PayUmoney", "Not yet integrated"),
            new GatewaySeed(null, "CCAvenue", "Not yet integrated")
    );

    private final PromoSliderRepository promoSliderRepository;
    private final SlideRepository slideRepository;
    private final PaymentGatewayRepository paymentGatewayRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedPaymentGateways();
        PromoSlider slider = promoSliderRepository.findAll().stream()
                .filter(s -> SLIDER_NAME.equals(s.getName())).findFirst()
                .orElseGet(() -> {
                    PromoSlider s = new PromoSlider();
                    s.setName(SLIDER_NAME);
                    s.setIsActive(true);
                    s.setPositionId(1);
                    s.setSize(2); // "medium" — see AdminSliderService.SIZE_TO_INT
                    s.setCreatedAt(LocalDateTime.now());
                    s.setUpdatedAt(LocalDateTime.now());
                    return promoSliderRepository.save(s);
                });

        int created = 0;
        for (BannerSeed banner : BANNERS) {
            boolean exists = slideRepository.findByPromoSliderIdOrderByPositionIdAsc(slider.getId().intValue()).stream()
                    .anyMatch(s -> banner.uniqueId().equals(s.getUniqueId()));
            if (exists) continue;

            Slide slide = new Slide();
            slide.setPromoSliderId(slider.getId().intValue());
            slide.setUniqueId(banner.uniqueId());
            slide.setName(banner.name());
            slide.setDescription(banner.description());
            slide.setImage("slide/" + banner.imageFile());
            slide.setPositionId(banner.position());
            slide.setIsActive(true);
            slide.setCreatedAt(LocalDateTime.now());
            slide.setUpdatedAt(LocalDateTime.now());
            slideRepository.save(slide);
            created++;
        }
        log.info("Demo content seeding complete: promo slider '{}' ({} new slide(s) created)", SLIDER_NAME, created);
    }

    /**
     * Nothing else writes payment_gateways (no create endpoint exists - only the toggle) - without
     * this the table stays empty forever, so Settings → Payment gateways has nothing to show, and
     * GET /payment-gateways (which the customer app's checkout now reads) returns nothing either.
     * Matched on {@code code} where set, else {@code name} (the decorative placeholder rows have no
     * code). A matched row's name/description are kept in sync with GATEWAYS on every restart -
     * this list is still actively evolving, so a demo row shouldn't go stale after a rename here
     * (e.g. "UPI / Razorpay" splitting into separate "UPI" and "Razorpay" rows) - only isActive is
     * left alone, since that's the one field an admin actually edits.
     */
    private void seedPaymentGateways() {
        int created = 0;
        int updated = 0;
        List<PaymentGateway> existing = paymentGatewayRepository.findAll();
        for (GatewaySeed seed : GATEWAYS) {
            PaymentGateway match = existing.stream()
                    .filter(g -> seed.code() != null ? seed.code().equals(g.getCode()) : seed.name().equals(g.getName()))
                    .findFirst().orElse(null);

            if (match != null) {
                if (!seed.name().equals(match.getName()) || !seed.description().equals(match.getDescription())) {
                    match.setName(seed.name());
                    match.setDescription(seed.description());
                    match.setUpdatedAt(LocalDateTime.now());
                    paymentGatewayRepository.save(match);
                    updated++;
                }
                continue;
            }

            PaymentGateway gateway = new PaymentGateway();
            gateway.setCode(seed.code());
            gateway.setName(seed.name());
            gateway.setDescription(seed.description());
            // Real, selectable options start on; not-yet-integrated placeholders start off, same as
            // the admin app's mock fixtures — an admin can flip either on/off any time regardless.
            gateway.setIsActive(seed.code() != null);
            gateway.setCreatedAt(LocalDateTime.now());
            gateway.setUpdatedAt(LocalDateTime.now());
            paymentGatewayRepository.save(gateway);
            created++;
        }
        if (created > 0 || updated > 0) {
            log.info("Demo payment gateway seeding complete: {} new, {} updated", created, updated);
        }
    }
}
