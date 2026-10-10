package com.pureeats.user.service;

import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.user.dto.RiderProfileRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiderKycTest {

    private static RiderProfileRequest req(String aadhaar, String pan, String vehicleType, String vehicleNumber, String payout) {
        return new RiderProfileRequest("Ravi Kumar", vehicleNumber, null, null, null, "KA05 2019 0001234", null, null, aadhaar, pan,
                vehicleType, payout, "Ravi Kumar", "123456789012", "hdfc0001234", "ravi@okhdfcbank");
    }

    @Test
    void validBankApplication_isNormalised() {
        RiderProfileRequest r = req("1234 5678 9012", "abcde1234f", "bike", "ka-05-hh-1234", "BANK");
        assertDoesNotThrow(() -> RiderKyc.validate(r));
        DeliveryGuyDetail d = new DeliveryGuyDetail();
        RiderKyc.apply(d, r);
        assertEquals("KA0520190001234", d.getLicenseNumber());
        assertEquals("123456789012", d.getAadhaarNumber());
        assertEquals("ABCDE1234F", d.getPanNumber());
        assertEquals("123456789012", d.getIdProofNumber(), "legacy column still filled for older readers");
        assertEquals("BIKE", d.getVehicleType());
        assertEquals("HDFC0001234", d.getBankIfsc());
        assertNull(d.getUpiId(), "only the chosen payout method is kept");
    }

    @Test
    void upiPayout_andCycleWithoutANumber_areFine() {
        assertDoesNotThrow(() -> RiderKyc.validate(req("123456789012", "ABCDE1234F", "CYCLE", null, "UPI")));
    }

    @Test
    void bothAadhaarAndPan_areRequired() {
        BadRequestException noPan = assertThrows(BadRequestException.class, () -> RiderKyc.validate(req("123456789012", null, "BIKE", "KA05", "BANK")));
        assertTrue(noPan.getMessage().contains("PAN"));
        BadRequestException noAadhaar = assertThrows(BadRequestException.class, () -> RiderKyc.validate(req(" ", "ABCDE1234F", "BIKE", "KA05", "BANK")));
        assertTrue(noAadhaar.getMessage().contains("Aadhaar"));
    }

    @Test
    void invalidDetails_areRejectedWithAClearMessage() {
        assertThrows(BadRequestException.class, () -> RiderKyc.validate(req("1234", "ABCDE1234F", "BIKE", "KA05", "BANK")));
        assertThrows(BadRequestException.class, () -> RiderKyc.validate(req("123456789012", "12345", "BIKE", "KA05", "BANK")));
        assertThrows(BadRequestException.class, () -> RiderKyc.validate(req("123456789012", "ABCDE1234F", "CAR", "KA05", "BANK")));
        assertThrows(BadRequestException.class, () -> RiderKyc.validate(req("123456789012", "ABCDE1234F", "BIKE", null, "BANK")), "bike needs a number");
        assertThrows(BadRequestException.class, () -> RiderKyc.validate(req("123456789012", "ABCDE1234F", "BIKE", "KA05", null)));
    }

    @Test
    void legacySingleIdProof_isReadAsAadhaarOrPan() {
        DeliveryGuyDetail d = new DeliveryGuyDetail();
        d.setIdProofType("PAN");
        d.setIdProofNumber("ABCDE1234F");
        assertEquals("ABCDE1234F", d.effectivePan());
        assertNull(d.effectiveAadhaar(), "an older partner with only a PAN has no Aadhaar on file");
    }

    @Test
    void mask_keepsOnlyTheLastFour() {
        assertEquals("XXXXXXXX9012", RiderKyc.mask("123456789012"));
        assertNull(RiderKyc.mask(null));
    }
}
