package com.pureeats.user.service;

import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.user.dto.RiderProfileRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiderKycTest {

    private static RiderProfileRequest req(String idType, String idNumber, String vehicleType, String vehicleNumber, String payout) {
        return new RiderProfileRequest("Ravi Kumar", vehicleNumber, null, null, null, "KA05 2019 0001234", idType, idNumber, vehicleType, payout,
                "Ravi Kumar", "123456789012", "hdfc0001234", "ravi@okhdfcbank");
    }

    @Test
    void validBankApplication_isNormalised() {
        RiderProfileRequest r = req("AADHAAR", "1234 5678 9012", "bike", "ka-05-hh-1234", "BANK");
        assertDoesNotThrow(() -> RiderKyc.validate(r));
        DeliveryGuyDetail d = new DeliveryGuyDetail();
        RiderKyc.apply(d, r);
        assertEquals("KA0520190001234", d.getLicenseNumber());
        assertEquals("123456789012", d.getIdProofNumber());
        assertEquals("BIKE", d.getVehicleType());
        assertEquals("HDFC0001234", d.getBankIfsc());
        assertNull(d.getUpiId(), "only the chosen payout method is kept");
    }

    @Test
    void upiPayout_andCycleWithoutANumber_areFine() {
        assertDoesNotThrow(() -> RiderKyc.validate(req("PAN", "abcde1234f", "CYCLE", null, "UPI")));
    }

    @Test
    void invalidDetails_areRejectedWithAClearMessage() {
        assertThrows(BadRequestException.class, () -> RiderKyc.validate(req("AADHAAR", "1234", "BIKE", "KA05", "BANK")));
        assertThrows(BadRequestException.class, () -> RiderKyc.validate(req("PAN", "12345", "BIKE", "KA05", "BANK")));
        assertThrows(BadRequestException.class, () -> RiderKyc.validate(req("AADHAAR", "123456789012", "CAR", "KA05", "BANK")));
        assertThrows(BadRequestException.class, () -> RiderKyc.validate(req("AADHAAR", "123456789012", "BIKE", null, "BANK")), "bike needs a number");
        assertThrows(BadRequestException.class, () -> RiderKyc.validate(req("AADHAAR", "123456789012", "BIKE", "KA05", null)));
    }

    @Test
    void mask_keepsOnlyTheLastFour() {
        assertEquals("XXXXXXXX9012", RiderKyc.mask("123456789012"));
        assertNull(RiderKyc.mask(null));
    }
}
