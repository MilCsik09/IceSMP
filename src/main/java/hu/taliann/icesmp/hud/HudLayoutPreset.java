package hu.taliann.icesmp.hud;

import java.util.List;
import java.util.Optional;

/** Curated client-resolution/GUI-scale previews; no client state is inferred by the server. */
public record HudLayoutPreset(String id, String resolution, int guiScale,
                              HudLayoutSnapshot layout) {

    public static final List<HudLayoutPreset> VALUES = List.of(
            preset("720p-gui2", "1280x720", 2, 0, 8, 12, 2),
            preset("1080p-gui2", "1920x1080", 2, 0, 12, 16, 3),
            preset("2048x1152-gui3", "2048x1152", 3, 0, 16, 16, 4),
            preset("1440p-gui3", "2560x1440", 3, 0, 18, 20, 5),
            preset("4k-gui4", "3840x2160", 4, 0, 24, 24, 6),
            preset("large-accessible", "bármely", 3, -12, 24, 24, 7));

    public static Optional<HudLayoutPreset> find(final String id) {
        return VALUES.stream().filter(preset -> preset.id.equalsIgnoreCase(id)).findFirst();
    }

    private static HudLayoutPreset preset(final String id, final String resolution, final int guiScale,
                                          final int x, final int y, final int margin, final int scaleIndex) {
        return new HudLayoutPreset(id, resolution, guiScale,
                new HudLayoutSnapshot(x, y, margin, scaleIndex));
    }
}
