# IceSMP permissionreferencia

> Dokumentált HEAD: `4643ab53586f0c1ee7352df16dcd477013e6fad4`

A node-ok a runtime `Permissions.register()` gráfjából, a parancs- és listenerellenőrzésekből, valamint a bundled crate-config dinamikus permissionjéből származnak. A `paper-plugin.yml` nem deklarálja ezt a gráfot; az itt jelzett default a tényleges futásidejű regisztráció.

## Gyors kiosztási szabályok

- `icesmp.admin.all` csak vezető adminnak/üzemeltetőnek való.
- A `icesmp.admin.moderation` csomag minden moderációs leaf node-ot megad; finomhangolt csapatnál inkább leaf node-okat ossz.
- `inventory.edit`, `admin.currency`, `admin.crate`, `admin.item`, `admin.territory.bypass` különösen érzékeny.
- A legacy node-ok működnek, de új kiosztásnál a kanonikus `icesmp.admin.*` neveket használd.
- A crate-definíciók tetszőleges, `icesmp.` prefixű plusz node-ot regisztrálhatnak default `FALSE` értékkel.

## Teljes node-lista (44)

| Node | Leírás | Célközönség | Command | GUI | Listener/service | Parent | Default | Érzékenység | Javasolt kiosztás | Deployed változás |
|---|---|---|---|---|---|---|---|---|---|---|
| `icesmp.admin.all` | Az összes kanonikus admin-node szülője. | Vezető admin | — | Admin panel minden jogosultságfüggő eleme | — | — | OP | kritikus | Csak vezető admin/üzemeltető | Új |
| `icesmp.admin.reload` | Plugin config és üzenetek reloadja; az /icesmp gyökér kapuja is. | Admin | /icesmp, /icesmp reload | Admin panel: reload | — | icesmp.admin.all | OP | magas | Admin | Megváltozott |
| `icesmp.admin.config` | Élő config override és Config GUI. | Vezető admin | /icesmp config * | Config menü | ConfigMenuGUIListener | icesmp.admin.all | OP | kritikus | Szűk üzemeltetői kör | Új |
| `icesmp.admin.events` | Világesemények kézi indítása és spawnpontkezelés. | Eventes/Admin | /events adminágak | Esemény/Admin menü | — | icesmp.admin.all | OP | magas | Eventes és admin | Megváltozott |
| `icesmp.admin.npc` | NPC-kötések kezelése. | Admin/Builder | /npcbind * | Admin menü | NpcInteractionListener | icesmp.admin.all | OP | magas | NPC-t kezelő builder/admin | Megváltozott |
| `icesmp.admin.quest` | Quest admin és builder. | Admin/Eventes | /quest complete; /quest admin * | Quest builder | QuestBuilderListener | icesmp.admin.all | OP | magas | Quest designer/admin | Megváltozott |
| `icesmp.admin.parkour` | Parkour pálya létrehozása/törlése. | Builder/Admin | /parkour setstart/setfinish/remove | — | — | icesmp.admin.all | OP | magas | Builder | Megváltozott |
| `icesmp.admin.exchangeboard` | Árfolyamtábla kezelése. | Builder/Admin | /exchangeboard | Admin menü | — | icesmp.admin.all | OP | magas | Builder/admin | Megváltozott |
| `icesmp.admin.territory` | Territórium- és claim-admin. | Builder/Admin | /territory *; /claim admin unclaim | Claim/Admin menü | SelectionWandListener | icesmp.admin.all | OP | kritikus | World designer/vezető admin | Megváltozott |
| `icesmp.admin.territory.bypass` | Claim- és régióvédelem teljes megkerülése. | Vezető admin | — | — | ClaimProtectionListener; TheftListener; TerritoryProtectionService | icesmp.admin.all | OP | kritikus | Csak vezető admin | Megváltozott |
| `icesmp.admin.spec` | Más játékos specializációjának resetje. | Admin | /spec reset | — | — | icesmp.admin.all | OP | magas | Admin | Megváltozott |
| `icesmp.admin.profession` | Szakma adminmutációk. | Admin | /profession blueprint/set/clear/addxp | — | — | icesmp.admin.all | OP | magas | Admin | Megváltozott |
| `icesmp.admin.job` | Kaszt, XP, spell és katalizátor adminmutációk. | Admin | /class * | — | — | icesmp.admin.all | OP | kritikus | Admin | Új |
| `icesmp.admin.currency` | Játékosegyenleg beállítása. | Vezető admin | /currency set | — | — | icesmp.admin.all | OP | kritikus | Gazdasági admin | Új |
| `icesmp.admin.faction` | Frakció, király és kassza admin. | Vezető admin | /faction set; king set/clear; treasury | Frakció menü | — | icesmp.admin.all | OP | kritikus | Vezető admin | Új |
| `icesmp.admin.relic` | Relikvia adminátadás. | Vezető admin | /relic give | — | — | icesmp.admin.all | OP | kritikus | Vezető admin | Új |
| `icesmp.admin.sinner` | Bűnállapot adminmutáció. | Admin | /sinner | — | — | icesmp.admin.all | OP | magas | Admin | Új |
| `icesmp.admin.war` | Hadiablak kézi vezérlése. | Eventes/Admin | /faction war start/stop | — | — | icesmp.admin.all | OP | magas | Eventes/vezető admin | Új |
| `icesmp.admin.crate` | Crate hely, kulcs, stat és recovery admin. | Vezető admin | /crate set/remove/give/list/stats/resetstats/status | — | CrateListener | icesmp.admin.all | OP | kritikus | Crate-admin | Új |
| `icesmp.admin.inspect` | Összesített játékosinspektor. | Admin | /icesmp inspect | — | — | icesmp.admin.all | OP | magas | Admin | Új |
| `icesmp.admin.item` | Bármely natív/plugin item kiadása. | Fejlesztő/vezető admin | /iceitem | Admin menü | — | icesmp.admin.all | OP | kritikus | Csak fejlesztő/vezető admin | Új |
| `icesmp.territory.builder` | Építés a védett zónákban teljes admin-bypass nélkül. | Builder | — | — | TerritoryProtectionService | icesmp.admin.all | OP | magas | Megbízható builder | Megváltozott |
| `icesmp.admin.moderation` | Moderációs csomag szülőnode; a report admin is közvetlenül használja. | Moderátor/Admin | /reports | Moderációs GUI reports gomb | Moderation/Report listenerek | icesmp.admin.all | OP | kritikus | Moderátori szerepcsomag | Új |
| `icesmp.moderation.warn` | Figyelmeztetés. | Moderátor | /warn | Moderációs GUI 10 | — | icesmp.admin.moderation | OP | magas | Moderátor | Új |
| `icesmp.moderation.kick` | Kirúgás. | Moderátor | /kick | Moderációs GUI 13 | — | icesmp.admin.moderation | OP | magas | Moderátor | Új |
| `icesmp.moderation.mute` | Némítás és feloldás. | Moderátor | /mute, /unmute | Moderációs GUI 11/14 | Chat/PM mute enforcement | icesmp.admin.moderation | OP | magas | Moderátor | Új |
| `icesmp.moderation.ban` | Ban/tempban/unban. | Admin/Moderátor | /ban, /tempban, /unban | Moderációs GUI 12/15 | Login ban enforcement | icesmp.admin.moderation | OP | kritikus | Senior moderátor/admin | Új |
| `icesmp.moderation.history` | History és aktív punishmentek. | Moderátor | /history, /punishments | Moderációs GUI 19/20 | — | icesmp.admin.moderation | OP | közepes | Moderátor | Új |
| `icesmp.moderation.socialspy` | SocialSpy állapot. | Moderátor | /socialspy | Moderációs GUI 30 | PrivateMessageCommand | icesmp.admin.moderation | OP | magas | Senior moderátor | Új |
| `icesmp.moderation.vanish` | Vanish kezelése. | Admin | /vanish | Moderációs GUI 31 | VanishManager/listenerek | icesmp.admin.moderation | OP | magas | Admin | Új |
| `icesmp.moderation.vanish.see` | Vanish játékosok láthatósága. | Vezető admin | — | Játékoslista/moderációs célpontszűrés | VanishManager | icesmp.admin.moderation | OP | magas | Admin | Új |
| `icesmp.moderation.offlinetp` | Utolsó logouthelyre teleport. | Moderátor/Admin | /offlinetp | Moderációs GUI 28/29 | Logout location capture | icesmp.admin.moderation | OP | magas | Admin | Új |
| `icesmp.moderation.inventory.read` | Online inventory/ender read. | Moderátor | /invsee ... read | Moderációs GUI 22/24 | InvseeGUIListener | icesmp.admin.moderation | OP | magas | Senior moderátor | Új |
| `icesmp.moderation.inventory.edit` | Online inventory/ender szerkesztés escrow-val. | Vezető admin | /invsee ... edit | Moderációs GUI 23/25 | InvseeGUIListener | icesmp.admin.moderation | OP | kritikus | Csak vezető admin | Új |
| `icesmp.moderation.gui` | Moderációs GUI megnyitása. | Moderátor | /moderation | Moderációs GUI | ModerationGUIListener | icesmp.admin.moderation | OP | közepes | Moderátor | Új |
| `icesmp.crate.use` | Natív crate böngészés, kulcsvásárlás és nyitás alapkapuja. | Játékos | /crate játékoságak; fizikai nyitás | Crate böngésző/spin | CrateListener; CrateManager | — | TRUE | közepes | Minden játékos | Új |
| `icesmp.message` | Natív privát üzenetek. | Játékos | /msg, /tell, /w, /reply | — | PrivateMessageCommand | — | TRUE | alacsony | Minden játékos | Új |
| `icesmp.sit` | Natív /sit és click-to-sit. | Játékos | /sit | — | SitInteractionListener és lifecycle listenerek | — | TRUE | alacsony | Minden játékos | Új |
| `icesmp.admin` | Legacy alias: kaszt- és sinner-admin. | Admin | /class adminágak; /sinner | — | — | — | OP | magas | Migráció után kanonikus node-ok | Megváltozott |
| `icesmp.job.admin` | Legacy alias az icesmp.admin.job node-ra. | Admin | /class * | — | — | — | OP | magas | Csak kompatibilitás | Megváltozott |
| `icesmp.currency.admin` | Legacy alias az icesmp.admin.currency node-ra. | Admin | /currency set | — | — | — | OP | kritikus | Csak kompatibilitás | Megváltozott |
| `icesmp.faction.admin` | Legacy alias az icesmp.admin.faction node-ra. | Admin | /faction adminágak | — | — | — | OP | kritikus | Csak kompatibilitás | Megváltozott |
| `icesmp.relic.admin` | Legacy alias az icesmp.admin.relic node-ra. | Admin | /relic give | — | — | — | OP | kritikus | Csak kompatibilitás | Megváltozott |
| `icesmp.crate.ritka` | A bundled ritka crate konfigurált hozzáférési kapuja. | Játékos/tesztelő | /crate info/preview/buy és fizikai nyitás | Crate böngésző | CrateManager/CrateListener | — | FALSE | közepes | A ritka crate-re jogosult csoport | Új |

