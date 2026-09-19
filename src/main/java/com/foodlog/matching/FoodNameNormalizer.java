package com.foodlog.matching;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Turns free-form food names into comparable token lists (lower-case, no accents, singular). */
public final class FoodNameNormalizer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> STOP_WORDS = Set.of("a", "an", "the", "of", "some", "my", "fresh");

    private FoodNameNormalizer() {
    }

    public static List<String> tokens(String name) {
        if (name == null) {
            return List.of();
        }
        String s = Normalizer.normalize(name.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        s = DIACRITICS.matcher(s).replaceAll("");
        s = NON_WORD.matcher(s).replaceAll(" ").trim();
        if (s.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String t : s.split(" ")) {
            if (!STOP_WORDS.contains(t)) {
                out.add(singularize(t));
            }
        }
        return out;
    }

    /**
     * Crude English singularisation. It only has to be consistent: the same rule is applied to
     * database names and to queries, so "tomatoes" and "Tomato" end up as the same token.
     */
    static String singularize(String t) {
        if (t.length() <= 3 || containsCjk(t)) {
            return t;
        }
        if (t.endsWith("ies")) {
            return t.substring(0, t.length() - 3) + "y";
        }
        if (t.endsWith("oes")) {
            return t.substring(0, t.length() - 2);
        }
        if (t.endsWith("s") && !t.endsWith("ss") && !t.endsWith("us") && !t.endsWith("is")) {
            return t.substring(0, t.length() - 1);
        }
        return t;
    }

    static boolean containsCjk(String s) {
        return s.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }
}
