#!/usr/bin/env python3
"""One-shot source patch used by the evidence-based PR #76 review."""

from __future__ import annotations

from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file_path = Path(path)
    text = file_path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one replacement, found {count}: {old[:100]!r}")
    file_path.write_text(text.replace(old, new), encoding="utf-8")


def insert_before(path: str, marker: str, addition: str) -> None:
    replace_once(path, marker, addition + marker)


def patch_treasury() -> None:
    path = "src/main/java/hu/taliann/icesmp/managers/FactionTreasuryManager.java"
    replace_once(
        path,
        """                        balances.put(faction,\n                                Math.max(0.0D, section.getDouble(factionKey, 0.0D)));\n""",
        """                        balances.put(faction, readStoredNonNegative(\n                                section, factionKey, \"treasury.\" + factionKey));\n""",
    )
    replace_once(
        path,
        """                            taxRates.put(faction, Math.max(0.0D,\n                                    rateSection.getDouble(factionKey, 0.0D)));\n""",
        """                            taxRates.put(faction, readStoredNonNegative(\n                                    rateSection, factionKey, \"tax-rates.\" + factionKey));\n""",
    )
    insert_before(
        path,
        "    private double readDebtAmount(final ConfigurationSection section, final String key,\n",
        """    private double readStoredNonNegative(final ConfigurationSection section,\n                                                   final String key, final String path) {\n        final Object raw = section.get(key);\n        if (!(raw instanceof Number number)) {\n            plugin.getLogger().warning(\n                    \"Invalid non-numeric persisted amount at \" + path + \"; value disabled.\");\n            return 0.0D;\n        }\n        final double value = number.doubleValue();\n        if (!Double.isFinite(value) || value < 0.0D) {\n            plugin.getLogger().warning(\n                    \"Invalid non-finite/negative persisted amount at \" + path\n                            + \"; value disabled without clamping.\");\n            return 0.0D;\n        }\n        return value;\n    }\n\n""",
    )
    replace_once(
        path,
        """    /** Gets a faction's effective citizen-tax rate. */\n    public double getTaxRate(final FactionType faction) {\n        synchronized (stateLock) {\n            final double configDefault = Math.max(0.0D,\n                    configManager.getDouble(\"factions.tax.rate-percent\", 2.0D));\n            return faction == null\n                    ? configDefault : taxRates.getOrDefault(faction, configDefault);\n        }\n    }\n\n    /** Sets a faction's citizen-tax rate. */\n    public double setTaxRate(final FactionType faction, final double ratePercent) {\n        final double max = Math.max(0.0D,\n                configManager.getDouble(\"factions.tax.max-rate-percent\", 10.0D));\n        final double safeRate = Double.isFinite(ratePercent) ? ratePercent : 0.0D;\n        final double applied = Math.max(0.0D, Math.min(max, safeRate));\n        if (faction != null) {\n            synchronized (stateLock) {\n                taxRates.put(faction, applied);\n            }\n            requestSave();\n        }\n        return applied;\n    }\n""",
        """    private double nonNegativeFiniteConfig(final String path, final double defaultValue) {\n        final Object raw = configManager.contains(path) && configManager.getConfiguration() != null\n                ? configManager.getConfiguration().get(path) : defaultValue;\n        if (raw instanceof Number number) {\n            final double value = number.doubleValue();\n            if (Double.isFinite(value) && value >= 0.0D) {\n                return value;\n            }\n        }\n        plugin.getLogger().warning(\"Config: invalid '\" + path + \"' value (\" + raw\n                + \”); expected a finite non-negative number — this branch is disabled at 0.0, \”\n                + \”not silently clamped.\”);\n        return 0.0D;\n    }\n\n    private int nonNegativeIntConfig(final String path, final int defaultValue) {\n        final Object raw = configManager.contains(path) && configManager.getConfiguration() != null\n                ? configManager.getConfiguration().get(path) : defaultValue;\n        if (raw instanceof Number number) {\n            final double value = number.doubleValue();\n            if (Double.isFinite(value) && value >= 0.0D && value <= Integer.MAX_VALUE\n                    && value == Math.rint(value)) {\n                return (int) value;\n            }\n        }\n        plugin.getLogger().warning(\"Config: invalid '\" + path + \"' value (\" + raw\n                + \”); expected a non-negative integer — this branch is disabled at 0, \”\n                + \”not silently clamped.\”);\n        return 0;\n    }\n\n    /** Gets a faction's effective citizen-tax rate. */\n    public double getTaxRate(final FactionType faction) {\n        synchronized (stateLock) {\n            final double configDefault = nonNegativeFiniteConfig(\n                    \"factions.tax.rate-percent\", 2.0D);\n            final double stored = faction == null\n                    ? configDefault : taxRates.getOrDefault(faction, configDefault);\n            if (Double.isFinite(stored) && stored >= 0.0D) {\n                return stored;\n            }\n            plugin.getLogger().warning(\"Invalid persisted tax rate for \" + faction\n                    + \"; using the validated config default.\");\n            return configDefault;\n        }\n    }\n\n    /** Sets a faction's citizen-tax rate. The configured maximum is explicit, not hidden. */\n    public double setTaxRate(final FactionType faction, final double ratePercent) {\n        if (faction == null || !Double.isFinite(ratePercent) || ratePercent < 0.0D) {\n            plugin.getLogger().warning(\"Rejected invalid faction tax rate: \" + ratePercent);\n            return faction == null ? 0.0D : getTaxRate(faction);\n        }\n        final double max = nonNegativeFiniteConfig(\n                \"factions.tax.max-rate-percent\", 10.0D);\n        final double applied = Math.min(max, ratePercent);\n        if (ratePercent > max) {\n            plugin.getLogger().warning(\"Faction tax rate \" + ratePercent\n                    + \" exceeds configured factions.tax.max-rate-percent=\" + max\n                    + \"; applying the documented maximum.\");\n        }\n        synchronized (stateLock) {\n            taxRates.put(faction, applied);\n        }\n        requestSave();\n        return applied;\n    }\n""".replace("”", '"'),
    )
    replace_once(
        path,
        """     * original currency and credited back to its original treasury. Unassigned guests are absent\n     * from the assignment snapshot, so they receive neither a new assessment nor debt collection.\n""",
        """     * original currency and credited back to its original treasury. Unassigned guests receive no\n     * new assessment, but a previously assessed origin-aware debt remains collectible after reset.\n""",
    )
    replace_once(
        path,
        """        final double minimum = Math.max(0.0D,\n                configManager.getDouble(\"factions.tax.minimum-amount\", 2.0D));\n        final double maxArrears = Math.max(0.0D,\n                configManager.getDouble(\"factions.tax.max-arrears\", 50.0D));\n        final int evasionThreshold = Math.max(0,\n                configManager.getInt(\"factions.tax.evasion-strikes\", 3));\n""",
        """        final double minimum = nonNegativeFiniteConfig(\n                \"factions.tax.minimum-amount\", 2.0D);\n        final double maxArrears = nonNegativeFiniteConfig(\n                \"factions.tax.max-arrears\", 50.0D);\n        final int evasionThreshold = nonNegativeIntConfig(\n                \"factions.tax.evasion-strikes\", 3);\n""",
    )
    replace_once(
        path,
        """        for (final Map.Entry<UUID, FactionType> entry\n                : factionManager.getFactionAssignments().entrySet()) {\n            final FactionType currentFaction = entry.getValue();\n            if (currentFaction == null) {\n                continue;\n            }\n\n            final UUID citizenId = entry.getKey();\n            final CurrencyType currentCurrency = CurrencyType.fromFactionType(currentFaction);\n            final double newAssessment;\n            if (exempt.contains(currentFaction.name())) {\n                newAssessment = 0.0D;\n            } else {\n                final double ratePercent = getTaxRate(currentFaction);\n                final double balance = currencyManager.getBalance(citizenId, currentCurrency);\n""",
        """        final Map<UUID, FactionType> assignments = factionManager.getFactionAssignments();\n        final Set<UUID> participants = new HashSet<>(assignments.keySet());\n        synchronized (stateLock) {\n            participants.addAll(taxDebts.playerIdsWithDebt());\n        }\n        for (final UUID citizenId : participants) {\n            final FactionType currentFaction = assignments.get(citizenId);\n            final double newAssessment;\n            if (currentFaction == null || exempt.contains(currentFaction.name())) {\n                newAssessment = 0.0D;\n            } else {\n                final CurrencyType currentCurrency = CurrencyType.fromFactionType(currentFaction);\n                final double ratePercent = getTaxRate(currentFaction);\n                final double balance = currencyManager.getBalance(citizenId, currentCurrency);\n""",
    )
    replace_once(
        path,
        """                if (taxDebts.bindUnresolvedLegacy(citizenId, currentFaction)) {\n""",
        """                if (currentFaction != null\n                        && taxDebts.bindUnresolvedLegacy(citizenId, currentFaction)) {\n""",
    )
    replace_once(
        path,
        """                final double assessed = originFaction == currentFaction ? newAssessment : 0.0D;\n""",
        """                final double assessed = currentFaction != null && originFaction == currentFaction\n                        ? newAssessment : 0.0D;\n""",
    )


