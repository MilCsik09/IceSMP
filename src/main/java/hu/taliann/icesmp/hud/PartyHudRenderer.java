package hu.taliann.icesmp.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.key.Key;

import java.util.List;

/** Four-row WoW-style party cluster under the local player frame. */
public final class PartyHudRenderer {
    static final int ROW_WIDTH = 252;
    static final int ROW_ADVANCE = 66;
    private static final Key HEADER_FONT = Key.key("icesmp_hud", "survival/party_header");
    private static final Key HEALTH_SEGMENT_FONT = Key.key("icesmp_hud", "survival/party_health_segments");
    private static final Key RESOURCE_SEGMENT_FONT = Key.key("icesmp_hud", "survival/party_resource_segments");
    private static final Key HEALTH_TEXT_FONT = Key.key("icesmp_hud", "survival/party_health_text");
    private static final Key STATUS_FONT = Key.key("icesmp_hud", "survival/party_status");
    private static final List<String> THEMES = List.of("ice", "ember", "frost", "guild", "lich");

    public Component render(final PartyHudState state, final HudLayoutSnapshot layout,
                            final HudComponent highlighted) {
        if (state == null || state.members().isEmpty()) return Component.empty();
        final HudLayoutSnapshot safeLayout = layout == null ? HudLayoutSnapshot.defaults() : layout;
        if (!safeLayout.componentLayout(HudComponent.PARTY_GROUP).visible()) return Component.empty();
        final TextComponent.Builder output = Component.text().shadowColor(ShadowColor.none());
        int row = 0;
        for (final PartyHudState.Member member : state.members()) {
            final int y = row * ROW_ADVANCE;
            final TextColor accent = SurvivalHudRenderer.accent(member.factionAccent(), 0x8BE9FD);
            output.append(SurvivalHudRenderer.glyph(HudComponent.PARTY_FRAME, 0,
                    SurvivalHudRenderer.PANEL_FONT, partyGlyph(member.factionTheme()), ROW_WIDTH,
                    accent, safeLayout, y, highlighted));
            output.append(SurvivalHudRenderer.text(HudComponent.PARTY_NAME, 14,
                    HEADER_FONT,
                    (member.leader() ? "◆ " : "") + member.name(),
                    member.online() ? accent : TextColor.color(0x65717E), 138,
                    safeLayout, y, highlighted));
            SurvivalHudRenderer.drawSegments(output, HudComponent.PARTY_HEALTH, 14,
                    HEALTH_SEGMENT_FONT, SurvivalHudRenderer.MINI_TRACK,
                    SurvivalHudRenderer.miniHealthFill(member.healthPercent()), member.healthPercent(),
                    18, 7, safeLayout, y, highlighted);
            output.append(SurvivalHudRenderer.text(HudComponent.PARTY_HEALTH, 148,
                    HEALTH_TEXT_FONT,
                    SurvivalHudRenderer.compact(member.health()) + "/"
                            + SurvivalHudRenderer.compact(member.maximumHealth()),
                    SurvivalHudRenderer.healthColor(member.healthPercent()), 70,
                    safeLayout, y, highlighted));
            SurvivalHudRenderer.drawSegments(output, HudComponent.PARTY_RESOURCE, 14,
                    RESOURCE_SEGMENT_FONT, SurvivalHudRenderer.MINI_TRACK,
                    SurvivalHudRenderer.MINI_RESOURCE, member.resourcePercent(),
                    18, 7, safeLayout, y, highlighted);
            if (!member.status().isBlank()) {
                output.append(SurvivalHudRenderer.text(HudComponent.PARTY_STATUS, 188,
                        STATUS_FONT, member.status(),
                        member.inRange() ? TextColor.color(0xD8E2EB) : TextColor.color(0x84909B),
                        58, safeLayout, y, highlighted));
            }
            row++;
        }
        return output.build();
    }

    private static char partyGlyph(final String theme) {
        final int index = THEMES.indexOf(theme);
        return (char) (0xEB40 + Math.max(0, index));
    }
}
