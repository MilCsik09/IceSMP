# Folia Developer Guide – IceSMP

Ez a dokumentum Folia-specifikus irányelveket tartalmaz az IceSMP fejlesztéséhez.

---

## 📌 Folia vs Paper – Lényeges Különbségek

| Terület | Paper | Folia | Hatás IceSMP-re |
|--------|-------|-------|-----------------|
| **Scheduler** | Globális | Per-region | Szinkron task OK ✅ |
| **Async Task** | Globális executor | Nem támogatott ❌ | Nem használunk ✅ |
| **Entity Kezelés** | Globális thread-safe | Region-locked | Helyi OK ✅ |
| **Player Access** | Jellemzően OK | Offline lehet region-váltáskor | Null-check szükséges ✅ |
| **Listener Execution** | Szinkron | Region-szinkron | Nincs diff ✅ |
| **World Operations** | Szinkron | Region-szinkron | Helyi OK ✅ |

---

## ✅ Folia-Kompatibilis Kódminták

### ✅ **Helyes: Szinkron Task null-check-kel**

```java
// ✅ JÓ – IceSMP módszer
plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
    final Player player = Bukkit.getPlayer(playerId);
    if (player == null) {
        // Játékos offline vagy más regionban
        return;
    }
    // ... player módosítás szinkron, helyi regionban
}, 20L);
```

### ✅ **Helyes: Local Entity Kezelés**

```java
// ✅ JÓ – IceSMP módszer
final Chicken chicken = player.getWorld().spawn(location, Chicken.class);
chicken.setAI(false); // Szinkron, player regionjában

plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
    if (!chicken.isValid()) { // Ellenőrzés!
        task.cancel();
        return;
    }
    chicken.teleport(chicken.getLocation().add(step)); // Helyi teleportálás
}, 0L, 1L);
```

### ✅ **Helyes: Event Handler-ben Szinkron**

```java
// ✅ JÓ – IceSMP módszer
@EventHandler(priority = EventPriority.MONITOR)
public void onInventoryClick(final InventoryClickEvent event) {
    final Player player = (Player) event.getWhoClicked(); // Garantált létezik
    // Szinkron kezelés – játékos regionjában vagyunk
    currencyManager.refreshPlayerCurrencyItems(player);
}
```

---

## ❌ Folia-Nem-Kompatibilis Minta

### ❌ **TILOS: Async Task**

```java
// ❌ TILOS Foliában
plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
    // Folia ezt nem futtatja!
});
```

**Megoldás:** Ha DB query vagy I/O szükséges:
```java
// ✅ HELYES – Virtual thread vagy CompletableFuture
CompletableFuture.supplyAsync(() -> {
    // I/O műveletek szinkron executor thread-ben
    return database.query(...);
}).thenAccept(result -> {
    // Eredmény feldolgozása
    plugin.getServer().getScheduler().runTask(plugin, () -> {
        // Szinkron kontextus visszakapcsolódás
    });
});
```

### ❌ **PROBLÉMÁS: Entity Kezelés Másik Régióból**

```java
// ❌ PROBLÉMÁS
Entity entity = getNearbyEntitySomewhere(); // Könnyen más regionban lehet
entity.teleport(newLocation); // Potenciális race condition
```

**Megoldás:** Helyi kezelés:
```java
// ✅ HELYES
Entity entity = player.getNearbyEntities(10, 10, 10).stream()
    .findFirst().orElse(null);
if (entity != null && entity.isValid()) {
    entity.teleport(newLocation);
}
```

---

## 🔧 Common Pitfalls & Megoldások

### Problem 1: Játékos Offline Task-ban

```java
// ❌ ROSSZ – Null pointer exception
plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
    Bukkit.getPlayer(playerId).addPotionEffect(...); // NPE!
}, 20L);

// ✅ JÓ
plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
    final Player player = Bukkit.getPlayer(playerId);
    if (player != null) {
        player.addPotionEffect(...);
    }
}, 20L);
```

### Problem 2: Entity Invalid După Task Delay

```java
// ❌ ROSSZ – Entity lehet eltávolítva
plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
    entity.setVelocity(...); // Entity már eltávolított lehet
}, 100L);

// ✅ JÓ
plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
    if (entity.isValid()) {
        entity.setVelocity(...);
    }
}, 100L);
```

### Problem 3: Concurrent Map Locking

