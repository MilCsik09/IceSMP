package hu.taliann.icesmp.hud;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Immutable, validated presentation-only HUD layout. */
public record HudLayoutSnapshot(int xOffsetPixels, int yOffsetPixels, int safeMarginPixels,
                                int scaleIndex, Map<HudComponent, HudComponentLayout> components) {

    public static final int MIN_X_OFFSET = -512;
    public static final int MAX_X_OFFSET = 512;
    public static final int MIN_Y_OFFSET = -256;
    public static final int MAX_Y_OFFSET = 255;
    public static final int MIN_SAFE_MARGIN = 0;
    public static final int MAX_SAFE_MARGIN = 128;
    public static final List<Integer> SCALE_PERMILLE = List.of(
            750, 900, 1000, 1150, 1250, 1400, 1600, 1800,
            2000, 2200, 2400, 2600, 2800, 3000, 3250, 3500);
    public static final int DEFAULT_X_OFFSET = 0;
    public static final int DEFAULT_Y_OFFSET = 24;
    public static final int DEFAULT_SAFE_MARGIN = 24;
    public static final int DEFAULT_SCALE_INDEX = 2;

    public HudLayoutSnapshot {
        xOffsetPixels = clamp(xOffsetPixels, MIN_X_OFFSET, MAX_X_OFFSET);
        yOffsetPixels = clamp(yOffsetPixels, MIN_Y_OFFSET, MAX_Y_OFFSET);
        safeMarginPixels = clamp(safeMarginPixels, MIN_SAFE_MARGIN, MAX_SAFE_MARGIN);
        scaleIndex = clamp(scaleIndex, 0, SCALE_PERMILLE.size() - 1);
        final EnumMap<HudComponent, HudComponentLayout> safe = new EnumMap<>(HudComponent.class);
        for (final HudComponent component : HudComponent.editableValues()) {
            safe.put(component, defaultComponentLayout(component));
        }
        if (components != null) {
            components.forEach((component, layout) -> {
                if (component != null && component != HudComponent.GLOBAL
                        && (component.rendered() || component.isGroup()) && layout != null) {
                    safe.put(component, layout);
                }
            });
        }
        components = Map.copyOf(safe);
    }

    public HudLayoutSnapshot(final int xOffsetPixels, final int yOffsetPixels,
                             final int safeMarginPixels, final int scaleIndex) {
        this(xOffsetPixels, yOffsetPixels, safeMarginPixels, scaleIndex, Map.of());
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
                scaleIndex(scale, DEFAULT_SCALE_INDEX));
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

    public int anchoredX(final HudComponent component, final int sourceX) {
        final HudComponentLayout element = effectiveComponentLayout(component);
        if (leftAnchored(component)) {
            return sourceX + xOffsetPixels + safeMarginPixels + element.xOffsetPixels();
        }
        return anchoredX(sourceX) + element.xOffsetPixels();
    }

    /** 13-bit shader payload: 9-bit signed Y plus a 4-bit scale variant. */
    public int shaderCode() {
        return (scaleIndex << 9) | (yOffsetPixels + 256);
    }

    public int shaderCode(final HudComponent component) {
        return shaderCode(component, 0);
    }

    public int shaderCode(final HudComponent component, final int additionalYOffset) {
        final HudComponentLayout element = effectiveComponentLayout(component);
        final int y = clamp(yOffsetPixels + element.yOffsetPixels() + additionalYOffset,
                MIN_Y_OFFSET, MAX_Y_OFFSET);
        final int effectiveScale = closestScaleIndex(scale() * element.scale());
        return (effectiveScale << 9) | (y + 256);
    }

    public boolean visible(final HudComponent component) {
        return component == null || component == HudComponent.GLOBAL || !component.hideable()
                || effectiveComponentLayout(component).visible();
    }

    public HudComponentLayout componentLayout(final HudComponent component) {
        if (component == null || component == HudComponent.GLOBAL) return HudComponentLayout.defaults();
        return components.getOrDefault(component, HudComponentLayout.defaults());
    }

    /** Resolves a child transform against its movable player/target/party group. */
    public HudComponentLayout effectiveComponentLayout(final HudComponent component) {
        final HudComponentLayout child = componentLayout(component);
        final HudComponent parent = component == null ? null : component.parentGroup();
        if (parent == null) return child;
        final HudComponentLayout group = componentLayout(parent);
        return new HudComponentLayout(
                group.xOffsetPixels() + child.xOffsetPixels(),
                group.yOffsetPixels() + child.yOffsetPixels(),
                closestScaleIndex(group.scale() * child.scale()),
                group.visible() && child.visible());
    }

    public static boolean leftAnchored(final HudComponent component) {
        return component != null && (component == HudComponent.PLAYER_GROUP
                || component == HudComponent.TARGET_GROUP || component == HudComponent.PARTY_GROUP
                || component.parentGroup() != null);
    }

    public static HudComponentLayout defaultComponentLayout(final HudComponent component) {
        if (component == HudComponent.TARGET_GROUP) {
            return new HudComponentLayout(264, 0, HudComponentLayout.DEFAULT_SCALE_INDEX, true);
        }
        if (component == HudComponent.PARTY_GROUP) {
            return new HudComponentLayout(0, 82, HudComponentLayout.DEFAULT_SCALE_INDEX, true);
        }
        return HudComponentLayout.defaults();
    }

    public HudLayoutSnapshot move(final int deltaX, final int deltaY) {
        return new HudLayoutSnapshot(xOffsetPixels + deltaX, yOffsetPixels + deltaY,
                safeMarginPixels, scaleIndex, components);
    }

    public HudLayoutSnapshot move(final HudComponent target, final int deltaX, final int deltaY) {
        if (target == null || target == HudComponent.GLOBAL) return move(deltaX, deltaY);
        return withComponent(target, componentLayout(target).move(deltaX, deltaY));
    }

    public HudLayoutSnapshot changeMargin(final int delta) {
        return new HudLayoutSnapshot(xOffsetPixels, yOffsetPixels,
                safeMarginPixels + delta, scaleIndex, components);
    }

    public HudLayoutSnapshot changeScale(final int variants) {
        return new HudLayoutSnapshot(xOffsetPixels, yOffsetPixels, safeMarginPixels,
                scaleIndex + variants, components);
    }

    public HudLayoutSnapshot changeScale(final HudComponent target, final int variants) {
        if (target == null || target == HudComponent.GLOBAL) return changeScale(variants);
        return withComponent(target, componentLayout(target).changeScale(variants));
    }

    public HudLayoutSnapshot setX(final HudComponent target, final int value) {
        if (target == null || target == HudComponent.GLOBAL) {
            return new HudLayoutSnapshot(value, yOffsetPixels, safeMarginPixels,
                    scaleIndex, components);
        }
        final HudComponentLayout element = componentLayout(target);
        return withComponent(target, new HudComponentLayout(value, element.yOffsetPixels(),
                element.scaleIndex(), element.visible()));
    }

    public HudLayoutSnapshot setY(final HudComponent target, final int value) {
        if (target == null || target == HudComponent.GLOBAL) {
            return new HudLayoutSnapshot(xOffsetPixels, value, safeMarginPixels,
                    scaleIndex, components);
        }
        final HudComponentLayout element = componentLayout(target);
        return withComponent(target, new HudComponentLayout(element.xOffsetPixels(), value,
                element.scaleIndex(), element.visible()));
    }

    public HudLayoutSnapshot setScale(final HudComponent target, final double value) {
        final int index = scaleIndex(value, -1);
        if (index < 0) throw new IllegalArgumentException("Unsupported HUD scale: " + value);
        if (target == null || target == HudComponent.GLOBAL) {
            return new HudLayoutSnapshot(xOffsetPixels, yOffsetPixels, safeMarginPixels,
                    index, components);
        }
        final HudComponentLayout element = componentLayout(target);
        return withComponent(target, new HudComponentLayout(element.xOffsetPixels(),
                element.yOffsetPixels(), index, element.visible()));
    }

    public HudLayoutSnapshot toggleVisibility(final HudComponent target) {
        if (target == null || target == HudComponent.GLOBAL || !target.hideable()) return this;
        return withComponent(target, componentLayout(target).toggleVisibility());
    }

    public HudLayoutSnapshot reset(final HudComponent target) {
        if (target == null || target == HudComponent.GLOBAL) {
            final HudLayoutSnapshot defaults = defaults();
            return new HudLayoutSnapshot(defaults.xOffsetPixels, defaults.yOffsetPixels,
                    defaults.safeMarginPixels, defaults.scaleIndex, components);
        }
        return withComponent(target, defaultComponentLayout(target));
    }

    public HudLayoutSnapshot withComponent(final HudComponent component,
                                           final HudComponentLayout componentLayout) {
        if (component == null || component == HudComponent.GLOBAL
                || (!component.rendered() && !component.isGroup())) return this;
        final EnumMap<HudComponent, HudComponentLayout> next = new EnumMap<>(components);
        next.put(component, componentLayout == null ? HudComponentLayout.defaults() : componentLayout);
        return new HudLayoutSnapshot(xOffsetPixels, yOffsetPixels, safeMarginPixels, scaleIndex, next);
    }

    public HudLayoutSnapshot withGlobal(final HudLayoutSnapshot global) {
        final HudLayoutSnapshot safe = global == null ? defaults() : global;
        return new HudLayoutSnapshot(safe.xOffsetPixels, safe.yOffsetPixels,
                safe.safeMarginPixels, safe.scaleIndex, components);
    }

    static int scaleIndex(final Object raw, final int fallback) {
        if (!(raw instanceof Number number)) return fallback;
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value < 0.75D || value > 3.50D) return fallback;
        final int target = (int) Math.round(value * 1000.0D);
        return closestScaleIndex(target / 1000.0D);
    }

    static int closestScaleIndex(final double scale) {
        final int target = (int) Math.round(scale * 1000.0D);
        int closest = 0;
        for (int index = 1; index < SCALE_PERMILLE.size(); index++) {
            if (Math.abs(SCALE_PERMILLE.get(index) - target)
                    < Math.abs(SCALE_PERMILLE.get(closest) - target)) closest = index;
        }
        return closest;
    }

    static int validInteger(final Object raw, final int minimum, final int maximum,
                            final int fallback) {
        if (!(raw instanceof Number number)) return fallback;
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < minimum || value > maximum) return fallback;
        return (int) value;
    }

    static int clamp(final int value, final int minimum, final int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
