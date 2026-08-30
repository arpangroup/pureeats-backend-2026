package com.pureeats.domain.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PiiMaskUtilTest {

    @Test
    void masksEmailKeepingFirstAndLastLocalCharacter() {
        assertEquals("j**n@gmail.com", PiiMaskUtil.maskEmail("john@gmail.com"));
    }

    @Test
    void masksVeryShortEmailLocalPartWithoutIndexError() {
        assertEquals("a*@gmail.com", PiiMaskUtil.maskEmail("ab@gmail.com"));
        assertEquals("a*@gmail.com", PiiMaskUtil.maskEmail("a@gmail.com"));
    }

    @Test
    void masksPhoneKeepingLastFourDigits() {
        assertEquals("******3210", PiiMaskUtil.maskPhone("9876543210"));
    }

    @Test
    void masksShortPhoneEntirely() {
        assertEquals("***", PiiMaskUtil.maskPhone("123"));
    }

    @Test
    void maskDestinationAutoDetectsEmailVsPhone() {
        assertEquals("j**n@gmail.com", PiiMaskUtil.maskDestination("john@gmail.com"));
        assertEquals("******3210", PiiMaskUtil.maskDestination("9876543210"));
    }

    @Test
    void nullInputsPassThroughWithoutThrowing() {
        assertNull(PiiMaskUtil.maskEmail(null));
        assertNull(PiiMaskUtil.maskPhone(null));
        assertNull(PiiMaskUtil.maskDestination(null));
    }
}
