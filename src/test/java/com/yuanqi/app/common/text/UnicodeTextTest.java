package com.yuanqi.app.common.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnicodeTextTest {

    @Test
    void normalizesDisplayAndComparisonSeparately() {
        assertEquals("é", UnicodeText.nfc("e\u0301"));
        assertEquals("admin", UnicodeText.comparisonKey("ＡＤＭＩＮ"));
        assertEquals("strasse", UnicodeText.comparisonKey("Straße"));
        assertEquals("café", UnicodeText.comparisonKey("Ｃａｆe\u0301"));
    }

    @Test
    void countsExtendedGraphemeClusters() {
        assertEquals(1, UnicodeText.graphemeCount("👨‍👩‍👧‍👦"));
        assertEquals(1, UnicodeText.graphemeCount("👍🏽"));
        assertEquals(1, UnicodeText.graphemeCount("🇨🇳"));
        assertEquals(1, UnicodeText.graphemeCount("❤️"));
    }

    @Test
    void trimsUnicodeWhitespaceAndSplitsSearchTerms() {
        assertEquals("A\u00A0\u3000B", UnicodeText.trimUnicode("  A\u00A0\u3000B  "));
        assertEquals(List.of("café", "photo"),
                UnicodeText.searchTerms("  Ｃａｆe\u0301\u3000PHOTO  "));
    }

    @Test
    void rejectsControlsAndBidiControlsByFieldPolicy() {
        assertTrue(UnicodeText.containsForbiddenControl("A\nB", false));
        assertFalse(UnicodeText.containsForbiddenControl("A\nB", true));
        assertTrue(UnicodeText.containsForbiddenControl("ab\u202Ecd", true));
    }
}
