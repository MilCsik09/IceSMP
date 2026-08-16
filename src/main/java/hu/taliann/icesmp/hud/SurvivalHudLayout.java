package hu.taliann.icesmp.hud;

/** Validated bottom-centred layout for the non-optional survival HUD replacement. */
public record SurvivalHudLayout(int xOffsetPixels, int yOffsetPixels, int scaleIndex) {

    public static final int DEFAULT_SCALE_INDEX = 2;

    public SurvivalHudLayout {
        xOffsetPixels = HudLayoutSnapshot.clamp(xOffsetPixels,
                HudLayoutSnapshot.MIN_X_OFFSET, HudLayoutSnapshot.MAX_X_OFFSET);
        yOffsetPixels = HudLayoutSnapshot.clamp(yOffsetPixels,
                HudLayoutSnapshot.MIN_Y_OFFSET, HudLayoutSnapshot.MAX_Y_OFFSET);
        scaleIndex = HudLayoutSnapshot.clamp(scaleIndex, 0,
                HudLayoutSnapshot.SCALE_PERMILLE.size() - 1);
    }

    public static SurvivalHudLayout defaults() {
        return new SurvivalHudLayout(0, 0, DEFAULT_SCALE_INDEX);
    }

    public static SurvivalHudLayout fromConfigValues(final Object x, final Object y,
                                                      final Object scale) {
        return new SurvivalHudLayout(
                HudLayoutSnapshot.validInteger(x, HudLayoutSnapshot.MIN_X_OFFSET,
                        HudLayoutSnapshot.MAX_X_OFFSET, 0),
                HudLayoutSnapshot.validInteger(y, HudLayoutSnapshot.MIN_Y_OFFSET,
                        HudLayoutSnapshot.MAX_Y_OFFSET, 0),
                HudLayoutSnapshot.scaleIndex(scale, DEFAULT_SCALE_INDEX));
    }

    public int anchoredX(final int sourceX) {
        return sourceX + xOffsetPixels;
    }

    /** Same 13-bit transport as the editable class HUD: signed Y plus scale variant. */
    public int shaderCode() {
        return (scaleIndex << 9) | (yOffsetPixels + 256);
    }
}
