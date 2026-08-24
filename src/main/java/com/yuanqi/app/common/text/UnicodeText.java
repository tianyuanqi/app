package com.yuanqi.app.common.text;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.text.Normalizer2;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Unicode 16 / ICU4J 76.1 文本规范化与字素计数入口。 */
public final class UnicodeText {
    private static final Normalizer2 NFC = Normalizer2.getNFCInstance();
    private static final Normalizer2 NFKC_CF = Normalizer2.getNFKCCasefoldInstance();
    private static final Set<Integer> BIDI_CONTROLS = Set.of(
            0x061C, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
            0x2066, 0x2067, 0x2068, 0x2069);

    private UnicodeText() {
    }

    public static String nfc(String value) {
        return value == null ? null : NFC.normalize(value);
    }

    public static String comparisonKey(String value) {
        return value == null ? null : NFKC_CF.normalize(value);
    }

    public static String trimUnicode(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int start = 0;
        int end = value.length();
        while (start < end) {
            int cp = value.codePointAt(start);
            if (!UCharacter.hasBinaryProperty(cp, UProperty.WHITE_SPACE)) {
                break;
            }
            start += Character.charCount(cp);
        }
        while (end > start) {
            int cp = value.codePointBefore(end);
            if (!UCharacter.hasBinaryProperty(cp, UProperty.WHITE_SPACE)) {
                break;
            }
            end -= Character.charCount(cp);
        }
        return value.substring(start, end);
    }

    public static int graphemeCount(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(value);
        int count = 0;
        for (int boundary = iterator.first(); boundary != BreakIterator.DONE; boundary = iterator.next()) {
            if (boundary != 0) {
                count++;
            }
        }
        return count;
    }

    public static boolean containsForbiddenControl(String value, boolean allowLineBreaks) {
        if (value == null) {
            return false;
        }
        return value.codePoints().anyMatch(cp -> BIDI_CONTROLS.contains(cp)
                || (Character.isISOControl(cp) && !(allowLineBreaks && (cp == '\n' || cp == '\r'))));
    }

    public static List<String> searchTerms(String value) {
        String trimmed = trimUnicode(value);
        if (trimmed == null || trimmed.isEmpty()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        trimmed.codePoints().forEach(cp -> {
            if (UCharacter.hasBinaryProperty(cp, UProperty.WHITE_SPACE)) {
                if (!current.isEmpty()) {
                    terms.add(comparisonKey(nfc(current.toString())));
                    current.setLength(0);
                }
            } else {
                current.appendCodePoint(cp);
            }
        });
        if (!current.isEmpty()) {
            terms.add(comparisonKey(nfc(current.toString())));
        }
        return List.copyOf(terms);
    }
}
