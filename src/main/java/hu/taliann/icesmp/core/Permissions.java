package hu.taliann.icesmp.core;

import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The plugin's canonical permission scheme, registered at enable time.
 *
 * <p><b>Séma:</b> minden admin-node {@code icesmp.admin.<domain>} alakú, plusz az
 * {@code icesmp.territory.builder} szerep-node (építők a védett zónákban). A
 * {@link #ALL} ({@code icesmp.admin.all}) valódi szülő-node: az összes admin-node a
 * gyereke, tehát wildcard-támogatás nélküli permission-pluginnal is egyetlen sorban
 * megadható a teljes admin-készlet. Minden node default=OP (az eddigi, regisztrálatlan
 * viselkedéssel azonos: operátornak minden jár).</p>
 *
 * <p><b>Visszafelé kompatibilitás:</b> a korábbi, kilógó nevű node-ok
 * ({@code icesmp.admin}, {@code icesmp.job.admin}, {@code icesmp.currency.admin},
 * {@code icesmp.faction.admin}, {@code icesmp.relic.admin}) alias-ként regisztrálódnak
 * a kanonikus megfelelőjük gyerekével — a meglévő LuckPerms-beállítások változtatás
 * nélkül tovább működnek.</p>
 */
public final class Permissions {

    /** Szülő super-node: az ÖSSZES alábbi node-ot megadja egyben. */
    public static final String ALL = "icesmp.admin.all";

    public static final String RELOAD = "icesmp.admin.reload";
    public static final String CONFIG = "icesmp.admin.config";
    public static final String EVENTS = "icesmp.admin.events";
    public static final String NPC = "icesmp.admin.npc";
    public static final String QUEST = "icesmp.admin.quest";
    public static final String PARKOUR = "icesmp.admin.parkour";
    public static final String EXCHANGE_BOARD = "icesmp.admin.exchangeboard";
    public static final String TERRITORY = "icesmp.admin.territory";
    public static final String TERRITORY_BYPASS = "icesmp.admin.territory.bypass";
    public static final String SPEC = "icesmp.admin.spec";
    public static final String PROFESSION = "icesmp.admin.profession";
    public static final String JOB = "icesmp.admin.job";
    public static final String CURRENCY = "icesmp.admin.currency";
    public static final String FACTION = "icesmp.admin.faction";
    public static final String RELIC = "icesmp.admin.relic";
    public static final String SINNER = "icesmp.admin.sinner";
    public static final String WAR = "icesmp.admin.war";
    public static final String CRATE = "icesmp.admin.crate";
    public static final String MODERATION = "icesmp.admin.moderation";
    public static final String MODERATION_WARN = "icesmp.moderation.warn";
    public static final String MODERATION_KICK = "icesmp.moderation.kick";
    public static final String MODERATION_MUTE = "icesmp.moderation.mute";
    public static final String MODERATION_BAN = "icesmp.moderation.ban";
    public static final String MODERATION_HISTORY = "icesmp.moderation.history";
    public static final String MODERATION_SOCIALSPY = "icesmp.moderation.socialspy";
    public static final String MODERATION_VANISH = "icesmp.moderation.vanish";
    public static final String MODERATION_VANISH_SEE = "icesmp.moderation.vanish.see";
    public static final String MODERATION_OFFLINE_TP = "icesmp.moderation.offlinetp";
    public static final String MODERATION_INVENTORY_READ = "icesmp.moderation.inventory.read";
    public static final String MODERATION_INVENTORY_EDIT = "icesmp.moderation.inventory.edit";
    public static final String MODERATION_GUI = "icesmp.moderation.gui";
    public static final String MESSAGE = "icesmp.message";
    public static final String INSPECT = "icesmp.admin.inspect";
    public static final String ITEM = "icesmp.admin.item";
    public static final String TERRITORY_BUILDER = "icesmp.territory.builder";

    private Permissions() {
    }

    /** Registers every canonical node, the {@link #ALL} parent and the legacy aliases. Idempotent. */
    public static void register() {
        final PluginManager pm = Bukkit.getPluginManager();

        final Map<String, String> canonical = new LinkedHashMap<>();
        canonical.put(RELOAD, "Config + üzenetek újratöltése (/icesmp reload)");
        canonical.put(CONFIG, "Ingame config-felülbírálás (/icesmp config)");
        canonical.put(EVENTS, "Világesemény-triggerek (/events)");
        canonical.put(NPC, "NPC-kötések (/npcbind)");
        canonical.put(QUEST, "Quest admin + force-complete (/quest admin)");
        canonical.put(PARKOUR, "Parkour-pálya kijelölés (/parkour set*)");
        canonical.put(EXCHANGE_BOARD, "Árfolyam-tábla kezelés (/exchangeboard)");
        canonical.put(TERRITORY, "Territórium- és claim-admin (/territory, /claim admin)");
        canonical.put(TERRITORY_BYPASS, "Zóna- és claim-védelem teljes megkerülése");
        canonical.put(SPEC, "Specializáció-admin (/spec más játékosra)");
        canonical.put(PROFESSION, "Szakma-admin (/profession más játékosra)");
        canonical.put(JOB, "Kaszt-admin (/class addxp/setxp/givecatalyst/unlockspell/admin)");
        canonical.put(CURRENCY, "Egyenleg-admin (/currency set)");
        canonical.put(FACTION, "Frakció-admin (/faction set, kassza, király)");
        canonical.put(RELIC, "Relikvia-admin (/relic give/reset)");
        canonical.put(SINNER, "Bűn-kezelés (/sinner)");
        canonical.put(WAR, "Hadi-ablak admin (/faction war start|stop)");
        canonical.put(CRATE, "Láda-admin (/crate set/remove/give)");
        canonical.put(INSPECT, "Játékos-inspektor (/icesmp inspect)");
        canonical.put(ITEM, "Admin item-adás (/iceitem)");
        canonical.put(TERRITORY_BUILDER, "Építés a védett zónákban (szerver-építő szerep)");

        final Map<String, Boolean> allChildren = new LinkedHashMap<>();
        for (final Map.Entry<String, String> entry : canonical.entrySet()) {
            registerNode(pm, new Permission(entry.getKey(), entry.getValue(), PermissionDefault.OP));
            allChildren.put(entry.getKey(), Boolean.TRUE);
        }

        final Map<String, String> moderationNodes = new LinkedHashMap<>();
        moderationNodes.put(MODERATION_WARN, "Figyelmeztetés kiadása (/warn)");
        moderationNodes.put(MODERATION_KICK, "Játékos kirúgása (/kick)");
        moderationNodes.put(MODERATION_MUTE, "Némítás és feloldás (/mute, /unmute)");
        moderationNodes.put(MODERATION_BAN, "Kitiltás és feloldás (/ban, /tempban, /unban)");
        moderationNodes.put(MODERATION_HISTORY, "Büntetési előzmények megtekintése");
        moderationNodes.put(MODERATION_SOCIALSPY, "Natív privát üzenetek megfigyelése");
        moderationNodes.put(MODERATION_VANISH, "Vanish állapot kezelése");
        moderationNodes.put(MODERATION_VANISH_SEE, "Vanish állapotú adminok megtekintése");
        moderationNodes.put(MODERATION_OFFLINE_TP, "Teleport az utolsó kijelentkezési helyre");
        moderationNodes.put(MODERATION_INVENTORY_READ, "Online inventory és ender-láda olvasása");
        moderationNodes.put(MODERATION_INVENTORY_EDIT, "Online inventory és ender-láda szerkesztése");
        moderationNodes.put(MODERATION_GUI, "Natív moderációs admin GUI");
        final Map<String, Boolean> moderationChildren = new LinkedHashMap<>();
        for (final Map.Entry<String, String> entry : moderationNodes.entrySet()) {
            registerNode(pm, new Permission(entry.getKey(), entry.getValue(), PermissionDefault.OP));
            moderationChildren.put(entry.getKey(), Boolean.TRUE);
        }
        registerNode(pm, new Permission(MODERATION,
                "IceSMP natív moderációs jogosultságcsomag", PermissionDefault.OP, moderationChildren));
        allChildren.put(MODERATION, Boolean.TRUE);
        registerNode(pm, new Permission(MESSAGE,
                "IceSMP privát üzenetküldés (/msg, /tell, /w, /reply)", PermissionDefault.TRUE));
        registerNode(pm, new Permission(ALL,
                "IceSMP super-admin: az összes IceSMP admin-node egyben", PermissionDefault.OP, allChildren));

        // Legacy aliasok: a régi node megadása a kanonikus gyerekét is megadja.
        registerNode(pm, alias("icesmp.admin", JOB, SINNER));
        registerNode(pm, alias("icesmp.job.admin", JOB));
        registerNode(pm, alias("icesmp.currency.admin", CURRENCY));
        registerNode(pm, alias("icesmp.faction.admin", FACTION));
        registerNode(pm, alias("icesmp.relic.admin", RELIC));
    }

    private static Permission alias(final String legacyNode, final String... canonicalChildren) {
        final Map<String, Boolean> children = new LinkedHashMap<>();
        for (final String child : canonicalChildren) {
            children.put(child, Boolean.TRUE);
        }
        return new Permission(legacyNode,
                "Elavult alias — használd helyette: " + String.join(", ", canonicalChildren),
                PermissionDefault.OP, children);
    }

    private static void registerNode(final PluginManager pm, final Permission permission) {
        // Idempotent: enable/disable ciklusnál a korábbi definíció cserélődik.
        if (pm.getPermission(permission.getName()) != null) {
            pm.removePermission(permission.getName());
        }
        pm.addPermission(permission);
    }
}
