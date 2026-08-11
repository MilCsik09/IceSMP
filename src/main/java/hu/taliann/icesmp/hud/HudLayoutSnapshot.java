package hu.taliann.icesmp.hud;

import java.util.List;

/** Immutable, validated presentation-only HUD layout. */
public record HudLayoutSnapshot(int xOffsetPixels, int yOffsetPixels, int safeMarginPixels,
                                int scaleIndex) {

    public static final int MIN_X_OFFSET = -512;
    public static final int MAX_X_OFFSET = 512;
    public static final int MIN_Y_OFFSET = -256;
    public static final int MAX_Y_OFFSET = 255;
    public static final int MIN_SAFE_MARGIN = 0;
    public static final int MAX_SAFE_MARGIN = 128;
    public static final List<Integer> SCALE_PERMILLE = List.of(750, 900, 1000, 1150, 1250, 1400, 1600, 1800);
    public static final int DEFAULT_X_OFFSET = 0;
    public static final int DEFAULT_Y_OFFSET = 16;
    public static final int DEFAULT_SAFE_MARGIN = 16;
    public static final int DEFAULT_SCALE_INDEX = 4;

    public HudLayoutSnapshot {
        xOffsetPixels = clamp(xOffsetPixels, MIN_X_OFFSET, MAX_X_OFFSET);
        yOffsetPixels = clamp(yOffsetPixels, MIN_Y_OFFSET, MAX_Y_OFFSET);
        safeMarginPixels = clamp(safeMarginPixels, MIN_SAFE_MARGIN, MAX_SAFE_MARGIN);
        scaleIndex = clamp(scaleIndex, 0, SCALE_PERMILLE.size() - 1);
    }

    public static HudLayoutSnapshot defaults() {
        return new HudLayoutSnapshot(DEFAULT_X_OFFSET, DEFAULT_Y_OFFSET,
                DEFAULT_SAFE_MARGIN, DEFAULT_SCALE_INDEX);
    }

    /** Invalid or out-of-range config values fall back field-by-field to safe defaults. */
    public static HudLayoutSnapshot fromConfigValues(final Object x, final Object y,
                                                     final Object safeMargin, final Object scale) {
        return new HudLayoutSnapshot(
                validInteger(x, MIN_X_OFFSET, MAX_X_OFFSET, DEFAULT_X_OFFSET),
                validInteger(y, MIN_Y_OFFSET, MAX_Y_OFFSET, DEFAULT_Y_OFFSET),
                validInteger(safeMargin, MIN_SAFE_MARGIN, MAX_SAFE_MARGIN, DEFAULT_SAFE_MARGIN),
                scaleIndex(scale));
    }

    public int scalePermille() {
        return SCALE_PERMILLE.get(scaleIndex);
    }

    public double scale() {
        return scalePermille() / 1000.0D;
    }

    public int anchoredX(final int sourceX) {
        return sourceX + xOffsetPixels - safeMarginPixels;
    }

    /** 12-bit shader payload: 9-bit signed Y plus a 3-bit scale variant. */
    public int shaderCode() {
        return (scaleIndex << 9) | (yOffsetPixels + 256);
    }

    public HudLayoutSnapshot move(final int deltaX, final int deltaY) {
        return new HudLayoutSnapshot(xOffsetPixels + deltaX, yOffsetPixels + deltaY,
                safeMarginPixels, scaleIndex);
    }

    public HudLayoutSnapshot changeMargin(final int delta) {
        return new HudLayoutSnapshot(xOffsetPixels, yOffsetPixels,
                safeMarginPixels + delta, scaleIndex);
    }

    public HudLayoutSnapshot changeScale(final int variants) {
        return new HudLayoutSnapshot(xOffsetPixels, yOffsetPixels, safeMarginPixels,
                scaleIndex + variants);
    }

    private static int scaleIndex(final Object raw) {
        if (!(raw instanceof Number number)) return DEFAULT_SCALE_INDEX;
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value < 0.75D || value > 1.80D) return DEFAULT_SCALE_INDEX;
        final int target = (int) Math.round(value * 1000.0D);
        int closest = 0;
        for (int index = 1; index < SCALE_PERMILLE.size(); index++) {
            if (Math.abs(SCALE_PERMILLE.get(index) - target)
                    < Math.abs(SCALE_PERMILLE.get(closest) - target)) closest = index;
        }
        return closest;
    }

    private static int validInteger(final Object raw, final int minimum, final int maximum,
                                    final int fallback) {
        if (!(raw instanceof Number number)) return fallback;
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < minimum || value > maximum) return fallback;
        return (int) value;
    }

    private static int clamp(final int value, final int minimum, final int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
