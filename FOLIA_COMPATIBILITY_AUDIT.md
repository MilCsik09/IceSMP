# Folia Kompatibilitási Audit Jelentés

**Dátum:** 2026-04-16  
**Projekt:** IceSMP  
**Migrálás típusa:** Paper → Folia  
**Státusz:** ✅ KOMPATIBILIS (kis figyelmeztetésekkel)

---

## 📋 Összefoglaló

Az IceSMP projekt **Folia-val futtatható**, de az alábbi dolgok figyelmet igényelnek:

### ✅ Kompatibilis
- Scheduler task (sync) → OK
- BukkitRunnable + runTaskLater → OK (Folia támogat)
- PDC (PersistentDataContainer) → OK
- Event listenerek → OK
- Parancs rendszer (Brigadier) → OK
- Paper Bootstrap/Loader API → OK (Folia kompatibilis)

### ⚠️ Figyelmeztetés
- **Async task** – Foliában **nem ajánlott** async scheduling. A kód jelenleg **nem használ** async taskokat (jó!)
- **Játékos keresés** – `Bukkit.getPlayer()` + `Bukkit.getPlayerExact()` – ezek szinkron, de OK az event listenerből
- **Multi-region threading** – Karakterek más regionban lehetnek, így null check szükséges

---

## 🔍 Részletes Analízis

### 1. **Scheduler Task Hívások**

#### ✅ `AngryChickenSpell.java` (38-59. sor)
```java
plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {...}, 0L, 1L);
plugin.getServer().getScheduler().runTaskLater(plugin, () -> {...}, 40L);
```
- **Státusz:** OK
- **Ok:** Mindkét hívás szinkron (sync) task, Folia támogatja
- **Megjegyzés:** A entity kezelés regionális, de a kód tartalmaz null-checket

#### ✅ `MetelytepoManager.java` (197-213. sor)
```java
new BukkitRunnable() {
    @Override
    public void run() {
        if (!target.isValid()) return;
        // ... entity módosítás ...
    }
}.runTaskLater(plugin, ticks);
```
- **Státusz:** OK
- **Ok:** Szinkron task, entity valid-check van
- **Megjegyzés:** Entity может быть a task futásakor már eltávolítva más regionban, de `isValid()` ellenőrzés van

#### ✅ `LuckyStarSpell.java` (50-64. sor)
```java
final BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
    final Player player = Bukkit.getPlayer(playerId);
    if (player == null || !isActive(player)) return;
    // ...
}, 20L);
```
- **Státusz:** OK
- **Ok:** Szinkron task, null-check van a játékosra
- **Megjegyzés:** Folia eltávolíthatja a játékost a regionból, de a null-check kezel ezt

#### ✅ `HideSpell.java` (47. sor)
```java
plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearHide(playerId), HIDE_DURATION_TICKS);
```
- **Státusz:** OK
- **Ok:** Szinkron task
- **Megjegyzés:** A `clearHide()` metódus is tartalmaz null-checket (75. sor)

#### ✅ `InnerFocusSpell.java` (44-46. sor)
```java
plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
    restorePlayer(playerId, originalWalkSpeed);
}, 20L * 5L);
```
- **Státusz:** OK
- **Ok:** Szinkron task, `restorePlayer` null-checkkel van

#### ✅ `ArmamentSpell.java` (71-77. sor)
```java
plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
    final Player onlinePlayer = Bukkit.getPlayer(playerId);
    if (onlinePlayer != null) {
        removeTaggedItems(onlinePlayer);
    }
    ACTIVE_UNTIL.remove(playerId);
}, DURATION_TICKS);
```
- **Státusz:** OK
- **Ok:** Szinkron task, null-check van

#### ✅ `CurrencyItemRefreshListener.java` (28, 37. sor)
```java
plugin.getServer().getScheduler().runTask(plugin, () -> currencyManager.refreshPlayerCurrencyItems(player));
```
- **Státusz:** OK
- **Ok:** Szinkron task, event handler-ből hívódik
- **Megjegyzés:** A játékos már nyitott event-ben van, így létezik

---

### 2. **Játékos Keresés (Bukkit.getPlayer)**

#### ✅ Szinkron Task-ban (`LuckyStarSpell.java:51`)
```java
final Player player = Bukkit.getPlayer(playerId);
```
- **Státusz:** OK (null-check van)
- **Ok:** Task-ban szinkron hívás, ha játékos offline → null

#### ✅ Szinkron Task-ban (`AngryChickenSpell.java:47`)
```java
final Player shooter = Bukkit.getPlayer(shooterId);
```
- **Státusz:** OK (null-check van)
- **Ok:** `if (shooter != null && living == shooter)` ellenőrzés

#### ✅ Event Handler-ben (`PlayerSessionCleanupListener.java:59`)
```java
final Player player = Bukkit.getPlayer(playerId);
```
- **Státusz:** OK (null-check van)
- **Ok:** Event handler szinkron, játékos eltávozhat

#### ✅ Parancs Kezelő (`SinnerCommand.java:40`, `RelicCommand.java:110`, `CurrencySetSubcommand.java:50`)
```java
final Player target = Bukkit.getPlayerExact(args[0]);
```
- **Státusz:** OK
- **Ok:** `getPlayerExact` (strict matching), opcionális parancs, nem kritikus

---

