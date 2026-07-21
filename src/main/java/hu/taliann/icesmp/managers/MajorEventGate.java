package hu.taliann.icesmp.managers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Gameplay-audit: figyelem-orchestráció — a NAGY, mob-spawnoló PvE-események
 * (világboss, invázió, vad hajsza, kíséret, kultisták) természetes sorsolása
 * kapuzott: ha már fut egy másik nagy esemény, az új sorsolás kimarad, így a
 * szerver figyelme nem forgácsolódik szét. Admin-parancs (forceStart) mindig
 * átmegy — a kapu CSAK a természetes indítást fogja vissza. Az aktív-állapot
 * ellenőrzők a core-ban regisztrálódnak; minden kulcs élőben olvasódik.
 */
public final class MajorEventGate {

    /** A default major-lista (world-events.orchestration.major-events felülírhatja). */
    private static final List<String> DEFAULT_MAJORS =
            List.of("world-boss", "invasion", "wild-hunt", "escort", "cultists");

    private final ConfigManager configManager;
    private final Map<String, BooleanSupplier> activeChecks = new ConcurrentHashMap<>();

    public MajorEventGate(final ConfigManager configManager) {
        this.configManager = configManager;
    }

    /** Core-oldali regisztráció: eseménykulcs → "épp aktív?" ellenőrző (volatile olvasás). */
    public void register(final String eventKey, final BooleanSupplier activeCheck) {
        activeChecks.put(eventKey, activeCheck);
    }

    /**
     * Indulhat-e MOST természetes sorsolásból az adott nagy esemény. false, ha az
     * orchestráció él, az esemény a major-listán van, és egy MÁSIK listás esemény
     * éppen fut. A managerek a saját sorsolásuk elején hívják (global tick szál).
     *
     * @param eventKey a kérdező esemény kulcsa (pl. "world-boss")
     * @return true, ha a természetes indítás mehet
     */
    public boolean mayStartNaturally(final String eventKey) {
        if (!configManager.getBoolean("world-events.orchestration.enabled", true)) {
            return true;
        }
        final List<String> configured = configManager.getStringList("world-events.orchestration.major-events");
        final List<String> majors = configured.isEmpty() ? DEFAULT_MAJORS : configured;
        if (!majors.contains(eventKey)) {
            return true;
        }
        for (final String other : majors) {
            if (other.equals(eventKey)) {
                continue;
            }
            final BooleanSupplier check = activeChecks.get(other);
            if (check != null && check.getAsBoolean()) {
                return false;
            }
        }
        return true;
    }
}
