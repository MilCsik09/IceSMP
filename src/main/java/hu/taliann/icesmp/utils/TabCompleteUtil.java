package hu.taliann.icesmp.utils;

import java.util.Locale;

/** Tab-complete segédfüggvények (a parancs-osztályok static importtal hívják). */
public final class TabCompleteUtil {

    private TabCompleteUtil() {
    }

    /**
     * A megadott argumentum-index kisbetűsített értéke, vagy üres string, ha az argumentum
     * még nincs beírva — a tab-complete szűrő-prefixe.
     *
     * <p>A {@link Locale#ROOT} kötelező: a szerver default locale-jában (pl. tr_TR) az
     * {@code "I"} nem {@code "i"}-re kisbetűsödik, és a prefix-szűrés csendben elromlik.
     */
    public static String prefixAt(final String[] args, final int index) {
        return args != null && args.length > index && args[index] != null
                ? args[index].toLowerCase(Locale.ROOT)
                : "";
    }
}
