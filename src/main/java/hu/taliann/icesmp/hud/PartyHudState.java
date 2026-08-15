package hu.taliann.icesmp.hud;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** WoW-style compact party projection, excluding the local player. */
public record PartyHudState(List<Member> members) {
    public PartyHudState {
        members = members == null ? List.of() : List.copyOf(members.stream().limit(4).toList());
    }

    public record Member(UUID playerId, String name, String factionTheme, String factionAccent,
                         double health, double maximumHealth, int resourcePercent,
                         boolean leader, boolean online, boolean inRange, boolean dead) {
        public Member {
            name = Objects.requireNonNullElse(name, "?");
            factionTheme = Objects.requireNonNullElse(factionTheme, "ice");
            factionAccent = Objects.requireNonNullElse(factionAccent, "8BE9FD");
            maximumHealth = positive(maximumHealth, 20.0D);
            health = Math.max(0.0D, Math.min(maximumHealth, finite(health, 0.0D)));
            resourcePercent = Math.max(0, Math.min(100, resourcePercent));
        }

        public int healthPercent() {
            return (int) Math.round(health / maximumHealth * 100.0D);
        }

        public String status() {
            if (!online) return "Offline";
            if (dead || health <= 0.0D) return "Halott";
            if (!inRange) return "Távol";
            return leader ? "Vezető" : "";
        }

        private static double positive(final double value, final double fallback) {
            final double safe = finite(value, fallback);
            return safe > 0.0D ? safe : fallback;
        }

        private static double finite(final double value, final double fallback) {
            return Double.isFinite(value) ? value : fallback;
        }
    }

    public static PartyHudState preview() {
        return new PartyHudState(List.of(
                new Member(UUID.randomUUID(), "Vörös", "ember", "E7683F",
                        78, 100, 62, true, true, true, false),
                new Member(UUID.randomUUID(), "Kék", "frost", "8BE9FD",
                        44, 80, 35, false, true, true, false),
                new Member(UUID.randomUUID(), "Sötét", "lich", "62D7CE",
                        20, 120, 80, false, true, false, false)));
    }
}
