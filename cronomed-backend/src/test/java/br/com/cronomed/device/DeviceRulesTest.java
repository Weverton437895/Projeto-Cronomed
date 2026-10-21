package br.com.cronomed.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.cronomed.common.ApiException;
import org.junit.jupiter.api.Test;

class DeviceRulesTest {

    @Test
    void normalizesMacFormats() {
        assertEquals("AA:BB:CC:DD:EE:FF", DeviceService.normalizeMac("aa:bb:cc:dd:ee:ff"));
        assertEquals("AA:BB:CC:DD:EE:FF", DeviceService.normalizeMac("AA-BB-CC-DD-EE-FF"));
        assertEquals("AA:BB:CC:DD:EE:FF", DeviceService.normalizeMac("aabbccddeeff"));
    }

    @Test
    void rejectsInvalidMac() {
        assertThrows(ApiException.class, () -> DeviceService.normalizeMac("xyz"));
        assertThrows(ApiException.class, () -> DeviceService.normalizeMac("AA:BB:CC:DD:EE"));
    }

    @Test
    void topicIdHasNoSeparators() {
        IotDevice d = new IotDevice();
        d.setMacAddress("AA:BB:CC:DD:EE:FF");
        assertEquals("AABBCCDDEEFF", d.topicId());
    }

    @Test
    void compartmentRgbParsesHex() {
        Compartment c = new Compartment();
        c.setColorHex("#0072B2");
        assertEquals(0x0072B2, c.rgb());
    }
}
