package hu.taliann.icesmp.itemization;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class SignatureEffectRegistry {

    public enum Trigger {
        ON_HIT,
        ON_SHOOT,
        ON_USE,
        ON_BLOCK_BREAK,
        ON_FISH,
        WHILE_EQUIPPED
    }

    public record Definition(String id, String displayName, String tooltip,
                             Set<Trigger> triggers, String consumer) {
        public Definition {
            id = ItemStatCatalog.normalizeId(id);
            displayName = required(displayName, "signature display name");
            tooltip = required(tooltip, "signature tooltip");
            triggers = Set.copyOf(Objects.requireNonNull(triggers, "triggers"));
            if (triggers.isEmpty()) throw new IllegalArgumentException("signature trigger is required");
            consumer = required(consumer, "signature consumer");
        }
    }

    private static final Map<String, Definition> DEFINITIONS;

    static {
        final LinkedHashMap<String, Definition> definitions = new LinkedHashMap<>();
        register(definitions, "kallan_szeletelo", "Kallan szeletelő szele",
                "Gyorsabb, páncéltörő nyilat lő.", Set.of(Trigger.ON_SHOOT), "SignatureItemListener#onShoot");
        register(definitions, "glatziendorfi_jegvert", "Jégvért",
                "Viselés közben csillapítja a beérkező sebzést.", Set.of(Trigger.WHILE_EQUIPPED),
                "SignatureItemListener#onPlayerDamaged");
        register(definitions, "jegsarkany_kantar", "Jégsárkány-kantár",
                "Tartósan gyorsabbá tesz egy hátast.", Set.of(Trigger.ON_USE),
                "SignatureItemListener#onKantarUse");
        register(definitions, "pyralingradi_tuzkopo", "Tűzköpő",
                "A lövedékét tűzjárta rúnák erősítik.", Set.of(Trigger.ON_SHOOT),
                "SignatureItemListener#onShoot");
        register(definitions, "verszavanna_agyara", "Vérszavanna agyara",
                "Közelharci találatkor vérszomjas erőt szabadít fel.", Set.of(Trigger.ON_HIT),
                "SignatureItemListener#onMelee");
        register(definitions, "fonix_tollkopeny", "Főnix tollköpeny",
                "Viselőjét megóvja a természetes tűztől.", Set.of(Trigger.WHILE_EQUIPPED),
                "SignatureItemListener#onPlayerDamaged");
        register(definitions, "vasmuvek_csakanya", "Vasművek csákánya",
                "Ércbányászatkor ritkán megkettőzi a kitermelést.", Set.of(Trigger.ON_BLOCK_BREAK),
                "SignatureItemListener#onMine");
        register(definitions, "bokic_horgaszbot", "Bokic horgászbot",
                "Ritkán második fogást emel ki a vízből.", Set.of(Trigger.ON_FISH),
                "SignatureItemListener#onFish");
        register(definitions, "smaragdko_bankbetet", "Smaragdkő-bankbetét",
                "Használatkor Creutzérre váltható.", Set.of(Trigger.ON_USE),
                "SignatureItemListener#onUse");
        register(definitions, "szellemszarvas_bubaj", "Szellemszarvas-bűbáj",
                "Tartós cooldown mellett ködlovast idéz.", Set.of(Trigger.ON_USE),
                "SignatureItemListener#onUse");
        register(definitions, "glatziendorfi_jegtoro", "Jégtörő",
                "Lelassított célpont ellen nagyobbat sebez.", Set.of(Trigger.ON_HIT),
                "SignatureItemListener#onMelee");
        register(definitions, "miinus_haragja", "Miinus haragja",
                "Alacsony életerőn nagyobb közelharci sebzést ad.", Set.of(Trigger.ON_HIT),
                "SignatureItemListener#onMelee");
        register(definitions, "sarkanycsont_ij", "Sárkánycsont íj",
                "Lövedéke a sárkánycsont erejét hordozza.", Set.of(Trigger.ON_SHOOT),
                "SignatureItemListener#onShoot");
        register(definitions, "zhoris_langnyelve", "Zhoris lángnyelve",
                "Égő célpont ellen erősödik, máskor lángot lobbanthat.", Set.of(Trigger.ON_HIT),
                "SignatureItemListener#onMelee");
        register(definitions, "napfogyatkozas", "Napfogyatkozás",
                "Éjszaka erősebb lövedékeket lő.", Set.of(Trigger.ON_SHOOT),
                "SignatureItemListener#onShoot");
        DEFINITIONS = Map.copyOf(definitions);
    }

    private SignatureEffectRegistry() {
    }

    public static Optional<Definition> find(final String id) {
        return id == null || id.isBlank()
                ? Optional.empty() : Optional.ofNullable(DEFINITIONS.get(ItemStatCatalog.normalizeId(id)));
    }

    public static Definition require(final String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("unknown signature effect: " + id));
    }

    public static Map<String, Definition> definitions() {
        return DEFINITIONS;
    }

    private static void register(final Map<String, Definition> definitions, final String id,
                                 final String name, final String tooltip, final Set<Trigger> triggers,
                                 final String consumer) {
        final Definition definition = new Definition(id, name, tooltip, triggers, consumer);
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalStateException("duplicate signature effect: " + definition.id());
        }
    }

    private static String required(final String raw, final String label) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException(label + " is required");
        return raw.trim();
    }
}