def patch_membership_manager() -> None:
    path = "src/main/java/hu/taliann/icesmp/managers/FactionManager.java"
    replace_once(
        path,
        "import hu.taliann.icesmp.factions.FactionMembership;\n",
        "import hu.taliann.icesmp.factions.FactionMembership;\n"
        "import hu.taliann.icesmp.factions.FactionMembershipMutation;\n",
    )
    replace_once(
        path,
        "    private final File storageFile;\n",
        "    private final File storageFile;\n"
        "    /** Assignment/history mutation and full-snapshot persistence boundary. */\n"
        "    private final Object stateLock = new Object();\n",
    )
    replace_once(
        path,
        """    public void save() {\n        try {\n            final YamlConfiguration yaml = new YamlConfiguration();\n\n            for (final Map.Entry<UUID, FactionType> entry : playerFactions.entrySet()) {\n                yaml.set(entry.getKey().toString(), entry.getValue().name());\n            }\n            for (final Map.Entry<UUID, FactionType> entry : lastChosenFactions.entrySet()) {\n                yaml.set(HISTORY_SECTION + \".\" + entry.getKey(), entry.getValue().name());\n            }\n\n            YamlStore.saveAtomic(storageFile, yaml);\n            plugin.getLogger().info(\"Saved \" + playerFactions.size() + \" faction assignments.\");\n        } catch (final IOException e) {\n            plugin.getLogger().severe(\"Failed to save factions: \" + e.getMessage());\n            throw new java.io.UncheckedIOException(\"Failed to save factions\", e);\n        }\n    }\n""",
        """    public void save() {\n        synchronized (stateLock) {\n            writeStateLocked();\n        }\n    }\n\n    /** The caller must hold stateLock. */\n    private void writeStateLocked() {\n        try {\n            final YamlConfiguration yaml = new YamlConfiguration();\n\n            for (final Map.Entry<UUID, FactionType> entry : playerFactions.entrySet()) {\n                yaml.set(entry.getKey().toString(), entry.getValue().name());\n            }\n            for (final Map.Entry<UUID, FactionType> entry : lastChosenFactions.entrySet()) {\n                yaml.set(HISTORY_SECTION + \".\" + entry.getKey(), entry.getValue().name());\n            }\n\n            YamlStore.saveAtomic(storageFile, yaml);\n            plugin.getLogger().info(\"Saved \" + playerFactions.size() + \" faction assignments.\");\n        } catch (final IOException e) {\n            plugin.getLogger().severe(\"Failed to save factions: \" + e.getMessage());\n            throw new java.io.UncheckedIOException(\"Failed to save factions\", e);\n        }\n    }\n""",
    )
    replace_once(
        path,
        """    public Map<UUID, FactionType> getFactionAssignments() {\n        return Map.copyOf(playerFactions);\n    }\n""",
        """    public Map<UUID, FactionType> getFactionAssignments() {\n        synchronized (stateLock) {\n            return Map.copyOf(playerFactions);\n        }\n    }\n""",
    )
    replace_once(
        path,
        """    public void setFaction(final UUID uuid, final FactionType factionType) {\n        final UUID playerId = Objects.requireNonNull(uuid, \"player UUID\");\n        final FactionType target = Objects.requireNonNull(factionType, \"chosen faction\");\n        final FactionType previous = playerFactions.put(playerId, target);\n        final boolean changed = previous != target;\n        lastChosenFactions.put(playerId, target);\n        if (changed) {\n            membershipChangeHook.accept(playerId);\n        }\n        save();\n        final hu.taliann.icesmp.managers.GuildManager guildRef = guildManager;\n""",
        """    public void setFaction(final UUID uuid, final FactionType factionType) {\n        final UUID playerId = Objects.requireNonNull(uuid, \"player UUID\");\n        final FactionType target = Objects.requireNonNull(factionType, \"chosen faction\");\n        final boolean changed;\n        synchronized (stateLock) {\n            final FactionMembershipMutation.Snapshot previousState =\n                    FactionMembershipMutation.capture(\n                            playerFactions, lastChosenFactions, playerId);\n            changed = previousState.assignment() != target;\n            FactionMembershipMutation.assign(\n                    playerFactions, lastChosenFactions, playerId, target);\n            try {\n                writeStateLocked();\n            } catch (final RuntimeException | Error persistenceFailure) {\n                FactionMembershipMutation.restore(\n                        playerFactions, lastChosenFactions, previousState);\n                throw persistenceFailure;\n            }\n        }\n        if (changed) {\n            membershipChangeHook.accept(playerId);\n        }\n        final hu.taliann.icesmp.managers.GuildManager guildRef = guildManager;\n""",
    )
    replace_once(
        path,
        """    public void removeFaction(final UUID uuid) {\n        if (uuid == null) {\n            return;\n        }\n\n        playerFactions.remove(uuid);\n        membershipChangeHook.accept(uuid);\n        save();\n        final hu.taliann.icesmp.managers.GuildManager guildRef = guildManager;\n        if (guildRef != null) {\n            guildRef.reconcileFaction(uuid, null);\n        }\n    }\n""",
        """    public void removeFaction(final UUID uuid) {\n        if (uuid == null) {\n            return;\n        }\n\n        synchronized (stateLock) {\n            final FactionMembershipMutation.Snapshot previousState =\n                    FactionMembershipMutation.capture(\n                            playerFactions, lastChosenFactions, uuid);\n            if (!previousState.hadAssignment()) {\n                return;\n            }\n            FactionMembershipMutation.removeAssignment(playerFactions, uuid);\n            try {\n                writeStateLocked();\n            } catch (final RuntimeException | Error persistenceFailure) {\n                FactionMembershipMutation.restore(\n                        playerFactions, lastChosenFactions, previousState);\n                throw persistenceFailure;\n            }\n        }\n        membershipChangeHook.accept(uuid);\n        final hu.taliann.icesmp.managers.GuildManager guildRef = guildManager;\n        if (guildRef != null) {\n            guildRef.reconcileFaction(uuid, null);\n        }\n    }\n""",
    )


