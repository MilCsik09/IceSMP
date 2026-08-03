package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import org.bukkit.entity.Player;

/**
 * A specializáció-visszaváltás (respec) EGYETLEN végrehajtó helye: feltétel-ellenőrzés,
 * ár levonása, spec törlése és a talentpont-visszatérítés.
 *
 * <p>Két belépési pont hívja ({@code /spec respec} és a Karakter-GUI), ezért a folyamat nem
 * élhet egyikben sem: pénzt mozgat, és ha a két példány szétcsúszik, az egyik úton rosszul
 * számol. A hívók CSAK megjelenítenek — a szolgáltatás adatot ad vissza, nem üzenetet, hogy a
 * parancs és a GUI megtarthassa a saját szövegét/hangját.
 *
 * <p>Külön osztály (nem a {@code SpecializationManager} metódusa), mert a művelethez
 * {@link TalentManager} is kell, az pedig a DI-sorrendben a SpecializationManager UTÁN épül —
 * ott a hivatkozás kört zárna.
 */
public final class RespecService {

    /** A respec kimenete; a szöveget/hangot a hívó adja hozzá. */
    public record Outcome(Status status, double cost, CurrencyType currency, int refundedTalentPoints) {

        public enum Status {
            /** Sikerült: az ár levonva, a spec törölve. */
            OK,
            /** Nincs mit visszaváltani (nincs ilyen specializáció). */
            NOTHING_TO_RESPEC,
            /** Nincs elég egyenleg az árra. */
            INSUFFICIENT_FUNDS
        }

        public boolean ok() {
            return status == Status.OK;
        }
    }

    private final SpecializationManager specializationManager;
    private final TalentManager talentManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;

    public RespecService(final SpecializationManager specializationManager, final TalentManager talentManager,
                         final CurrencyManager currencyManager, final FactionManager factionManager) {
        this.specializationManager = specializationManager;
        this.talentManager = talentManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
    }

    /**
     * Respec végrehajtása.
     *
     * @param classPool true = kaszt-specializáció, false = szakma-specializáció
     */
    public Outcome respec(final Player player, final boolean classPool) {
        final double cost = specializationManager.getRespecCost();
        final FactionType faction = factionManager.getEconomyFaction(player.getUniqueId());
        final CurrencyType currency = CurrencyType.fromFactionType(faction);

        final boolean hasSpec = classPool
                ? specializationManager.getClassSpecialization(player) != null
                : specializationManager.getProfessionSpecialization(player) != null;
        if (!hasSpec) {
            return new Outcome(Outcome.Status.NOTHING_TO_RESPEC, cost, currency, 0);
        }
        // Atomi levonás (nincs get+set verseny): konkurens egyenleg-írás nem veszhet el.
        if (cost > 0.0D && !currencyManager.deductFromBalance(player.getUniqueId(), currency, cost)) {
            return new Outcome(Outcome.Status.INSUFFICIENT_FUNDS, cost, currency, 0);
        }

        int refunded = 0;
        if (classPool) {
            specializationManager.resetClassSpecialization(player);
            // A spec-hez kötött talentek elvesznek a speccel; a pontjuk visszakerül a készletbe.
            refunded = talentManager.refundUnavailableTalents(player, true);
        } else {
            specializationManager.resetProfessionSpecialization(player);
        }
        return new Outcome(Outcome.Status.OK, cost, currency, refunded);
    }
}
