#!/usr/bin/env python3
from __future__ import annotations

import pathlib
import subprocess
import textwrap

ROOT = pathlib.Path.cwd()
PR32 = "1459df4443a656b9a2906d54004e04e11a8ac433"
FILES_FROM_PR32 = [
    "src/main/java/hu/taliann/icesmp/managers/DevItemManager.java",
    "src/main/java/hu/taliann/icesmp/managers/DevItemStateData.java",
    "scripts/test_dev_item_state.py",
    "src/regression/java/hu/taliann/icesmp/managers/DevItemStateDataRegressionTest.java",
]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def replace_between(text: str, start_marker: str, end_marker: str, replacement: str, label: str) -> str:
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"{label}: missing start marker")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"{label}: missing end marker")
    return text[:start] + replacement + text[end:]


def main() -> None:
    subprocess.run(["git", "checkout", PR32, "--", *FILES_FROM_PR32], cwd=ROOT, check=True)

    manager_path = ROOT / FILES_FROM_PR32[0]
    manager = manager_path.read_text(encoding="utf-8")

    manager = replace_once(
        manager,
        "    private record PendingReward(String rarity, String entry) {\n    }\n",
        "    private record PendingReward(String rarity, String entry) {\n    }\n\n"
        "    /** In-memory rollback snapshot; it is not another durable protocol layer. */\n"
        "    private record PendingStateSnapshot(\n"
        "            long progressMillis,\n"
        "            String rarity,\n"
        "            String entry,\n"
        "            ItemStack item,\n"
        "            int sinceRare,\n"
        "            int sinceEpic,\n"
        "            int sinceLegendary\n"
        "    ) {\n"
        "    }\n",
        "pending snapshot record",
    )

    tick_pre_delivery = """        PendingReward pending = pendingReward();
        ItemStack reward;
        if (pending == null) {
            pending = rollPendingReward();
            if (pending == null) {
                if (rewardConfigWarningSent.compareAndSet(false, true)) {
                    plugin.getLogger().warning("Csodálatos Bingulus: nincs kisorsolható, érvényes jutalom a konfigurációban.");
                }
                return;
            }
            rewardConfigWarningSent.set(false);
            reward = resolveReward(owner, pending.entry());
            if (reward == null || reward.getType().isAir()) {
                plugin.getLogger().warning("Csodálatos Bingulus: nem építhető jutalom: " + pending.entry());
                return;
            }
            // A single pending snapshot is the recovery source of truth. It is written before the
            // inventory can observe the item, so a normal restart can retry the exact same roll.
            pending = preparePendingRewardDurably(pending, reward);
        } else {
            synchronized (stateLock) {
                final ItemStack exactPending = pendingItem.get();
                if (exactPending == null || exactPending.getType().isAir()
                        || exactPending.getAmount() <= 0) {
                    throw new IllegalStateException("A pending DEV-item jutalom metaadata és ItemStack állapota eltér.");
                }
                reward = exactPending.clone();
            }
        }

"""
    manager = replace_between(
        manager,
        "        PendingReward pending = pendingReward();\n",
        "        if (!canFit(owner.getInventory(), reward)) {\n",
        tick_pre_delivery,
        "tick prepare phase",
    )

    delivery_and_helper = """        final ItemStack[] inventoryBefore = cloneStorageContents(owner.getInventory());
        final Map<Integer, ItemStack> leftovers = owner.getInventory().addItem(reward.clone());
        if (!leftovers.isEmpty()) {
            // Capacity validation and mutation run on the same entity scheduler. Restore the complete
            // pre-delivery inventory if the API still reports a remainder.
            owner.getInventory().setStorageContents(inventoryBefore);
            if (rewardInventoryNoticeSent.compareAndSet(false, true)) {
                owner.sendMessage(messageManager.get("dev-item.inventory-full",
                        "&eA Csodálatos Bingulus jutalma várakozik. Szabadíts fel egy helyet az inventorydban!"));
            }
            return;
        }

        try {
            if (!completePendingRewardDurably(pending)) {
                owner.getInventory().setStorageContents(inventoryBefore);
                return;
            }
        } catch (final RuntimeException | Error failure) {
            owner.getInventory().setStorageContents(inventoryBefore);
            throw failure;
        }
        rewardInventoryNoticeSent.set(false);
        announce(owner, pending.rarity(), reward);
    }

    private ItemStack[] cloneStorageContents(final PlayerInventory inventory) {
        final ItemStack[] contents = inventory.getStorageContents();
        final ItemStack[] clone = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            clone[slot] = contents[slot] == null ? null : contents[slot].clone();
        }
        return clone;
    }

"""
    manager = replace_between(
        manager,
        "        final Map<Integer, ItemStack> leftovers = owner.getInventory().addItem(reward);\n",
        "    private boolean ensureAuthoritativeItem",
        delivery_and_helper,
        "tick completion phase",
    )

    pending_helpers = """    private PendingReward pendingReward() {
        synchronized (stateLock) {
            return pendingRewardLocked();
        }
    }

    private PendingReward pendingRewardLocked() {
        final String rarity = pendingRarity.get();
        final String entry = pendingEntry.get();
        final ItemStack item = pendingItem.get();
        final boolean hasRarity = !rarity.isBlank();
        final boolean hasEntry = !entry.isBlank();
        final boolean hasItem = item != null && !item.getType().isAir() && item.getAmount() > 0;
        if (!hasRarity && !hasEntry && item == null) {
            return null;
        }
        if (hasRarity != hasEntry || hasRarity != hasItem) {
            throw new IllegalStateException("A pending DEV-item jutalom állapota részleges vagy sérült.");
        }
        return new PendingReward(rarity, entry);
    }

    private void clearPendingRewardLocked() {
        pendingRarity.set("");
        pendingEntry.set("");
        pendingItem.set(null);
    }

    private PendingStateSnapshot capturePendingStateLocked() {
        final ItemStack item = pendingItem.get();
        return new PendingStateSnapshot(
                progressMillis.get(), pendingRarity.get(), pendingEntry.get(),
                item == null ? null : item.clone(),
                sinceRare.get(), sinceEpic.get(), sinceLegendary.get());
    }

    private void restorePendingStateLocked(final PendingStateSnapshot snapshot) {
        progressMillis.set(snapshot.progressMillis());
        pendingRarity.set(snapshot.rarity());
        pendingEntry.set(snapshot.entry());
        pendingItem.set(snapshot.item() == null ? null : snapshot.item().clone());
        sinceRare.set(snapshot.sinceRare());
        sinceEpic.set(snapshot.sinceEpic());
        sinceLegendary.set(snapshot.sinceLegendary());
    }

    private PendingReward preparePendingRewardDurably(final PendingReward selection,
                                                        final ItemStack exactReward) {
        requireHealthyState();
        synchronized (this) {
            synchronized (stateLock) {
                if (pendingRewardLocked() != null) {
                    throw new IllegalStateException("A pending DEV-item jutalom megváltozott a sorsolás közben.");
                }
                pendingRarity.set(selection.rarity());
                pendingEntry.set(selection.entry());
                pendingItem.set(exactReward.clone());
            }
            try {
                final YamlConfiguration snapshot;
                synchronized (stateLock) {
                    snapshot = snapshotYamlLocked();
                }
                writeSnapshot(snapshot);
                return selection;
            } catch (final RuntimeException | Error failure) {
                synchronized (stateLock) {
                    clearPendingRewardLocked();
                }
                throw failure;
            }
        }
    }

    private boolean completePendingRewardDurably(final PendingReward expected) {
        requireHealthyState();
        synchronized (this) {
            final PendingStateSnapshot before;
            synchronized (stateLock) {
                final PendingReward current = pendingRewardLocked();
                if (current == null) {
                    return false;
                }
                if (!current.equals(expected)) {
                    throw new IllegalStateException("A pending DEV-item jutalom megváltozott a nyugtázás előtt.");
                }
                before = capturePendingStateLocked();
                progressMillis.set(0L);
                clearPendingRewardLocked();
                updatePityAfterLocked(current.rarity());
            }
            try {
                final YamlConfiguration snapshot;
                synchronized (stateLock) {
                    snapshot = snapshotYamlLocked();
                }
                writeSnapshot(snapshot);
                return true;
            } catch (final RuntimeException | Error failure) {
                synchronized (stateLock) {
                    restorePendingStateLocked(before);
                }
                throw failure;
            }
        }
    }

"""
    manager = replace_between(
        manager,
        "    private PendingReward pendingReward() {\n",
        "    private PendingReward rollPendingReward() {\n",
        pending_helpers,
        "pending helper block",
    )

    forbidden = [
        "rewardReceiptKey", "pendingGrantId", "pendingRecipient", "DeliveryDecision",
        "persistPlayer(", "saveData()", "reassignPendingRecipient",
        "bingulus.pending.grant-id", "bingulus.pending.recipient",
    ]
    present = [token for token in forbidden if token in manager]
    if present:
        raise SystemExit("Receipt protocol remnants remain: " + ", ".join(present))
    manager_path.write_text(manager, encoding="utf-8")

    launcher = textwrap.dedent('''\
        #!/usr/bin/env python3
        """Compile and run the dependency-free DEV-item state regression suite."""

        from __future__ import annotations

        import pathlib
        import shutil
        import subprocess
        import tempfile

        ROOT = pathlib.Path(__file__).resolve().parents[1]
        SOURCES = [
            ROOT / "src/main/java/hu/taliann/icesmp/managers/DevItemStateData.java",
            ROOT / "src/regression/java/hu/taliann/icesmp/managers/DevItemStateDataRegressionTest.java",
        ]
        MAIN_CLASS = "hu.taliann.icesmp.managers.DevItemStateDataRegressionTest"


        def require_tool(name: str) -> str:
            executable = shutil.which(name)
            if executable is None:
                raise SystemExit(f"Required Java 21 tool is unavailable: {name}")
            return executable


        def main() -> None:
            javac = require_tool("javac")
            java = require_tool("java")
            missing = [str(path) for path in SOURCES if not path.is_file()]
            if missing:
                raise SystemExit("Missing regression source(s): " + ", ".join(missing))

            with tempfile.TemporaryDirectory(prefix="icesmp-dev-item-regression-") as directory:
                output = pathlib.Path(directory)
                subprocess.run(
                    [javac, "--release", "21", "-d", str(output), *(str(path) for path in SOURCES)],
                    cwd=ROOT,
                    check=True,
                )
                subprocess.run([java, "-cp", str(output), MAIN_CLASS], cwd=ROOT, check=True)


        if __name__ == "__main__":
            main()
        ''')
    (ROOT / FILES_FROM_PR32[2]).write_text(launcher, encoding="utf-8")

    regression = textwrap.dedent('''\
        package hu.taliann.icesmp.managers;

        import java.util.UUID;

        /** Dependency-free regression coverage for the practical DEV-item persistence invariants. */
        public final class DevItemStateDataRegressionTest {

            private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
            private static final UUID NEW_OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
            private static final UUID INSTANCE = UUID.fromString("33333333-3333-3333-3333-333333333333");

            private DevItemStateDataRegressionTest() {
            }

            public static void main(final String[] args) {
                transferPreservesSingletonProgressAndPendingReward();
                markIssuedPreservesSingletonIdentity();
                malformedUuidIsRejected();
                negativeProgressAndPityAreRejected();
                partialPendingRewardIsRejected();
                unissuedStateCannotCarryEarnedProgress();
                emptyPendingStateIsValid();
                System.out.println("DevItemStateData regression tests passed.");
            }

            private static void transferPreservesSingletonProgressAndPendingReward() {
                final DevItemStateData transferred = pendingState().transferTo(NEW_OWNER);
                check(transferred.owner().equals(NEW_OWNER), "the configured owner must change");
                check(transferred.instanceId().equals(INSTANCE), "owner transfer must preserve the singleton token");
                check(transferred.issued(), "owner transfer must preserve issuance");
                check(transferred.progressMillis() == 42_000L, "owner transfer must preserve active time");
                check(transferred.pendingRarity().equals("epikus"), "owner transfer must preserve pending rarity");
                check(transferred.pendingEntry().equals("material:diamond"), "owner transfer must preserve pending entry");
                check(transferred.pendingItemPresent(), "owner transfer must preserve the exact pending item marker");
                check(transferred.rollsSinceRare() == 7, "owner transfer must preserve rare pity");
                check(transferred.rollsSinceEpic() == 11, "owner transfer must preserve epic pity");
                check(transferred.rollsSinceLegendary() == 13, "owner transfer must preserve legendary pity");
            }

            private static void markIssuedPreservesSingletonIdentity() {
                final DevItemStateData unissued = new DevItemStateData(
                        OWNER, INSTANCE, false, 0L, "", "", false, 0, 0, 0);
                final DevItemStateData issued = unissued.markIssued();
                check(issued.issued(), "markIssued must set the durable issuance bit");
                check(issued.owner().equals(OWNER), "markIssued must preserve the owner");
                check(issued.instanceId().equals(INSTANCE), "markIssued must not mint a replacement singleton");
            }

            private static void malformedUuidIsRejected() {
                expectThrows(IllegalArgumentException.class, () -> DevItemStateData.requireUuid("", "owner"));
                expectThrows(IllegalArgumentException.class, () -> DevItemStateData.requireUuid("not-a-uuid", "instance"));
                check(DevItemStateData.requireUuid("  " + OWNER + "  ", "owner").equals(OWNER),
                        "UUID parsing may trim surrounding operator whitespace");
            }

            private static void negativeProgressAndPityAreRejected() {
                expectThrows(IllegalArgumentException.class,
                        () -> new DevItemStateData(OWNER, INSTANCE, true, -1L, "", "", false, 0, 0, 0));
                expectThrows(IllegalArgumentException.class,
                        () -> new DevItemStateData(OWNER, INSTANCE, true, 0L, "", "", false, -1, 0, 0));
                expectThrows(IllegalArgumentException.class,
                        () -> new DevItemStateData(OWNER, INSTANCE, true, 0L, "", "", false, 0, -1, 0));
                expectThrows(IllegalArgumentException.class,
                        () -> new DevItemStateData(OWNER, INSTANCE, true, 0L, "", "", false, 0, 0, -1));
            }

            private static void partialPendingRewardIsRejected() {
                expectThrows(IllegalArgumentException.class,
                        () -> new DevItemStateData(OWNER, INSTANCE, true, 1L, "ritka", "", false, 0, 0, 0));
                expectThrows(IllegalArgumentException.class,
                        () -> new DevItemStateData(OWNER, INSTANCE, true, 1L,
                                "ritka", "material:diamond", false, 0, 0, 0));
                expectThrows(IllegalArgumentException.class,
                        () -> new DevItemStateData(OWNER, INSTANCE, true, 1L, "", "", true, 0, 0, 0));
            }

            private static void unissuedStateCannotCarryEarnedProgress() {
                expectThrows(IllegalArgumentException.class,
                        () -> new DevItemStateData(OWNER, INSTANCE, false, 1L, "", "", false, 0, 0, 0));
                expectThrows(IllegalArgumentException.class,
                        () -> new DevItemStateData(OWNER, INSTANCE, false, 0L,
                                "ritka", "material:diamond", true, 0, 0, 0));
                expectThrows(IllegalArgumentException.class,
                        () -> new DevItemStateData(OWNER, INSTANCE, false, 0L, "", "", false, 1, 0, 0));
            }

            private static void emptyPendingStateIsValid() {
                final DevItemStateData state = new DevItemStateData(
                        OWNER, INSTANCE, true, 0L, "", "", false, 0, 0, 0);
                check(!state.hasPendingReward(), "an empty pending snapshot must not create a reward");
            }

            private static DevItemStateData pendingState() {
                return new DevItemStateData(
                        OWNER, INSTANCE, true, 42_000L,
                        "epikus", "material:diamond", true, 7, 11, 13);
            }

            private static <T extends Throwable> T expectThrows(final Class<T> type, final ThrowingRunnable action) {
                try {
                    action.run();
                } catch (final Throwable thrown) {
                    if (type.isInstance(thrown)) {
                        return type.cast(thrown);
                    }
                    throw new AssertionError("Expected " + type.getName() + " but got " + thrown, thrown);
                }
                throw new AssertionError("Expected " + type.getName() + " to be thrown");
            }

            private static void check(final boolean condition, final String message) {
                if (!condition) {
                    throw new AssertionError(message);
                }
            }

            @FunctionalInterface
            private interface ThrowingRunnable {
                void run() throws Throwable;
            }
        }
        ''')
    (ROOT / FILES_FROM_PR32[3]).write_text(regression, encoding="utf-8")

    architecture_path = ROOT / "docs/ARCHITECTURE.md"
    architecture = architecture_path.read_text(encoding="utf-8")
    marker = "\n### 3.4 Parancsok — két stílus\n"
    dev_note = textwrap.dedent('''
        - **DEV-item jutalom — arányos pending/retry modell:** a pontosan kisorsolt tárgy a
          `dev-items-state.yml` egyetlen pending rekordjába kerül, mielőtt az inventoryhoz érnénk.
          Teljes inventory vagy normál restart esetén ugyanaz a jutalom újrapróbálható; sikeres átadáskor
          a pending rekord szinkron mentéssel törlődik. Nincs külön grant-UUID, player-PDC nyugta vagy
          saját playerdata-commit protokoll. Emiatt nem állítunk formális exactly-once garanciát az
          inventory-módosítás és a pending törlése közötti erőszakos process-kill ablakra.

        ''')
    if dev_note.strip() not in architecture:
        architecture = replace_once(architecture, marker, "\n" + dev_note + marker.lstrip("\n"), "architecture insertion")
    architecture_path.write_text(architecture, encoding="utf-8")

    audit_path = ROOT / "docs/audits/IceSMP_audit_update_b6db9d2.md"
    audit_path.parent.mkdir(parents=True, exist_ok=True)
    audit = textwrap.dedent('''\
        # IceSMP audit update — b6db9d2

        > **Auditált master:** `b6db9d21d12a2944b67925a5fe9228b4e76b9b04`  
        > **Dátum:** 2026-07-27  
        > **Hatókör:** a #26, #31, #32, #33, #34 és #35 merge-ek arányossági/overengineering auditja,
        > valamint a DEV-item pending reward célzott egyszerűsítése.  
        > **Nem állítás:** a korábbi teljes mélyaudit minden findingje nem lett újraellenőrizve.

        ## Termékdefiníció

        Az IceSMP elsődlegesen lore-központú fantasy kingdom SMP: kaszt- és karakterépítés, frakciók,
        politika, questek, szakmák, relikviák, raidek, kazamaták, világesemények, felfedezés és közösségi
        szezonok együtt adják az élményt. A gazdaság és a perzisztencia ezeket védi; nem önálló banki vagy
        általános tranzakciós termék.

        ## Merge- és rendszerbesorolás

        | Rendszer / merge | Besorolás | Valós hiba és arányossági döntés |
        |---|---|---|
        | #26 persistent-store wiring | `KEEP_AS_IS` | A kihagyott store nem töltődik/mentődik. Kis wiring-javítás, közvetlen progresszióvédelem. |
        | #31 `PersistentStoreCoordinator` | `SAFETY_CRITICAL_KEEP` | Corrupt/partial load után ne induljon írható részállapot; autosave és shutdown ne fusson össze. A state machine kicsi és érthető. |
        | Globális kritikus write gate | `SIMPLIFY` | Íráshiba után fail-closed kell, de egy DEV-item hiba ne fagyasszon le automatikusan minden más kritikus feature-t. Per-feature health gate javasolt külön scope-ban. |
        | #32 DEV-item durable singleton state | `SAFETY_CRITICAL_KEEP` | Megőrzi az egyedi item identitását, tulajdonosát, idejét, pity állapotát és pontos pending jutalmát. |
        | #33 DEV grant-ID + recipient + player-PDC receipt | `SIMPLIFIED` | Valós célja a dupe csökkentése volt, de egy ritka DEV-jutalomhoz túl sok tartós tanú és recovery ág került. Egyetlen pending snapshot + retry váltotta. |
        | #34 season/community generation marker | `KEEP_AS_IS` | Egy mezős marker megakadályozza, hogy új szezonban régi community progressz éljen tovább. Kis komplexitás, valós szezon-invariáns. |
        | #35 treasury és monument grant receipt | `SAFETY_CRITICAL_KEEP` | Tartós kasszajutalom és a Korszakok Könyve sora normál replay során ne duplikálódjon. Lokális, feature-specifikus receipt. |
        | #35 member reward PDC receipt/saveData protocol | `SIMPLIFY` | A pending tagjutalom és full-inventory retry kell; a playerdata receipt, effekt/inventory rollback és UUID-halmaz külön scope-ban egyszerűsítendő. |
        | Season announcement/story pending flag | `REMOVE_REDUNDANT_LAYER` | Chat és narratív kiírás kozmetikai/best-effort; ne blokkolja a tartós jutalmat, és ne kapjon recovery state-et. |
        | `TransactionJournal` + globális currency gate | `NEEDS_RUNTIME_VALIDATION` / `SIMPLIFY` | A market valódi item/pénzvesztést véd, de a globális gate és abszolút balance-repair túl széles. Külön market scope szükséges. |
        | `BlockRegenJournal` | `NEEDS_RUNTIME_VALIDATION` | A konténer snapshot-before-clear fontos. Az APPLYING/APPLIED modell valós Folia restart teszt nélkül nem nevezhető pontosan egyszerinek. |
        | Forrásszöveg-sorrendet vizsgáló regressziók | `REPLACE_WITH_EXISTING_PROJECT_PATTERN` | Törékeny implementációteszt helyett Bukkit-független állapot-invariáns teszt fut. |

        ## Ebben a branchben egyszerűsített DEV-item modell

        Megtartott invariánsok:

        - az autoritatív singleton instance és owner tartós;
        - az aktív idő és pity számlálók restart után megmaradnak;
        - a pontosan kisorsolt ItemStack a live inventory előtt tartós pending rekordba kerül;
        - teljes inventory esetén a jutalom pending marad;
        - normál restart/replay ugyanazt a pending jutalmat próbálja újra;
        - sikeres átadás után a pending törlése szinkron mentés;
        - write hiba esetén az élő inventory-módosítás visszaáll.

        Eltávolított rétegek és állapotok:

        - `pending.grant-id`;
        - `pending.recipient`;
        - `dev_reward_receipt` játékos-PDC;
        - explicit `Player.saveData()` commit;
        - `DELIVER / ACKNOWLEDGE / WAIT_FOR_RECORDED_RECIPIENT` döntési ágak;
        - címzett-átruházási recovery;
        - forrásszöveg-metódussorrendet ellenőrző Python-teszt.

        Feladott elméleti garancia:

        - nincs formális exactly-once bizonyítás erőszakos process-killre az inventory item hozzáadása és
          a pending YAML törlésének befejezése közötti szűk ablakban;
        - kézzel sérült playerdata és minden lehetséges storage kombinációja nem kap külön állapotgépet.

        Ez egy ritka DEV-item időalapú jutalma. A gyakorlati szerverüzemhez a tartós pending, a teljes
        inventory retry és a normál restart recovery arányos védelmet ad lényegesen kisebb mentális és
        tartós állapotkomplexitással.

        ## Metrikák

        | Mérőszám | Master | Branch |
        |---|---:|---:|
        | `DevItemManager.java` sor | `{{BASE_MANAGER_LINES}}` | `{{BRANCH_MANAGER_LINES}}` |
        | `DevItemStateData.java` sor | `{{BASE_STATE_LINES}}` | `{{BRANCH_STATE_LINES}}` |
        | DEV tartós pending mezők | 5 | 3 |
        | Player-PDC receipt kulcs | 1 | 0 |
        | Delivery decision ág | 3 | 0 |
        | Tartós transition helper | 4 | 2 |
        | Bukkit-független regressziós teszteset | 10 | 7 |
        | Érintett Java-fájl | — | 3 |
        | Hozzáadott / törölt sor összesen | — | `{{ADDED_LINES}} / {{DELETED_LINES}}` |

        ## Futtatott ellenőrzések

        Baseline master:

        - `./gradlew clean build --no-daemon --stacktrace` — `{{BASE_BUILD}}`
        - `python3 scripts/check_consistency.py` — `{{BASE_CONSISTENCY}}`
        - `python3 scripts/test_dev_item_state.py` — `{{BASE_DEV_TEST}}`

        Branch:

        - `./gradlew clean build --no-daemon --stacktrace` — `{{BRANCH_BUILD}}`
        - `python3 scripts/check_consistency.py` — `{{BRANCH_CONSISTENCY}}`
        - `python3 scripts/test_dev_item_state.py` — `{{BRANCH_DEV_TEST}}`

        ## Finding-státuszok

        - `SIMPLIFIED`: DEV-item pending reward receipt/outbox réteg.
        - `SAFETY_CRITICAL_KEEP`: strict store load, coordinator, DEV singleton state, season generation marker,
          treasury/monument idempotens grant.
        - `OVERENGINEERED_SIMPLIFICATION_NEEDED`: season member PDC receipt és kozmetikai batch flag-ek;
          globális kritikus write gate; market currency gate/journal recovery.
        - `NEEDS_RUNTIME_VALIDATION`: BlockRegenJournal konténer-replay, TransactionJournal market recovery,
          season playerdata delivery és valódi Folia ownership útvonalak.
        - `NOT_REVALIDATED`: a történeti audit minden más findingje.

        ## Aktuális gameplay-prioritások

        1. `TransientEntities` és world-event lifecycle valódi Folia régióhatár-tesztje.
        2. Party XP és Wild Hunt personal loot távoli játékos-hozzáférése.
        3. Rendszeresen beragadó world boss/escort/quest/dungeon utak runtime felderítése.
        4. Season member reward egyszerűsítése külön branchben.
        5. Market journal/gate külön, szűk gazdasági scope-ban.

        ## Nem futtatott ellenőrzések

        - valódi Folia 1.21.11 több-régiós szerver;
        - process-kill/fault-injection az inventory és YAML írás közti pontokon;
        - ENOSPC, permission-denied és fizikailag sérült playerdata;
        - teljes production plugin-stackkel integrációs teszt.
        ''')
    audit_path.write_text(audit, encoding="utf-8")
    print("DEV-item simplification applied.")


if __name__ == "__main__":
    main()
