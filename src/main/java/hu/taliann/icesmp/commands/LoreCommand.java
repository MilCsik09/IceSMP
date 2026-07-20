package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * D18 — /lore: a kódex ingame felszíne. Témánként 4-6 sor kánon-szöveg chatben
 * (a messages faction-lore.* kulcsaiból, inline defaultokkal — az adminok a
 * messages.yml-ben átírhatják/bővíthetik). Ugyanezek a szövegek táblákra/
 * NPC-dialógusba is emelhetők a fővárosokban (admin-elhelyezés).
 */
public final class LoreCommand implements BasicCommand {

    private static final List<String> TOPICS = List.of(
            "lang", "fagy", "menedek", "kitaszitottak", "fa", "kapu", "suttogok",
            "melyseg", "korszakok", "bokic");

    /** Téma → (messages-kulcs-prefix, sorok inline defaultja). A szövegek a kódexből (docs/LORE.md). */
    private static final Map<String, List<String>> DEFAULTS = Map.ofEntries(
            Map.entry("melyseg", List.of(
                    "&7&l— A Mélység Népe —",
                    "&7A világ első kovácsai: Asterlayna mélybe hullt szilánkjai körül ébredtek,",
                    "&7ők verték a Mételytépőt, s ők vájták a tárnákat, amikben ma borostyánná",
                    "&7dermedt az idő. Aztán egy nap minden kalapács elhallgatott.",
                    "&8„Túl mélyre ástunk — és a csend visszanézett.” Több feljegyzés nincs.")),
            Map.entry("korszakok", List.of(
                    "&6&l— A Korszakok Könyve —",
                    "&7A krónikások könyve, amelybe minden szezon — minden korszak — beíratik:",
                    "&7bajnokok, hősök, bukások. A lap fordulása előtt a világ felbolydul,",
                    "&7s a Lapforduló Őre a főváros falainál vigyázza az átmenetet.",
                    "&8Amit a Könyv megjegyzett, azt a felejtés sem veszi vissza.")),
            Map.entry("bokic", List.of(
                    "&3&l— A Bokic folyó —",
                    "&7A Menedék ütőere: sosem fagy be, mert — így mondják — egy alvó szív",
                    "&7ere. Partján gyógyfű terem, vizében kövér pisztráng jár, s a hídjain",
                    "&7sok száz éve minden vándor átkelt már legalább egyszer.",
                    "&8A Bokic mindent visz — és mindent visszahoz, csak várni kell.")),
            Map.entry("lang", List.of(
                    "&6&l— Perinfernicitas, a Láng népe —",
                    "&7A Vérszavanna fiai, Pyralingrad kohóinak örökösei. I. Zhoris lángmadarai",
                    "&7óta vallják: a tűz nem pusztít — megtisztít. Soleil főnixei a hadijelvényeik,",
                    "&7a parázs a pénzük, s a hőség, mely mást megöl, nekik otthon.",
                    "&8A Láng nem felejt. A Láng emlékezetből ég.")),
            Map.entry("fagy", List.of(
                    "&b&l— Cryghaliris, a Fagy népe —",
                    "&7A Jégmezők urai, Glatziendorf falainak őrzői. Kallan jégsárkányai alszanak",
                    "&7a gleccserek alatt; pikkelyükből vért, leheletükből penge készül. A hó nekik",
                    "&7nem tél — emlék: minden pehely egy név, amit a fagy megőrzött.",
                    "&8Ami egyszer jéggé lett, azt Cryghaliris sosem engedi el.")),
            Map.entry("menedek", List.of(
                    "&f&l— Ryanora & Caldestera, a Menedék —",
                    "&7A Bokic-mente gazdag völgye, Arkynn békés öröksége. Caldestera kapuin belül",
                    "&7tilos a fegyver — a Bankárszövetség Creutzérje beszél helyette. Vándor, itt",
                    "&7nem kérdezik, honnan jöttél; csak azt, mit hoztál a piacra.",
                    "&8A béke is üzlet. Caldesterában a legjobb üzlet.")),
            Map.entry("kitaszitottak", List.of(
                    "&8&l— A Kitaszítottak —",
                    "&7Mortengrad romjai közt élnek, akiket a bűn vagy a Suttogás a falakon kívülre",
                    "&7taszított. A Csontszámvevő veretét használják, a Néma Királynő élőhalottai",
                    "&7békén hagyják őket. Nincs otthonuk, amire honvágyuk lehetne.",
                    "&8A kitaszítottat nem a sötét választotta. Csak a sötét fogadta be.")),
            Map.entry("fa", List.of(
                    "&a&l— Az Élet Fája —",
                    "&7Asterlayna teste fölött nőtt, gyökerei a Mélység Népének csarnokaiig érnek.",
                    "&7A Hetedik Vérháború megsebezte; azóta lassan, kín közt gyógyul — s vele",
                    "&7gyógyul a világ. A Lélekkapocs minden darabja az ő ajándéka.",
                    "&8Amíg a Fa áll, a Felsőknek van miért visszaemlékezniük.")),
            Map.entry("kapu", List.of(
                    "&4&l— A Kárhozat Kapuja —",
                    "&7A Senkiföldjén ásít, a Jégmezők és a Vérszavanna közt: az óriás portál,",
                    "&7amely a Hetedik Vérháborút kirobbantotta. Körülötte nincs törvény, nincs",
                    "&7bűn — csak préda és zsákmány. A Kapu lehelete a bátrakat is megőrli.",
                    "&8Aki a Kapuhoz megy, az nem hős. Az éhes.")),
            Map.entry("suttogok", List.of(
                    "&5&l— A Suttogók —",
                    "&7Azt mondják, nincsenek. Azt mondják, a szél zúg csak a sculk felett.",
                    "&7Azt mondják, a Néma Királynő első suttogása még mindig keres valakit,",
                    "&7aki meghallja. Azt mondják. Azt mondják…",
                    "&8(Ha hallottad, már tudod. Ha nem, örülj neki.)")));

    private final MessageManager messageManager;

    public LoreCommand(final MessageManager messageManager) {
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final var sender = commandSourceStack.getSender();
        final String topic = args.length == 0 ? "" : normalize(args[0]);
        final List<String> defaults = DEFAULTS.get(topic);
        if (defaults == null) {
            sender.sendMessage(messageManager.get("lore-usage",
                    "&7A kódex lapjai: &f/lore <lang|fagy|menedek|kitaszitottak|fa|kapu|suttogok|melyseg|korszakok|bokic>"));
            return;
        }
        for (int i = 0; i < defaults.size(); i++) {
            sender.sendMessage(messageManager.get("faction-lore." + topic + "." + (i + 1), defaults.get(i)));
        }
    }

    /** Aliasok: frakció-kódnevek és köznapi formák is működnek. */
    private static String normalize(final String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "red", "piros", "perinfernicitas" -> "lang";
            case "blue", "kek", "cryghaliris" -> "fagy";
            case "neutral", "semleges", "ryanora", "caldestera" -> "menedek";
            case "dark", "sotet", "mortengrad", "kitaszitott" -> "kitaszitottak";
            case "eletfa", "elet-fa" -> "fa";
            case "karhozat", "doom" -> "kapu";
            case "suttogas", "whisper" -> "suttogok";
            case "torpok", "melyseg-nepe" -> "melyseg";
            case "konyv", "kronika-lore", "korszak" -> "korszakok";
            case "folyo" -> "bokic";
            default -> raw.toLowerCase(Locale.ROOT);
        };
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return TOPICS.stream().filter(t -> t.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
