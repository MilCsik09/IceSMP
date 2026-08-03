#!/usr/bin/env python3
from __future__ import annotations
import pathlib, re
ROOT = pathlib.Path(__file__).resolve().parents[2]

def read(p): return (ROOT/p).read_text(encoding='utf-8')
def write(p,s):
    q=ROOT/p; q.parent.mkdir(parents=True,exist_ok=True); q.write_text(s,encoding='utf-8')
def once(p,old,new):
    s=read(p); c=s.count(old)
    if c!=1: raise RuntimeError(f'{p}: expected 1 occurrence, got {c}: {old[:120]!r}')
    write(p,s.replace(old,new,1))
def regex_once(p,pat,repl,flags=0):
    s=read(p); n,c=re.subn(pat,repl,s,count=1,flags=flags)
    if c!=1: raise RuntimeError(f'{p}: regex expected 1, got {c}: {pat}')
    write(p,n)

# ---------------- Config GUI catalog and rendering ----------------
p='src/main/java/hu/taliann/icesmp/gui/ConfigMenuGUI.java'
s=read(p)
s=s.replace(' * meglévő override-mechanizmusra épül (a data-folder config.yml-be ír, amit a\n * ConfigManager UTOLSÓKÉNT fésül be — így minden kulcsot felülír), tehát minden\n * módosítás restart nélkül él. A teljes kulcskészlethez továbbra is a\n * {@code /icesmp config set|get|find} a felület; ez a menü a kurátort listát adja.',
''' * meglévő override-mechanizmusra épül, de kattintáskor csak egy játékoshoz kötött
 * tranzakciót módosít. A config.yml kizárólag a SAVE gombbal, egyetlen batch-ben íródik;
 * CANCEL/bezárás nem ment. A teljes kulcskészlethez továbbra is a
 * {@code /icesmp config set|get|find} a felület; ez a menü explicit allowlist.''')
s=s.replace('    public enum EntryType { TOGGLE, NUMBER, INTEGER, CYCLE }', '''    public enum EntryType { TOGGLE, NUMBER, INTEGER, CYCLE }
    public enum ReloadMode { LIVE, RELOAD_HOOK, RESTART_REQUIRED }
''')
s=s.replace('        static Entry cycle(final String key, final String label, final List<String> options) {\n            return new Entry(key, label, EntryType.CYCLE, 0, 0, 0, options);\n        }\n    }', '''        static Entry cycle(final String key, final String label, final List<String> options) {
            return new Entry(key, label, EntryType.CYCLE, 0, 0, 0, options);
        }

        public ReloadMode reloadMode() {
            if (key.equals("factions.tax.enabled") || key.equals("factions.tax.interval-minutes")) {
                return ReloadMode.RESTART_REQUIRED;
            }
            if (key.startsWith("motd.") || key.startsWith("sit.") || key.startsWith("crates.")
                    || key.startsWith("resource-pack.") || key.startsWith("factions.passives.")
                    || key.startsWith("factions.whisper.") || key.startsWith("professions.recipes.")) {
                return ReloadMode.RELOAD_HOOK;
            }
            return ReloadMode.LIVE;
        }
    }''')
needle='''                Entry.toggle("world-events.safety.enabled", "Játékos-/border spawn-biztonság"),
                Entry.number("world-events.safety.min-horizontal-distance-blocks", "Minimum játékostávolság (blokk)", 8, 0, 2048),'''
repl='''                Entry.toggle("world-events.safety.enabled", "Játékos-/border spawn-biztonság"),
                Entry.toggle("world-events.safety.ignore-spectators", "Spectatorok kihagyása"),
                Entry.toggle("world-events.safety.ignore-vanished", "Vanish adminok kihagyása"),
                Entry.toggle("world-events.safety.ignore-admins", "Adminok kihagyása"),
                Entry.number("world-events.safety.min-horizontal-distance-blocks", "Minimum játékostávolság (blokk)", 8, 0, 2048),'''
if s.count(needle)!=1: raise RuntimeError('event safety insert anchor mismatch')
s=s.replace(needle,repl,1)
needle='''                Entry.number("world-events.safety.min-3d-distance-blocks", "Minimum 3D távolság (0=kikapcsolva)", 8, 0, 2048),
                Entry.integer("world-events.safety.search-attempts", "Biztonságos hely keresési próbák", 1, 1, 128),
                Entry.number("world-events.safety.search-max-radius-blocks", "Keresési sugár maximum", 16, 16, 4096),
                Entry.number("world-events.safety.world-border-margin-blocks", "World border biztonsági margó", 8, 0, 1024),'''