### 3. **Concurrent Map-ek (Thread-Safety)**

#### ✅ `MetelytepoManager.java`
```java
private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
private final Map<UUID, Double> frozenSpeed = new ConcurrentHashMap<>();
private final Map<UUID, Long> abilityDamageBypass = new ConcurrentHashMap<>();
```
- **Státusz:** ✅ Tökéletes
- **Ok:** `ConcurrentHashMap` biztosítja a thread-safety-t

#### ✅ Spell Magánstatikus Maps
```java
private static final Map<UUID, ...> ACTIVE_PLAYERS = new ConcurrentHashMap<>();
```
- **Státusz:** ✅ OK
- **Ok:** Concurrent maps az összes Spell-ben

---

### 4. **Event Handlerek**

#### ✅ `CurrencyItemRefreshListener.java`
```java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onInventoryClick(final InventoryClickEvent event) {...}
```
- **Státusz:** OK
- **Ok:** Standard Bukkit event, Folia támogatja
- **Megjegyzés:** Event thread-je a játékos regionjában van

#### ✅ `PlayerSessionCleanupListener.java`
```java
@EventHandler
public void onPlayerQuit(final PlayerQuitEvent event) {
    cleanupPlayerState(event.getPlayer().getUniqueId());
}
```
- **Státusz:** OK
- **Ok:** Quit/Kick event szinkron, async task **nincs**

---

### 5. **PersistentDataContainer (PDC) Hívások**

#### ✅ `MetelytepoManager.java` (78, 150, 161, 169, 173)
```java
itemStack.getItemMeta().getPersistentDataContainer().get(relicIdKey, PersistentDataType.STRING);
player.getPersistentDataContainer().getOrDefault(sinnerKey, PersistentDataType.BYTE, (byte) 0);
player.getPersistentDataContainer().set(sinnerKey, PersistentDataType.BYTE, (byte) 1);
```
- **Státusz:** ✅ OK
- **Ok:** PDC thread-safe Foliában
- **Megjegyzés:** Entity PDC-t csak annak regionjában lehet módosítani, de kód ebből nem érkezik más regionból

#### ✅ `ArmamentSpell.java` (88)
```java
meta.getPersistentDataContainer().set(armamentTag, PersistentDataType.BOOLEAN, true);
```
- **Státusz:** ✅ OK

---

## 🎯 Folia-Specifikus Potenciális Problémák

### ❌ **Async Task Használat**
```java
// NEM AJÁNLOTT Foliában:
plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {...});
```
- **Jelenlegi Státusz:** ✅ Nincs async task a kódban
- **Ajánlás:** Megtartani ezt az gyakorlatot

### ⚠️ **Multi-Region Threading**
Ha egy NPC vagy entity más regionban van, és egy szinkron taskban próbálunk módosítani:
```java
// POTENCIÁLIS PROBLÉMA (de kódban nincs):
entity.teleport(newLocation); // Ha entity más regionban van → lehet paliidő
```
- **Jelenlegi Státusz:** ✅ Az entity kezelés lokális (~ 1.1 blokk sugár)
- **Ajánlás:** Megtartani ezt

### ⚠️ **Bukkit.getPlayer() Null Check**
Foliában a játékos offline lehete ha:
- Kilépett
- Timeout-ot kapott
- Más szerver instanciához csatlakozik (ha vannak több Folia instance-ok)
- **Jelenlegi Státusz:** ✅ Az összes hívásnak van null-check
- **Ajánlás:** Megtartani ezt a gyakorlatot

---

## ✅ Folia Migration Checklist

| Ellenőrzés | Státusz | Megjegyzés |
|-----------|---------|-----------|
| `paper-plugin.yml`: `folia-supported: true` | ✅ Hozzáadva | Kritikus! |
| Gradle: `folia-api` függőség | ✅ Beállítva | `dev.folia:folia-api` |
| Async task **nincsen** | ✅ OK | Jó praktika |
| Szinkron task null-checkel | ✅ OK | Összes hívásban van |
| ConcurrentHashMap thread-safety | ✅ OK | Megtörtént |
| Event handler cleaning | ✅ OK | PlayerSessionCleanupListener |
| PDC hívások thread-safe | ✅ OK | Standard Bukkit |

---

## 🚀 Konklúzió

**Az IceSMP projekt FUTTATHATÓ Folia szervereken!**

### Erősségek:
- ✅ Szinkron task design
- ✅ Proper null-checking
- ✅ Concurrent data structures
- ✅ Event-driven architecture

### Figyelmeztetések:
- ⚠️ Szűrt `Bukkit.getPlayer()` hívások – OK, de tartsuk szinkron kontextban
- ⚠️ Entity kezelés – OK, de van regionális korlát
- ⚠️ Scheduler – Nincs async, jó!

### Ajánlások:
1. **Tesztelés:** Kérem, tesztelj legalább 5-10 játékossal egy Folia szerverben
2. **Monitoring:** Figyelj az ezek közül valamelyikre:
   - Teleportálási hibák
   - Null pointer exception entity taskban
   - PDC thread-safety issues
3. **Dokumentáció:** Frissített `AGENTS.md` és `README.md` már Folia-t referencia

---

**Report készítette:** GitHub Copilot  
**Folia verzió:** 1.21.11  
**Paper API verzió:** 1.21.11-R0.1-SNAPSHOT