```java
// ❌ PROBLEMATIKUS – Manual synchronization
Map<UUID, Data> map = new HashMap<>(); // Nem thread-safe!
map.put(uuid, data);

// ✅ JÓ
Map<UUID, Data> map = new ConcurrentHashMap<>(); // Thread-safe
map.put(uuid, data);
```

---

## 📋 Folia Fejlesztési Checklistje

Új feature hozzáadásakor:

- [ ] **Nincsen async task** – Csak szinkron scheduler
- [ ] **Null-check van** – Player/Entity keresésben  
- [ ] **isValid() check** – Entity kezelés taskban
- [ ] **ConcurrentHashMap** – Statikus player state-nek
- [ ] **Event handler** – PlayerQuit/Kick cleanup-pel
- [ ] **Tesztelés** – Multi-player szimultán access

---

## 🚨 Red Flags - Figyelmeztessen Ha Ezt Látod

```java
// 🚨 PIROS ZÁSZLÓ 1 – Async task
plugin.getServer().getScheduler().runTaskAsynchronously(...)

// 🚨 PIROS ZÁSZLÓ 2 – HashMap külső state-hez
private static final Map<UUID, Data> state = new HashMap<>();

// 🚨 PIROS ZÁSZLÓ 3 – Direct entity access más regionból
Entity entity = Bukkit.selectEntities(...); // Random ent. más régióból

// 🚨 PIROS ZÁSZLÓ 4 – NPE sem kezelés
final Player player = Bukkit.getPlayer(uuid);
player.setHealth(20); // Null-check nincs!

// 🚨 PIROS ZÁSZLÓ 5 – Globális state szinkronizáció nélkül
public static List<PlayerData> allPlayers = new ArrayList<>(); // Nem thread-safe!
```

---

## 💡 Jó Praktikák Folia-ban

### 1. **Idempotent Operations**
```java
// ✅ OK – Ha kétszer futna a task, nem baj
public void givePotionEffect(Player player, PotionEffect effect) {
    if (player != null && !player.hasPotionEffect(effect.getType())) {
        player.addPotionEffect(effect);
    }
}
```

### 2. **Short-Lived Tasks**
```java
// ✅ JÓ – 20-100 tick között
plugin.getServer().getScheduler().runTaskLater(plugin, task, 30L); // 1.5 sec

// ⚠️ KERÜL – Hosszú futásidő
plugin.getServer().getScheduler().runTaskTimer(plugin, task, 0L, 1L); // Folyamatos
```

### 3. **Region-Local Processing**
```java
// ✅ JÓ – Az entity a player regionjában van
for (Entity nearby : player.getNearbyEntities(10, 10, 10)) {
    if (nearby instanceof Monster) {
        nearby.setHealth(0);
    }
}
```

### 4. **Stat Cleanup Ágens Pattern**
```java
// ✅ JÓ – PlayerQuitListener
@EventHandler
public void onPlayerQuit(PlayerQuitEvent event) {
    final UUID id = event.getPlayer().getUniqueId();
    
    // Összes state törlés egy helyen
    spellManager.cleanup(id);
    jobManager.cleanup(id);
    factionManager.cleanup(id);
    // ...
}
```

---

## 🧪 Tesztelés Folia-ban

### Local Test Server
```bash
# Folia szerver indítása
cd C:\Users\csikm\Desktop\IceSMP
.\gradlew.bat runServer
```

### Multi-Player Test Lépések
1. **2+ játékos csatlakozik**
2. **Spell-ek egyszerre casteolnak** – Scheduler contention test
3. **Inventory clickek** – CurrencyItemRefreshListener test
4. **Játékos kilépés** – PlayerSessionCleanupListener test
5. **Entity kezelés** – AngryChickenSpell test (Entity spawn)

### Hibaelvárások
```
❌ NullPointerException – EntityTask-ban
❌ ConcurrentModificationException – HashMap iter-ben
❌ "Bukkit.getScheduler() is not available" – Async code-ban
✅ "folia-supported: true" – plugin.yml-ből
```

---

## 📚 Referenciák

- **Folia Dokumentáció:** https://docs.papermc.io/folia/
- **Scheduler Guide:** https://docs.papermc.io/folia/reference/async-catchers
- **Region Threading:** https://docs.papermc.io/folia/reference/region-threading

---

**Utolsó frissítés:** 2026-04-16  
**Folia verzió:** 1.21.11  
**Paper API verzió:** 1.21.11-R0.1-SNAPSHOT