repl='''                Entry.number("world-events.safety.min-3d-distance-blocks", "Minimum 3D távolság (0=kikapcsolva)", 8, 0, 2048),
                Entry.number("world-events.safety.min-world-spawn-distance-blocks", "Világspawn minimumtáv", 8, 0, 4096),
                Entry.toggle("world-events.safety.require-loaded-chunk", "Csak betöltött chunk"),
                Entry.integer("world-events.safety.search-attempts", "Biztonságos hely keresési próbák", 1, 1, 128),
                Entry.number("world-events.safety.search-min-radius-blocks", "Keresési sugár minimum", 16, 0, 4096),
                Entry.number("world-events.safety.search-max-radius-blocks", "Keresési sugár maximum", 16, 16, 4096),
                Entry.number("world-events.safety.world-border-margin-blocks", "World border biztonsági margó", 8, 0, 1024),
                Entry.number("world-events.safety.reservation-distance-blocks", "Párhuzamos események minimumtávja", 8, 0, 2048),
                Entry.integer("world-events.safety.reservation-seconds", "Spawn-foglalás ideje (mp)", 10, 1, 3600),'''
if s.count(needle)!=1: raise RuntimeError('event safety second anchor mismatch')
s=s.replace(needle,repl,1)
needle='''        categories.put("borze", new Category("borze", "Börze és városi őrség", Material.EMERALD, List.of('''
if needle not in s: raise RuntimeError('moderation category anchor missing')
insert='''        categories.put("moderacio", new Category("moderacio", "Moderáció és vanish", Material.ENDER_EYE, List.of(
                Entry.toggle("moderation.enabled", "Natív moderáció"),
                Entry.toggle("moderation.chat-filter.enabled", "Chat-szűrő"),
                Entry.cycle("moderation.chat-filter.mode", "Chat-szűrő mód", List.of("CENSOR", "BLOCK")),
                Entry.toggle("moderation.spam.enabled", "Spam-védelem"),
                Entry.integer("moderation.spam.min-interval-millis", "Üzenetköz minimum (ms)", 100, 0, 60000),
                Entry.integer("moderation.spam.duplicate-window-seconds", "Duplikált üzenet ablak (mp)", 1, 0, 3600),
                Entry.toggle("moderation.chat-log.enabled", "Moderációs chat-log"),
                Entry.toggle("moderation.vanish.exclude-from-online-count", "Vanish kihagyása online számból"),
                Entry.toggle("moderation.vanish.allow-item-pickup", "Vanish tárgyfelvétel"),
                Entry.toggle("moderation.vanish.allow-damage", "Vanish sebzés/sebezhetőség"),
                Entry.toggle("moderation.vanish.allow-interaction", "Vanish interakció"),
                Entry.toggle("moderation.vanish.allow-chat", "Vanish chat"))));
'''
s=s.replace(needle,insert+needle,1)
pat=r'''    /\*\* A főmenü \(kategória-választó\) megnyitása\. \*/.*?    private static String formatNumber'''
replacement=r'''    /** A főmenü megnyitása kompatibilitási útvonalon, tranzakció nélkül. */
    public static void openRoot(final Player player) {
        openRoot(player, null);
    }

    /** A főmenü: négy kategóriasor + explicit mentés/elvetés. */
    public static void openRoot(final Player player, final ConfigEditSession session) {
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(), null);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ IceSMP Config", NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        final int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        int index = 0;
        for (final Category category : CATEGORIES.values()) {
            if (index >= slots.length) {
                throw new IllegalStateException("Config GUI category capacity exceeded: " + CATEGORIES.size());
            }
            final int slot = slots[index++];
            inventory.setItem(slot, tile(category.icon(), "&b" + category.title(),
                    List.of("&7" + category.entries().size() + " kulcs", "&eKattints a megnyitáshoz")));
            holder.bind(slot, "CAT:" + category.id());
        }
        if (session != null) {
            inventory.setItem(45, tile(Material.BARRIER, "&cElvetés", List.of("&7Nem ír config.yml-t.")));
            holder.bind(45, "CANCEL");
            inventory.setItem(49, tile(session.dirty() ? Material.LIME_DYE : Material.GRAY_DYE,
                    session.dirty() ? "&aMentés" : "&7Nincs módosítás",
                    List.of("&7Egyetlen tranzakcióban ment.")));
            holder.bind(49, "SAVE");
        }
        inventory.setItem(53, tile(Material.BARRIER, "&cBezárás", List.of("&7A nem mentett módosítások elvesznek.")));
        holder.bind(53, "CLOSE");
        player.openInventory(inventory);
    }

    public static void openCategory(final Player player, final String categoryId, final ConfigManager configManager) {
        openCategory(player, categoryId, configManager, null);
    }

    /** Egy kategória-lap a staged értékekkel; középső kattintás reseteli az override-ot. */
    public static void openCategory(final Player player, final String categoryId, final ConfigManager configManager,
                                    final ConfigEditSession session) {
        final Category category = CATEGORIES.get(categoryId);
        if (category == null) {
            openRoot(player, session);
            return;
        }
        if (category.entries().size() > 45) {
            throw new IllegalStateException("Config GUI category capacity exceeded: " + category.id());
        }
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(), categoryId);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ " + category.title(), NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        int slot = 0;
        for (final Entry entry : category.entries()) {
            inventory.setItem(slot, entryTile(entry, configManager, session));
            holder.bind(slot, switch (entry.type()) {
                case TOGGLE -> "TOGGLE:" + entry.key();
                case CYCLE -> "CYCLE:" + entry.key();
                default -> "NUM:" + entry.key();
            });
            slot++;
        }
        inventory.setItem(45, tile(Material.BARRIER, "&cElvetés", List.of("&7Nem ír config.yml-t.")));
        holder.bind(45, "CANCEL");
        inventory.setItem(48, tile(Material.ARROW, "&7Vissza", List.of()));
        holder.bind(48, "BACK");
        inventory.setItem(49, tile(session != null && session.dirty() ? Material.LIME_DYE : Material.GRAY_DYE,
                session != null && session.dirty() ? "&aMentés" : "&7Nincs módosítás",
                List.of("&7Egyetlen tranzakcióban ment.")));
        holder.bind(49, "SAVE");
        inventory.setItem(53, tile(Material.BARRIER, "&cBezárás", List.of("&7A nem mentett módosítások elvesznek.")));
        holder.bind(53, "CLOSE");
        player.openInventory(inventory);
    }

    private static ItemStack entryTile(final Entry entry, final ConfigManager configManager,
                                       final ConfigEditSession session) {
        final List<String> lore = new ArrayList<>();
        lore.add("&8" + entry.key());
        final Object displayed = session == null ? configManager.getConfiguration().get(entry.key()) : session.value(entry.key());
        final Object defaultValue = session == null ? configManager.getBaseValue(entry.key()) : session.defaultValue(entry.key());
        if (session != null && session.hasPending(entry.key())) {
            lore.add("&eNem mentett módosítás");
        }
        lore.add("&7Alapérték: &f" + String.valueOf(defaultValue));
        lore.add(switch (entry.reloadMode()) {
            case LIVE -> "&aHatás: élő olvasás";
            case RELOAD_HOOK -> "&eHatás: mentés utáni reload-hook";
            case RESTART_REQUIRED -> "&cHatás: szerver-újraindítás szükséges";
        });
        lore.add("&7Középső katt: alapérték/reset");
        switch (entry.type()) {
            case TOGGLE -> {
                final boolean value = displayed instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(displayed));
                lore.add(value ? "&aBekapcsolva" : "&cKikapcsolva");
                lore.add("&eKattints a staged váltáshoz");
                return tile(value ? Material.LIME_DYE : Material.GRAY_DYE,
                        (value ? "&a" : "&c") + entry.label(), lore);
            }
            case CYCLE -> {
                final String value = displayed == null ? "?" : String.valueOf(displayed);
                lore.add("&fJelenleg: &b" + value);
                lore.add("&7Opciók: &f" + String.join(" / ", entry.options()));
                lore.add("&eKattints a következőhöz");
                return tile(Material.COMPARATOR, "&b" + entry.label(), lore);
            }
            default -> {
                final double value = displayed instanceof Number number ? number.doubleValue() : 0.0D;
                lore.add("&fJelenleg: &b" + formatNumber(entry, value));
                lore.add("&7Bal katt: &f+" + formatStep(entry) + " &7| Jobb katt: &f−" + formatStep(entry));
                lore.add("&7SHIFT = ötszörös lépés");
                return tile(Material.PAPER, "&b" + entry.label(), lore);
            }
        }
    }

    private static String formatNumber'''
n,c=re.subn(pat,replacement,s,count=1,flags=re.S)
if c!=1: raise RuntimeError(f'ConfigMenuGUI render regex mismatch {c}')
s=n
needle='''    /** A kulcshoz tartozó katalógus-bejegyzés (a listener érték-léptetéséhez). */
    public static Entry findEntry'''
insert='''    /** Flattened immutable allowlist used by transactions and build-time coverage validation. */
    public static List<Entry> allEntries() {
        return CATEGORIES.values().stream().flatMap(category -> category.entries().stream()).toList();
    }

    /** A kulcshoz tartozó katalógus-bejegyzés (a listener érték-léptetéséhez). */
    public static Entry findEntry'''
if s.count(needle)!=1: raise RuntimeError('allEntries anchor mismatch')
s=s.replace(needle,insert,1)
write(p,s)

print('stage3 part 2a applied')
