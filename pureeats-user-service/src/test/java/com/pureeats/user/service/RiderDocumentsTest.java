package com.pureeats.user.service;

import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.domain.entity.User;
import com.pureeats.user.dto.RiderDocumentResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RiderDocumentsTest {

    private final RiderDocuments documents = new RiderDocuments(null, null);

    private static DeliveryGuyDetail partner(String vehicle, String payout) {
        DeliveryGuyDetail d = new DeliveryGuyDetail();
        d.setVehicleType(vehicle);
        d.setPayoutMethod(payout);
        return d;
    }

    private static RiderDocumentResponse doc(String type) {
        return new RiderDocumentResponse(type, type, "http://x/" + type, "image/jpeg", LocalDateTime.now());
    }

    @Test
    void bikeWithBankPayout_needsLicenceAadhaarBothSidesPanRcAndPassbook() {
        assertEquals(List.of("LICENSE", "AADHAAR_FRONT", "AADHAAR_BACK", "PAN", "RC", "PASSBOOK"), RiderDocuments.required(partner("BIKE", "BANK")));
    }

    @Test
    void cycleWithUpi_needsNoRcAndNoPassbook() {
        assertEquals(List.of("LICENSE", "AADHAAR_FRONT", "AADHAAR_BACK", "PAN"), RiderDocuments.required(partner("CYCLE", "UPI")));
    }

    @Test
    void missing_listsThePhotoAndEachDocumentNotUploaded() {
        DeliveryGuyDetail d = partner("EV", "UPI");
        Map<String, RiderDocumentResponse> uploaded = Map.of("LICENSE", doc("LICENSE"), "AADHAAR_FRONT", doc("AADHAAR_FRONT"), "PAN", doc("PAN"));

        assertEquals(List.of("PHOTO", "AADHAAR_BACK", "RC"), documents.missing(d, new User(), uploaded));

        d.setPhoto("delivery_guy/me.jpg");
        assertEquals(List.of("AADHAAR_BACK", "RC"), documents.missing(d, new User(), uploaded), "the photo is on file now");
    }

    @Test
    void documentTypes_areValidatedCaseInsensitively() {
        assertEquals("AADHAAR_FRONT", RiderDocuments.normalize("aadhaar-front"));
        assertThrows(BadRequestException.class, () -> RiderDocuments.normalize("selfie"));
    }

    @Test
    void theOtpSignUpPlaceholderName_isNotAFullName() {
        assertThrows(BadRequestException.class, () -> RiderKyc.validateName("PureEats User"));
        assertThrows(BadRequestException.class, () -> RiderKyc.validateName(" "));
        assertDoesNotThrow(() -> RiderKyc.validateName("Ravi Kumar"));
    }

    @Test
    void placeholderEmails_areHidden() {
        assertNull(UserProvisioningService.displayEmail("8655225588@otp.pureeats.local"));
        assertEquals("ravi@example.com", UserProvisioningService.displayEmail("ravi@example.com"));
    }
}
