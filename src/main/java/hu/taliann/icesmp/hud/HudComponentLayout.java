package hu.taliann.icesmp.hud;

/** Immutable transform relative to the global right-anchored HUD layout. */
public record HudComponentLayout(int xOffsetPixels, int yOffsetPixels, int scaleIndex,
                                 boolean visible) {

    public static final int DEFAULT_SCALE_INDEX = 2;

    public HudComponentLayout {
        xOffsetPixels = HudLayoutSnapshot.clamp(xOffsetPixels,
                HudLayoutSnapshot.MIN_X_OFFSET, HudLayoutSnapshot.MAX_X_OFFSET);
        yOffsetPixels = HudLayoutSnapshot.clamp(yOffsetPixels,
                HudLayoutSnapshot.MIN_Y_OFFSET, HudLayoutSnapshot.MAX_Y_OFFSET);
        scaleIndex = HudLayoutSnapshot.clamp(scaleIndex, 0,
                HudLayoutSnapshot.SCALE_PERMILLE.size() - 1);
    }

    public static HudComponentLayout defaults() {
        return new HudComponentLayout(0, 0, DEFAULT_SCALE_INDEX, true);
    }

    public static HudComponentLayout fromConfigValues(final Object x, final Object y,
                                                       final Object scale, final Object visible) {
        return new HudComponentLayout(
                HudLayoutSnapshot.validInteger(x, HudLayoutSnapshot.MIN_X_OFFSET,
                        HudLayoutSnapshot.MAX_X_OFFSET, 0),
                HudLayoutSnapshot.validInteger(y, HudLayoutSnapshot.MIN_Y_OFFSET,
                        HudLayoutSnapshot.MAX_Y_OFFSET, 0),
                HudLayoutSnapshot.scaleIndex(scale, DEFAULT_SCALE_INDEX),
                visible instanceof Boolean value ? value : true);
    }

    public double scale() {
        return HudLayoutSnapshot.SCALE_PERMILLE.get(scaleIndex) / 1000.0D;
    }

    public HudComponentLayout move(final int deltaX, final int deltaY) {
        return new HudComponentLayout(xOffsetPixels + deltaX, yOffsetPixels + deltaY,
                scaleIndex, visible);
    }

    public HudComponentLayout changeScale(final int variants) {
        return new HudComponentLayout(xOffsetPixels, yOffsetPixels, scaleIndex + variants, visible);
    }

    public HudComponentLayout toggleVisibility() {
        return new HudComponentLayout(xOffsetPixels, yOffsetPixels, scaleIndex, !visible);
    }
}
