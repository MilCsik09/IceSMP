package hu.taliann.icesmp.client;

import java.util.Locale;
import java.util.Optional;

/**
 * A kliensmod által felkínálható presentation-capability-k v1 készlete.
 *
 * <p>Egy capability csak akkor aktív, ha a kliens hirdette ÉS a szerver a
 * {@code client.features.<config-kulcs>} kapcsolóval engedélyezte ÉS a kiválasztott
 * protokoll támogatja. A vezetéken az enum-név utazik; ismeretlen név nem hiba,
 * hanem csendben kimarad (újabb kliens régebbi szerverrel is kézfogásképes).</p>
 */
public enum ClientCapability {
    NATIVE_HUD,
    ABILITY_BAR,
    KEYBIND_CAST,
    NATIVE_PROFILE,
    NATIVE_SPELLBOOK,
    NATIVE_TALENTS,
    QUEST_JOURNAL,
    NATIVE_PROFESSIONS,
    RECIPE_BROWSER,
    RELIC_RENDER_V1,
    ADVANCED_FX_V1,
    PLAYER_ANIMATION_V1,
    PARTY_FRAME,
    BOSS_FRAME,
    TERRITORY_OVERLAY;

    /** A {@code client.features.} alatti kebab-case config-kulcs. */
    public String configKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Vezetéknévből; ismeretlen név üres Optional (nem kivétel — forward compat). */
    public static Optional<ClientCapability> fromWire(final String wireName) {
        if (wireName == null) {
            return Optional.empty();
        }
        for (final ClientCapability capability : values()) {
            if (capability.name().equals(wireName)) {
                return Optional.of(capability);
            }
        }
        return Optional.empty();
    }
}
