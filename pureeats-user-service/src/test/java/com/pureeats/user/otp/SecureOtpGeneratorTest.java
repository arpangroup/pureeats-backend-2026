package com.pureeats.user.otp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureOtpGeneratorTest {

    private final SecureOtpGenerator generator = new SecureOtpGenerator();

    @ParameterizedTest
    @ValueSource(ints = {4, 6, 8, 10})
    void generatesNumericStringOfRequestedLength(int length) {
        String otp = generator.generate(length);
        assertEquals(length, otp.length());
        assertTrue(otp.chars().allMatch(Character::isDigit));
    }

    @Test
    void rejectsLengthOutsideSupportedRange() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(3));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(11));
    }

    @Test
    void generatesVaryingValuesAcrossCalls() {
        long distinctCount = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate(6))
                .distinct()
                .count();
        // Astronomically unlikely to collide 20 times in a row for a secure 6-digit generator.
        assertTrue(distinctCount > 1);
    }
}
