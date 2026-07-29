package hu.taliann.icesmp.moderation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Unicode-safe literal matching/censoring for configured moderation filter words. */
public final class ModerationTextFilter {
    private ModerationTextFilter() {
    }

    public static boolean containsIgnoreCase(final String text, final String literal) {
        if (text == null || literal == null || literal.isEmpty()) {
            return false;
        }
        return pattern(literal).matcher(text).find();
    }

    public static String censorIgnoreCase(final String text, final String literal) {
        if (text == null || literal == null || literal.isEmpty()) {
            return text;
        }
        final Matcher matcher = pattern(literal).matcher(text);
        final StringBuffer output = new StringBuffer(text.length());
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement("*".repeat(matcher.group().length())));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static Pattern pattern(final String literal) {
        return Pattern.compile(Pattern.quote(literal), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
