package com.pureeats.user.service;

import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.user.dto.RiderProfileRequest;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validation and normalisation of a delivery partner's sign-up details, in four groups that can also be
 * changed one at a time later (by the partner when Settings -> Profile editing allows it, or by an admin):
 * driving licence, ID proof (both Aadhaar AND PAN), vehicle, and payout (bank or UPI).
 */
public final class RiderKyc {

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

    /** A full application: every group is required. */
    static void validate(RiderProfileRequest r) {
        validateLicense(r.licenseNumber());
        validateAadhaar(r.aadhaarNumber());
        validatePan(r.panNumber());
        validateVehicle(r.vehicleType(), r.vehicleNumber());
        validatePayout(r.payoutMethod(), r.bankAccountHolder(), r.bankAccountNumber(), r.bankIfsc(), r.upiId());
    }

    static void apply(DeliveryGuyDetail d, RiderProfileRequest r) {
        applyLicense(d, r.licenseNumber());
        applyAadhaar(d, r.aadhaarNumber());
        applyPan(d, r.panNumber());
        applyVehicle(d, r.vehicleType(), r.vehicleNumber());
        applyPayout(d, r.payoutMethod(), r.bankAccountHolder(), r.bankAccountNumber(), r.bankIfsc(), r.upiId());
    }

    public static void validateLicense(String licenseNumber) {
        String license = compact(licenseNumber);
        if (license == null || !LICENSE.matcher(license).matches()) {
            throw new BadRequestException("Enter a valid driving licence number (e.g. KA0520190001234).");
        }
    }

    public static void validateIdProof(String idProofType, String idProofNumber) {
        String idType = upper(idProofType);
        String idNumber = compact(idProofNumber);
        if ("AADHAAR".equals(idType)) {
            if (idNumber == null || !AADHAAR.matcher(idNumber).matches()) throw new BadRequestException("Aadhaar number must be 12 digits.");
        } else if ("PAN".equals(idType)) {
            if (idNumber == null || !PAN.matcher(idNumber).matches()) throw new BadRequestException("Enter a valid PAN (e.g. ABCDE1234F).");
        } else {
            throw new BadRequestException("Choose Aadhaar or PAN as your ID proof.");
        }
    }

    public static void validateAadhaar(String aadhaarNumber) {
        String id = compact(aadhaarNumber);
        if (id == null || id.isEmpty()) throw new BadRequestException("Enter your Aadhaar number - both Aadhaar and PAN are required.");
        if (!AADHAAR.matcher(id).matches()) throw new BadRequestException("Aadhaar number must be 12 digits.");
    }

    public static void validatePan(String panNumber) {
        String id = compact(panNumber);
        if (id == null || id.isEmpty()) throw new BadRequestException("Enter your PAN - both Aadhaar and PAN are required.");
        if (!PAN.matcher(id).matches()) throw new BadRequestException("Enter a valid PAN (e.g. ABCDE1234F).");
    }

    public static void validateVehicle(String vehicleType, String vehicleNumber) {
        if (!VEHICLE_TYPES.contains(upper(vehicleType))) {
            throw new BadRequestException("Choose your vehicle type: Bike, Cycle or EV.");
        }
        // A cycle has no registration number; motor vehicles must have one.
        if (!"CYCLE".equals(upper(vehicleType)) && (vehicleNumber == null || vehicleNumber.isBlank())) {
            throw new BadRequestException("Enter your vehicle number.");
        }
    }

    public static void validatePayout(String payoutMethod, String holder, String accountNumber, String ifsc, String upiId) {
        String method = upper(payoutMethod);
        if ("BANK".equals(method)) {
            if (holder == null || holder.isBlank()) throw new BadRequestException("Enter the bank account holder's name.");
            String account = compact(accountNumber);
            if (account == null || !ACCOUNT.matcher(account).matches()) throw new BadRequestException("Enter a valid bank account number.");
            String code = compact(ifsc);
            if (code == null || !IFSC.matcher(code).matches()) throw new BadRequestException("Enter a valid IFSC (e.g. HDFC0001234).");
        } else if ("UPI".equals(method)) {
            if (upiId == null || !UPI.matcher(upiId.trim()).matches()) throw new BadRequestException("Enter a valid UPI ID (e.g. name@okhdfcbank).");
        } else {
            throw new BadRequestException("Choose how you want to be paid: bank account or UPI.");
        }
    }