def patch_listener() -> None:
    path = "src/main/java/hu/taliann/icesmp/listeners/FactionPassiveListener.java"
    replace_once(
        path,
        "        final UUID playerId = damagingPlayerId(event.getDamager());\n",
        "        final UUID playerId = damagingPlayerId(event);\n",
    )
    replace_once(
        path,
        """    private static UUID damagingPlayerId(final Entity damager) {\n        if (damager instanceof Player player) {\n            return player.getUniqueId();\n        }\n        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {\n            return shooter.getUniqueId();\n        }\n        return null;\n    }\n""",
        """    private static UUID damagingPlayerId(final EntityDamageByEntityEvent event) {\n        final UUID direct = owningPlayerId(event.getDamager());\n        return direct != null\n                ? direct : owningPlayerId(event.getDamageSource().getCausingEntity());\n    }\n\n    private static UUID owningPlayerId(final Object source) {\n        if (source instanceof Player player) {\n            return player.getUniqueId();\n        }\n        if (source instanceof Projectile projectile) {\n            return owningPlayerId(projectile.getShooter());\n        }\n        if (source instanceof org.bukkit.entity.Tameable tameable\n                && tameable.getOwner() != null) {\n            return tameable.getOwner().getUniqueId();\n        }\n        return null;\n    }\n""",
    )


