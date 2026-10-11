package com.pureeats.user.service;

import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.domain.entity.User;
import com.pureeats.media.entity.MediaAsset;
import com.pureeats.media.service.MediaAssetService;
import com.pureeats.media.storage.MediaUrlResolver;
import com.pureeats.user.dto.RiderDocumentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A delivery partner's uploaded documents - stored as media assets owned by the DeliveryGuyDetail id (no extra
 * table), one owner type per document; the latest upload of each counts. Which ones are required depends on the
 * application: licence, Aadhaar (front + back) and PAN always; the vehicle RC unless it's a cycle; the bank
 * passbook / cancelled cheque when paid to a bank account. The partner's own photo is required too (see
 * {@link #missing}). Admin approval is refused while anything is missing.
 */
@Component
@RequiredArgsConstructor
public class RiderDocuments {

    public static final String LICENSE = "LICENSE";
    public static final String AADHAAR_FRONT = "AADHAAR_FRONT";
    public static final String AADHAAR_BACK = "AADHAAR_BACK";
    public static final String PAN = "PAN";
    public static final String RC = "RC";
    public static final String PASSBOOK = "PASSBOOK";
    /** Not an uploaded document - the partner's profile photo, listed in {@link #missing} so it's required too. */
    public static final String PHOTO = "PHOTO";

    /** Every document type, in the order the app and the admin panel show them. */
    public static final List<String> TYPES = List.of(LICENSE, AADHAAR_FRONT, AADHAAR_BACK, PAN, RC, PASSBOOK);

    private static final Map<String, String> LABELS = Map.of(
            LICENSE, "Driving licence photo",
            AADHAAR_FRONT, "Aadhaar (front)",
            AADHAAR_BACK, "Aadhaar (back)",
            PAN, "PAN card",
            RC, "Vehicle RC",
            PASSBOOK, "Bank passbook / cancelled cheque",
            PHOTO, "Profile photo");

    private final MediaAssetService mediaAssetService;
    private final MediaUrlResolver mediaUrlResolver;

    /** Validates a type from the URL ({@code /documents/{type}}); accepts any case. */
    public static String normalize(String type) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!TYPES.contains(t)) {
            throw new BadRequestException("Unknown document type '" + type + "' - expected one of " + String.join(", ", TYPES));
        }
        return t;
    }

    public static String label(String type) {
        return LABELS.getOrDefault(type, type);
    }

    /** The licence keeps the owner type it always had, so photos uploaded before this still count. */
    static String ownerType(String type) {
        return LICENSE.equals(type) ? RiderService.OWNER_TYPE_LICENSE : "DELIVERY_GUY_DOC_" + type;
    }

    public void upload(DeliveryGuyDetail detail, String type, org.springframework.web.multipart.MultipartFile file, Long uploadedBy) {
        mediaAssetService.uploadDocument(file, ownerType(type), detail.getId(), uploadedBy);
    }

    /** The latest upload of each document type that has one. */
    public Map<String, RiderDocumentResponse> uploaded(Long deliveryGuyDetailId) {
        Map<String, RiderDocumentResponse> out = new LinkedHashMap<>();
        if (deliveryGuyDetailId == null) return out;
        for (String type : TYPES) {
            mediaAssetService.listForOwner(ownerType(type), deliveryGuyDetailId).stream()
                    .max(Comparator.comparing(MediaAsset::getCreatedAt))
                    .ifPresent(a -> out.put(type, new RiderDocumentResponse(type, label(type),
                            mediaUrlResolver.resolve(a.getStorageKey()), a.getContentType(), a.getCreatedAt())));
        }
        return out;
    }

    /** Documents this application needs: always licence, Aadhaar front + back and PAN; RC unless a cycle; passbook for a bank payout. */
    public static List<String> required(DeliveryGuyDetail d) {
        List<String> req = new ArrayList<>(List.of(LICENSE, AADHAAR_FRONT, AADHAAR_BACK, PAN));
        if (!"CYCLE".equalsIgnoreCase(d.getVehicleType())) req.add(RC);
        if ("BANK".equalsIgnoreCase(d.getPayoutMethod())) req.add(PASSBOOK);
        return req;
    }

    /** What's still needed before this partner can be approved - document types plus {@link #PHOTO}. Empty = complete. */
    public List<String> missing(DeliveryGuyDetail d, User user, Map<String, RiderDocumentResponse> uploaded) {
        List<String> out = new ArrayList<>();
        boolean hasPhoto = (d.getPhoto() != null && !d.getPhoto().isBlank()) || (user != null && user.getPhoto() != null && !user.getPhoto().isBlank());
        if (!hasPhoto) out.add(PHOTO);
        for (String type : required(d)) {
            if (!uploaded.containsKey(type)) out.add(type);
        }
        return out;
    }
}