    public static void applyLicense(DeliveryGuyDetail d, String licenseNumber) {
        d.setLicenseNumber(compact(licenseNumber));
    }

    public static void applyIdProof(DeliveryGuyDetail d, String idProofType, String idProofNumber) {
        d.setIdProofType(upper(idProofType));
        d.setIdProofNumber(compact(idProofNumber));
    }

    /** Also kept in the legacy single ID-proof columns so older readers still see an ID. */
    public static void applyAadhaar(DeliveryGuyDetail d, String aadhaarNumber) {
        d.setAadhaarNumber(compact(aadhaarNumber));
        d.setIdProofType("AADHAAR");
        d.setIdProofNumber(d.getAadhaarNumber());
    }

    public static void applyPan(DeliveryGuyDetail d, String panNumber) {
        d.setPanNumber(compact(panNumber));
    }

    public static void applyVehicle(DeliveryGuyDetail d, String vehicleType, String vehicleNumber) {
        d.setVehicleType(upper(vehicleType));
        if (vehicleNumber != null && !vehicleNumber.isBlank()) d.setVehicleNumber(vehicleNumber.trim().toUpperCase(Locale.ROOT));
    }

    public static void applyPayout(DeliveryGuyDetail d, String payoutMethod, String holder, String accountNumber, String ifsc, String upiId) {
        String method = upper(payoutMethod);
        d.setPayoutMethod(method);
        boolean bank = "BANK".equals(method);
        d.setBankAccountHolder(bank ? holder.trim() : null);
        d.setBankAccountNumber(bank ? compact(accountNumber) : null);
        d.setBankIfsc(bank ? compact(ifsc) : null);
        d.setUpiId(bank ? null : upiId.trim());
    }

    /** True when the requested licence differs from what's on file (null = not sent = unchanged). */
    static boolean licenseChanged(DeliveryGuyDetail d, String licenseNumber) {
        return licenseNumber != null && !Objects.equals(compact(licenseNumber), d.getLicenseNumber());
    }

    static boolean aadhaarChanged(DeliveryGuyDetail d, String number) {
        return number != null && !Objects.equals(compact(number), d.effectiveAadhaar());
    }

    static boolean panChanged(DeliveryGuyDetail d, String number) {
        return number != null && !Objects.equals(compact(number), d.effectivePan());
    }

    static boolean vehicleTypeChanged(DeliveryGuyDetail d, String type) {
        return type != null && !Objects.equals(upper(type), d.getVehicleType());
    }

    static boolean payoutChanged(DeliveryGuyDetail d, String method, String accountNumber, String ifsc, String upiId, String holder) {
        if (method == null) return false;
        if (!Objects.equals(upper(method), d.getPayoutMethod())) return true;
        if ("UPI".equals(upper(method))) return upiId != null && !Objects.equals(upiId.trim(), d.getUpiId());
        return (accountNumber != null && !Objects.equals(compact(accountNumber), d.getBankAccountNumber()))
                || (ifsc != null && !Objects.equals(compact(ifsc), d.getBankIfsc()))
                || (holder != null && !Objects.equals(holder.trim(), d.getBankAccountHolder()));
    }

    /** Everything but the last 4 characters hidden: XXXXXXXX1234. */
    public static String mask(String value) {
        if (value == null || value.isBlank()) return null;
        int keep = Math.min(4, value.length());
        return "X".repeat(value.length() - keep) + value.substring(value.length() - keep);
    }

    static String compact(String v) {
        return v == null ? null : v.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    static String upper(String v) {
        return v == null ? null : v.trim().toUpperCase(Locale.ROOT);
    }
}