def patch_regressions() -> None:
    passive = "src/regression/java/hu/taliann/icesmp/factions/FactionPassiveRegressionSuite.java"
    replace_once(
        passive,
        "import java.util.EnumSet;\n",
        "import java.util.EnumSet;\nimport java.util.HashMap;\nimport java.util.Map;\n",
    )
    replace_once(
        passive,
        "        membershipLifecycleFailsClosed();\n",
        "        membershipLifecycleFailsClosed();\n        membershipPersistenceRollbackIsAtomic();\n",
    )
    insert_before(
        passive,
        "    private static void redProvenanceAndCombustDurationRemainIndependent() {\n",
        """    private static void membershipPersistenceRollbackIsAtomic() {\n        final UUID playerId = UUID.randomUUID();\n        final Map<UUID, FactionType> assignments = new HashMap<>();\n        final Map<UUID, FactionType> history = new HashMap<>();\n        assignments.put(playerId, FactionType.RED);\n        history.put(playerId, FactionType.RED);\n\n        final FactionMembershipMutation.Snapshot beforeSwitch =\n                FactionMembershipMutation.capture(assignments, history, playerId);\n        FactionMembershipMutation.assign(\n                assignments, history, playerId, FactionType.BLUE);\n        FactionMembershipMutation.restore(assignments, history, beforeSwitch);\n        check(assignments.get(playerId) == FactionType.RED\n                        && history.get(playerId) == FactionType.RED,\n                \"failed membership save did not roll back assignment and history\");\n\n        final FactionMembershipMutation.Snapshot beforeReset =\n                FactionMembershipMutation.capture(assignments, history, playerId);\n        FactionMembershipMutation.removeAssignment(assignments, playerId);\n        FactionMembershipMutation.restore(assignments, history, beforeReset);\n        check(assignments.get(playerId) == FactionType.RED\n                        && history.get(playerId) == FactionType.RED,\n                \"failed admin reset did not restore durable citizenship\");\n\n        try {\n            FactionMembershipMutation.assign(\n                    assignments, history, playerId, FactionType.DARK);\n            throw new IllegalStateException(\"simulated persistence failure\");\n        } catch (final IllegalStateException expected) {\n            FactionMembershipMutation.restore(assignments, history, beforeSwitch);\n        }\n        check(assignments.get(playerId) == FactionType.RED,\n                \"simulated save failure left the candidate assignment published\");\n    }\n\n""",
    )
    replace_once(
        passive,
        """        check(listener.contains(\"contentContexts(mob, liveSettings, playerId)\")\n                        && listener.contains(\"canAlertDarkUndead(\"),\n                \"queued alert ignores live membership/config/content exclusions\");\n""",
        """        check(listener.contains(\"contentContexts(mob, liveSettings, playerId)\")\n                        && listener.contains(\"canAlertDarkUndead(\"),\n                \"queued alert ignores live membership/config/content exclusions\");\n        check(listener.contains(\"owningPlayerId(event.getDamageSource().getCausingEntity())\")\n                        && listener.contains(\"instanceof org.bukkit.entity.Tameable\"),\n                \"indirect or tame-owner provocation is not attributed to the player\");\n""",
    )
    replace_once(
        passive,
        """        final String core = read(\"src/main/java/hu/taliann/icesmp/core/IceSMPCore.java\");\n""",
        """        final String membershipManager = read(\n                \"src/main/java/hu/taliann/icesmp/managers/FactionManager.java\");\n        check(membershipManager.contains(\"FactionMembershipMutation.restore(\")\n                        && membershipManager.contains(\"writeStateLocked();\")\n                        && membershipManager.indexOf(\"writeStateLocked();\")\n                        < membershipManager.indexOf(\"membershipChangeHook.accept(playerId)\"),\n                \"membership hook can publish a state whose durable save failed\");\n\n        final String core = read(\"src/main/java/hu/taliann/icesmp/core/IceSMPCore.java\");\n""",
    )

    tax = "src/regression/java/hu/taliann/icesmp/factions/FactionTaxDebtRegressionSuite.java"
    replace_once(
        tax,
        "        debtAndStrikesStayWithTheirOriginAfterSwitch();\n",
        "        debtAndStrikesStayWithTheirOriginAfterSwitch();\n"
        "        debtorsRemainCollectibleAfterAssignmentReset();\n",
    )
    insert_before(
        tax,
        "    private static void unresolvedLegacyGuestNeverBecomesImplicitNeutral() {\n",
        """    private static void debtorsRemainCollectibleAfterAssignmentReset() {\n        final FactionTaxDebtLedger ledger = new FactionTaxDebtLedger();\n        final UUID knownDebtor = UUID.randomUUID();\n        final UUID unresolvedLegacy = UUID.randomUUID();\n        ledger.put(knownDebtor, FactionType.RED, 7.5D, 0);\n        ledger.putUnresolvedLegacy(unresolvedLegacy, 4.0D, 1);\n\n        check(ledger.playerIdsWithDebt().contains(knownDebtor),\n                \"known-origin debt disappeared when current assignment was reset\");\n        check(ledger.playerIdsWithDebt().contains(unresolvedLegacy),\n                \"unresolved legacy debt disappeared from durable collection participants\");\n        expectFailure(() -> ledger.playerIdsWithDebt().add(UUID.randomUUID()),\n                UnsupportedOperationException.class,\n                \"debtor snapshot must be immutable\");\n    }\n\n""",
    )
    replace_once(
        tax,
        """        check(source.contains(\"factionManager.getFactionAssignments().entrySet()\")\n                        && !source.contains(\"factionManager.getFaction(citizenId)\"),\n                \"guest tax collection regained an implicit NEUTRAL fallback\");\n""",
        """        check(source.contains(\"participants.addAll(taxDebts.playerIdsWithDebt())\")\n                        && source.contains(\"final FactionType currentFaction = assignments.get(citizenId)\")\n                        && source.contains(\"currentFaction == null || exempt.contains\")\n                        && !source.contains(\"factionManager.getFaction(citizenId)\"),\n                \"assignment reset hides known debt or creates a new guest assessment\");\n""",
    )


def main() -> None:
    patch_treasury()
    patch_membership_manager()
    patch_listener()
    patch_regressions()
    print("review autofix patch applied")


if __name__ == "__main__":
    main()