## Parent- és kompatibilitási gráf

- `icesmp.admin.all` gyerekei: minden kanonikus admin-domain, a `icesmp.territory.builder` és a `icesmp.admin.moderation` csomag.
- `icesmp.admin.moderation` gyerekei: a 12 `icesmp.moderation.*` leaf node.
- Nem gyereke az `admin.all` node-nak: `icesmp.crate.use`, `icesmp.message`, `icesmp.sit` és a per-crate dinamikus node-ok.
- Legacy: `icesmp.admin` → `icesmp.admin.job` + `icesmp.admin.sinner`; `icesmp.job.admin`, `icesmp.currency.admin`, `icesmp.faction.admin`, `icesmp.relic.admin` → megfelelő kanonikus node.

## Dinamikus crate-node

A release bundled `config/crates.yml` fájljában a `koznapi` crate permissionje üres, a `ritka` crate-é `icesmp.crate.ritka`. A runtime minden valid, `icesmp.` prefixű crate-node-ot `FALSE` defaulttal regisztrál. Ezért élő config módosítása új node-ot hozhat létre; az élő kiosztás a csatolt szerverconfig nélkül nem bizonyítható.

## Forrásbizonyíték

- `src/main/java/hu/taliann/icesmp/core/Permissions.java`: node-ok, defaultok, parentek, legacy aliasok;
- `src/main/java/hu/taliann/icesmp/core/IceSMPCore.java`: command wiring;
- command/listener/service fájlok: tényleges enforcement;
- `src/main/resources/config/crates.yml`: bundled dinamikus crate-node;
- gépi leltár: `source_interface_inventory.json`, minden node-hoz automatikus forráshivatkozás-listával.

