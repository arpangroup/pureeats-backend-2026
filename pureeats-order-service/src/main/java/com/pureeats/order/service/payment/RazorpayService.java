package com.pureeats.order.service.payment;

import com.pureeats.catalog.service.AppConfigService;
import com.pureeats.domain.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Talks to Razorpay's REST API directly (Basic Auth with the key id/secret, no SDK dependency —
 * same lightweight-RestClient approach as HttpIpGeolocationService/NominatimReverseGeocodingService
 * in pureeats-user-service) rather than trusting anything the browser claims about a payment.
 * Three responsibilities, each deliberately narrow:
 *   1. createOrder — Razorpay requires an order be created server-side before Checkout opens, which
 *      is also what stops a customer from opening Checkout for an amount they made up client-side.
 *   2. verifySignature — Razorpay's HMAC proof that the payment_id/order_id pair Checkout handed
 *      back actually came from Razorpay, not a forged success callback.
 *   3. fetchCapturedAmount — the authoritative amount actually charged, fetched fresh from
 *      Razorpay's own records rather than trusted from anywhere in the request. OrderService
 *      compares this against its own freshly-computed payable before ever persisting the order.
 * The key id/secret are read from AppConfigService on every call (not cached at construction) since
 * they're admin-editable at runtime without a redeploy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayService {

    private final AppConfigService appConfigService;
    private final RestClient restClient = RestClient.builder().baseUrl("https://api.razorpay.com/v1").build();

    @SuppressWarnings("unchecked")
    public RazorpayOrderInfo createOrder(BigDecimal amount, String receipt) {
        String keyId = requireKeyId();
        String keySecret = requireSecret();
        long amountPaise = toPaise(amount);

        Map<String, Object> requestBody = Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt);
        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri("/orders")
                    .headers(h -> h.setBasicAuth(keyId, keySecret))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("Razorpay order creation failed: {}", e.getMessage());
            throw new BadRequestException("Could not start the Razorpay payment — please try again");
        }
        if (response == null || response.get("id") == null) {
            throw new BadRequestException("Could not start the Razorpay payment — please try again");
        }
        return new RazorpayOrderInfo((String) response.get("id"), amountPaise, "INR", keyId);
    }

    /** Constant-time comparison — this is exactly the kind of check a timing side-channel could otherwise leak. */
    public boolean verifySignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        if (razorpayOrderId == null || razorpayPaymentId == null || razorpaySignature == null) {
            return false;
        }
        try {
            String payload = razorpayOrderId + "|" + razorpayPaymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(requireSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), razorpaySignature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Razorpay signature check could not run: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public BigDecimal fetchCapturedAmount(String razorpayPaymentId) {
        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri("/payments/{id}", razorpayPaymentId)
                    .headers(h -> h.setBasicAuth(requireKeyId(), requireSecret()))
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("Could not fetch Razorpay payment {}: {}", razorpayPaymentId, e.getMessage());
            throw new BadRequestException("Could not verify the Razorpay payment");
        }
        if (response == null || response.get("amount") == null) {
            throw new BadRequestException("Could not verify the Razorpay payment");
        }
        long amountPaise = ((Number) response.get("amount")).longValue();
        return BigDecimal.valueOf(amountPaise, 2);
    }

    private long toPaise(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private String requireKeyId() {
        String keyId = appConfigService.getRazorpayKeyId();
        if (keyId == null || keyId.isBlank()) {
            throw new BadRequestException("Razorpay is not configured yet — set a key ID in Settings → Customer App");
        }
        return keyId;
    }

    private String requireSecret() {
        String secret = appConfigService.getRazorpaySecret();
        if (secret == null || secret.isBlank()) {
            throw new BadRequestException("Razorpay is not configured yet — set a secret key in Settings → Customer App");
        }
        return secret;
    }
}
