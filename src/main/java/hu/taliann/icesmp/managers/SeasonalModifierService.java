package hu.taliann.icesmp.managers;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * B19 — Évszakos világ-modifikátorok (lore: az Égi Jelek + a gyógyuló Fa / a Királynő
 * álmának ciklusai — kódex V./VII./VIII.). A VALÓS naptári évszak finom szorzókat ad a
 * világesemények sorsolási esélyeire: télen sűrűbb a vérhold és a Vad Hajsza (a Fagy
 * uralkodik), nyáron gyakoribb a Bőség-idő és a gyűjtögető-láz, és így tovább.
 * Nincs új esemény — csak a meglévők gyakoriság-hangolása.
 *
 * <p>Config: {@code world-events.season-modifiers.<evszak>.<esemeny-kulcs>} szorzó (default 1.0);
 * évszakok: tel (dec-feb), tavasz (mar-máj), nyar (jún-aug), osz (szept-nov).
 * Master-kapcsoló: {@code world-events.season-modifiers.enabled}. Minden kulcs élőben olvasódik.
 * Szál-biztos: állapotmentes, csak configot olvas.
 */
public final class SeasonalModifierService {

    private final ConfigManager configManager;

    public SeasonalModifierService(final ConfigManager configManager) {
        this.configManager = configManager;
    }

    /** Az aktuális valós évszak config-kulcsa (tel/tavasz/nyar/osz). */
    public String currentSeasonKey() {
        final int month = LocalDate.now(ZoneId.systemDefault()).getMonthValue();
        return switch (month) {
            case 12, 1, 2 -> "tel";
            case 3, 4, 5 -> "tavasz";
            case 6, 7, 8 -> "nyar";
            default -> "osz";
        };
    }

    /**
     * Az esemény esély-szorzója az aktuális évszakban (1.0 = nincs módosítás).
     * A hívó a saját chance-percent kulcsát szorozza vele (a 100%-os plafon a hívónál).
     *
     * @param eventKey az esemény kulcsa (blood-moon, world-boss, invasion, wild-hunt,
     *                 abundance, gathering, treasure, meteor, corruption, archeology, stranger)
     */
    public double chanceMultiplier(final String eventKey) {
        if (!configManager.getBoolean("world-events.season-modifiers.enabled", true)) {
            return 1.0D;
        }
        return Math.max(0.0D, configManager.getDouble(
                "world-events.season-modifiers." + currentSeasonKey() + "." + eventKey, 1.0D));
    }
}