## Gépileg ellenőrzött dokumentációs azonosítók

Az alábbi egyedi jelölők a permission inventory mind a 44 node-jának dokumentációs lefedettségét teszik géppel ellenőrizhetővé.

<!-- icesmp-doc-id: permission.icesmp.admin -->
<!-- icesmp-doc-id: permission.icesmp.admin.all -->
<!-- icesmp-doc-id: permission.icesmp.admin.config -->
<!-- icesmp-doc-id: permission.icesmp.admin.crate -->
<!-- icesmp-doc-id: permission.icesmp.admin.currency -->
<!-- icesmp-doc-id: permission.icesmp.admin.events -->
<!-- icesmp-doc-id: permission.icesmp.admin.exchangeboard -->
<!-- icesmp-doc-id: permission.icesmp.admin.faction -->
<!-- icesmp-doc-id: permission.icesmp.admin.inspect -->
<!-- icesmp-doc-id: permission.icesmp.admin.item -->
<!-- icesmp-doc-id: permission.icesmp.admin.job -->
<!-- icesmp-doc-id: permission.icesmp.admin.moderation -->
<!-- icesmp-doc-id: permission.icesmp.admin.npc -->
<!-- icesmp-doc-id: permission.icesmp.admin.parkour -->
<!-- icesmp-doc-id: permission.icesmp.admin.profession -->
<!-- icesmp-doc-id: permission.icesmp.admin.quest -->
<!-- icesmp-doc-id: permission.icesmp.admin.relic -->
<!-- icesmp-doc-id: permission.icesmp.admin.reload -->
<!-- icesmp-doc-id: permission.icesmp.admin.sinner -->
<!-- icesmp-doc-id: permission.icesmp.admin.spec -->
<!-- icesmp-doc-id: permission.icesmp.admin.territory -->
<!-- icesmp-doc-id: permission.icesmp.admin.territory.bypass -->
<!-- icesmp-doc-id: permission.icesmp.admin.war -->
<!-- icesmp-doc-id: permission.icesmp.crate.ritka -->
<!-- icesmp-doc-id: permission.icesmp.crate.use -->
<!-- icesmp-doc-id: permission.icesmp.currency.admin -->
<!-- icesmp-doc-id: permission.icesmp.faction.admin -->
<!-- icesmp-doc-id: permission.icesmp.job.admin -->
<!-- icesmp-doc-id: permission.icesmp.message -->
<!-- icesmp-doc-id: permission.icesmp.moderation.ban -->
<!-- icesmp-doc-id: permission.icesmp.moderation.gui -->
<!-- icesmp-doc-id: permission.icesmp.moderation.history -->
<!-- icesmp-doc-id: permission.icesmp.moderation.inventory.edit -->
<!-- icesmp-doc-id: permission.icesmp.moderation.inventory.read -->
<!-- icesmp-doc-id: permission.icesmp.moderation.kick -->
<!-- icesmp-doc-id: permission.icesmp.moderation.mute -->
<!-- icesmp-doc-id: permission.icesmp.moderation.offlinetp -->
<!-- icesmp-doc-id: permission.icesmp.moderation.socialspy -->
<!-- icesmp-doc-id: permission.icesmp.moderation.vanish -->
<!-- icesmp-doc-id: permission.icesmp.moderation.vanish.see -->
<!-- icesmp-doc-id: permission.icesmp.moderation.warn -->
<!-- icesmp-doc-id: permission.icesmp.relic.admin -->
<!-- icesmp-doc-id: permission.icesmp.sit -->
<!-- icesmp-doc-id: permission.icesmp.territory.builder -->
