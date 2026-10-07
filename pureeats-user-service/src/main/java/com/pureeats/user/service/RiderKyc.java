package com.pureeats.user.service;

import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.user.dto.RiderProfileRequest;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Validation and normalisation of a delivery partner's sign-up details (licence, ID proof, vehicle, payout). */
final class RiderKyc {

    private static final Set<String> VEHICLE_TYPES = Set.of("BIKE", "CYCLE", "EV");
    private static final Pattern AADHAAR = Pattern.compile("\\d{12}");
    private static final Pattern PAN = Pattern.compile("[A-Z]{5}\\d{4}[A-Z]");
    /** Indian DL: state code + RTO + year + serial, e.g. KA0520190001234 - kept lenient on separators. */
    private static final Pattern LICENSE = Pattern.compile("[A-Z]{2}\\d{2}[A-Z0-9]{8,14}");
    private static final Pattern IFSC = Pattern.compile("[A-Z]{4}0[A-Z0-9]{6}");
    private static final Pattern ACCOUNT = Pattern.compile("\\d{9,18}");
    private static final Pattern UPI = Pattern.compile("[A-Za-z0-9._-]{2,256}@[A-Za-z]{2,64}");

    private RiderKyc() {
    }

    static void validate(RiderProfileRequest r) {
        String license = compact(r.licenseNumber());
        if (license == null || !LICENSE.matcher(license).matches()) {
            throw new BadRequestException("Enter a valid driving licence number (e.g. KA0520190001234).");
        }
        String idType = upper(r.idProofType());
        String idNumber = compact(r.idProofNumber());
        if ("AADHAAR".equals(idType)) {
            if (idNumber == null || !AADHAAR.matcher(idNumber).matches()) throw new BadRequestException("Aadhaar number must be 12 digits.");
        } else if ("PAN".equals(idType)) {
            if (idNumber == null || !PAN.matcher(idNumber).matches()) throw new BadRequestException("Enter a valid PAN (e.g. ABCDE1234F).");
        } else {
            throw new BadRequestException("Choose Aadhaar or PAN as your ID proof.");
        }
        if (!VEHICLE_TYPES.contains(upper(r.vehicleType()))) {
            throw new BadRequestException("Choose your vehicle type: Bike, Cycle or EV.");
        }
        // A cycle has no registration number; motor vehicles must have one.
        if (!"CYCLE".equals(upper(r.vehicleType())) && (r.vehicleNumber() == null || r.vehicleNumber().isBlank())) {
            throw new BadRequestException("Enter your vehicle number.");
        }
        String method = upper(r.payoutMethod());
        if ("BANK".equals(method)) {
            if (r.bankAccountHolder() == null || r.bankAccountHolder().isBlank()) throw new BadRequestException("Enter the bank account holder's name.");
            String account = compact(r.bankAccountNumber());
            if (account == null || !ACCOUNT.matcher(account).matches()) throw new BadRequestException("Enter a valid bank account number.");
            String ifsc = compact(r.bankIfsc());
            if (ifsc == null || !IFSC.matcher(ifsc).matches()) throw new BadRequestException("Enter a valid IFSC (e.g. HDFC0001234).");
        } else if ("UPI".equals(method)) {
            if (r.upiId() == null || !UPI.matcher(r.upiId().trim()).matches()) throw new BadRequestException("Enter a valid UPI ID (e.g. name@okhdfcbank).");
        } else {
            throw new BadRequestException("Choose how you want to be paid: bank account or UPI.");
        }
    }

    static void apply(DeliveryGuyDetail d, RiderProfileRequest r) {
        d.setLicenseNumber(compact(r.licenseNumber()));
        d.setIdProofType(upper(r.idProofType()));
        d.setIdProofNumber(compact(r.idProofNumber()));
        d.setVehicleType(upper(r.vehicleType()));
        if (r.vehicleNumber() != null && !r.vehicleNumber().isBlank()) d.setVehicleNumber(r.vehicleNumber().trim().toUpperCase(Locale.ROOT));
        String method = upper(r.payoutMethod());
        d.setPayoutMethod(method);
        boolean bank = "BANK".equals(method);
        d.setBankAccountHolder(bank ? r.bankAccountHolder().trim() : null);
        d.setBankAccountNumber(bank ? compact(r.bankAccountNumber()) : null);
        d.setBankIfsc(bank ? compact(r.bankIfsc()) : null);
        d.setUpiId(bank ? null : r.upiId().trim());
    }

    /** Everything but the last 4 characters hidden: XXXXXXXX1234. */
    static String mask(String value) {
        if (value == null || value.isBlank()) return null;
        int keep = Math.min(4, value.length());
        return "X".repeat(value.length() - keep) + value.substring(value.length() - keep);
    }

    private static String compact(String v) {
        return v == null ? null : v.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private static String upper(String v) {
        return v == null ? null : v.trim().toUpperCase(Locale.ROOT);
    }
}
