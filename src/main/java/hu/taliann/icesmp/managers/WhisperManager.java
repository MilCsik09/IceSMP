package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileWhisperStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** PlayerProfile-backed hidden whisperer role with fixed, evidence-driven exposure stages. */
public final class WhisperManager implements hu.taliann.icesmp.session.PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final SinManager sinManager;
    private final MessageManager messageManager;
    private final PlayerProfileWhisperStore whisperStore = new PlayerProfileWhisperStore();
    private final java.util.Set<UUID> ritualsInFlight = ConcurrentHashMap.newKeySet();
    /** Online routing projection rebuilt from PlayerProfile. */
    private final java.util.Set<UUID> whispererCache = ConcurrentHashMap.newKeySet();

    public WhisperManager(final JavaPlugin plugin, final ConfigManager configManager,
                          final FactionManager factionManager, final SinManager sinManager,
                          final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.sinManager = sinManager;
        this.messageManager = messageManager;
    }

    public boolean isEnabled() {
        return configManager.getBoolean("factions.whisper.enabled", true);
    }

    /** A successful cultist event removes one exposure stage and still shares its loot. */
    public void rewardFaithful(final java.util.Set<UUID> participants) {
        if (!isEnabled()) return;
        for (final Player online : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (!participants.contains(online.getUniqueId()) || !isWhispererCached(online.getUniqueId())) continue;
            whisperStore.applyCover(online.getUniqueId())
                    .whenComplete((result, failure) -> online.getScheduler().run(plugin, task -> {
                        if (failure != null || result == null || !result.state().whisperer()) return;
                        if (result.applied()) {
                            online.sendMessage(messageManager.getMessage("whisper-queen-favor",
                                    "<dark_gray>🕯 A Kapu érzi a hűséged — eggyel halványabb lett körülötted a gyanú.</dark_gray>"));
                        }
                        final int lootRolls = Math.max(0,
                                configManager.getInt("cultists.whisper-loot-rolls", 1));
                        boolean gaveAny = false;
                        for (final org.bukkit.inventory.ItemStack loot
                                : LootTable.roll(configManager, "cultists.rite-loot", lootRolls)) {
                            online.getInventory().addItem(loot).values().forEach(left ->
                                    online.getWorld().dropItemNaturally(online.getLocation(), left));
                            gaveAny = true;
                        }
                        if (gaveAny) {
                            online.sendMessage(messageManager.getMessage("whisper-queen-share",
                                    "<dark_gray>🕯 A hálózat osztozik a zsákmányon — csendben tedd el, ami a tiéd.</dark_gray>"));
                        }
                    }, null));
        }
    }

    public boolean isWhispererCached(final UUID playerId) {
        return whispererCache.contains(playerId)
                && factionManager.isEligibleForFactionBenefits(playerId)
                && !factionManager.isMember(playerId, FactionType.DARK);
    }

    public boolean canHearWhispersCached(final UUID playerId) {
        return isWhispererCached(playerId)
                || darkHears() && factionManager.isMember(playerId, FactionType.DARK);
    }

    public boolean canHearWhispers(final Player player) {
        return player != null && ((isWhisperer(player)
                && factionManager.isEligibleForFactionBenefits(player.getUniqueId())
                && !factionManager.isMember(player.getUniqueId(), FactionType.DARK))
                || darkHears() && factionManager.isMember(player.getUniqueId(), FactionType.DARK));
    }

    private boolean darkHears() {
        return configManager.getBoolean("factions.whisper.dark-hears-channel", true);
    }

    public boolean isWhisperer(final Player player) {
        if (player == null) return false;
        try { return whisperStore.read(player.getUniqueId()).whisperer(); }
        catch (final RuntimeException notReady) { return false; }
    }

    public PlayerProfileWhisperStore.Stage getStage(final Player player) {
        return whisperStore.read(java.util.Objects.requireNonNull(player).getUniqueId()).stage();
    }

    public long returnRemainingMillis(final Player player) {
        return whisperStore.returnRemainingMillis(player.getUniqueId());
    }

    public void makeWhisperer(final Player player) {
        if (player == null || !canBecomeWhisperer(player)) return;
        whisperStore.makeWhisperer(player.getUniqueId())
                .thenAccept(state -> whispererCache.add(player.getUniqueId()))
                .exceptionally(failure -> {
                    plugin.getLogger().severe("PlayerProfile whisperer grant failed for "
                            + player.getUniqueId() + ": " + rootMessage(failure));
                    return null;
                });
    }

    public void handleJoin(final Player player) {
        recoverRite(player, 0);
        reconcileMembership(player);
    }

    /** Guests and explicit DARK citizens cannot retain the hidden role. */
    public void reconcileMembership(final Player player) {
        if (player == null) return;
        final UUID playerId = player.getUniqueId();
        if (!factionManager.isEligibleForFactionBenefits(playerId)
                || factionManager.isMember(playerId, FactionType.DARK)) {
            whispererCache.remove(playerId);
            whisperStore.clear(playerId).exceptionally(failure -> {
                plugin.getLogger().severe("PlayerProfile whisper membership cleanup failed for "
                        + playerId + ": " + rootMessage(failure));
                return null;
            });
            return;
        }
        try {
            if (whisperStore.read(playerId).whisperer()) whispererCache.add(playerId);
            else whispererCache.remove(playerId);
        } catch (final RuntimeException notReady) {
            whispererCache.remove(playerId);
        }
    }

    /** Evidence consumption does not depend on the suspect staying online. */
    public java.util.concurrent.CompletionStage<Boolean> recordAccusation(final UUID witness, final UUID suspect) {
        if (!isEnabled()) return java.util.concurrent.CompletableFuture.completedFuture(false);
        return whisperStore.accuse(witness, suspect).thenApply(result -> {
            if (result.exposed()) whispererCache.remove(suspect);
            final Player online = Bukkit.getPlayer(suspect);
            if (online != null && result.accepted() && (result.state().whisperer() || result.exposed())) {
                online.getScheduler().run(plugin, task -> {
                    online.sendMessage(messageManager.getMessage("whisper-stage-advanced",
                            "<red>A leleplezés közelebb ért: <white>{stage}</white>.</red>",
                            Map.of("stage", result.state().stage().displayName())));
                    if (result.exposed()) applyExposure(online);
                }, null);
            }
            return result.accepted();
        });
    }

    public java.util.concurrent.CompletionStage<Boolean> grantEvidence(final UUID witnessId, final UUID suspectId) {
        final long seconds = Math.max(10L, Math.min(86_400L,
                configManager.getLong("factions.whisper.witness-seconds", 120L)));
        return whisperStore.grantEvidence(witnessId, suspectId, seconds * 1_000L);
    }

    /** Immutable scene snapshot; all witness reads and block access stay on an owning Folia region. */
    public void observe(final UUID witnessId, final UUID suspectId, final org.bukkit.Location scene,
                        final String suspectName, final double radius, final String messageKey) {
        if (witnessId.equals(suspectId)) return;
        final Player witness = Bukkit.getPlayer(witnessId);
        if (witness == null) return;
        final org.bukkit.Location captured = scene.clone();
        final long observedAt = System.currentTimeMillis();
        witness.getScheduler().run(plugin, task -> {
            if (System.currentTimeMillis() - observedAt > 1_000L || witness.isDead()
                    || witness.getGameMode() == org.bukkit.GameMode.SPECTATOR
                    || witness.getWorld() != captured.getWorld()
                    || witness.getEyeLocation().distanceSquared(captured) > radius * radius
                    || !visible(witness.getEyeLocation(), captured)) return;
            grantEvidence(witnessId, suspectId).whenComplete((granted, failure) -> {
                if (failure != null) {
                    plugin.getLogger().warning("Whisper evidence save failed: " + rootMessage(failure));
                } else if (Boolean.TRUE.equals(granted)) {
                    witness.getScheduler().run(plugin, ignored -> witness.sendMessage(messageManager.getMessage(
                            messageKey, "<dark_purple>👁 Gyanús tettet láttál. Friss bizonyíték: <white>/suttogas vád {player}</white>.</dark_purple>",
                            Map.of("player", suspectName))), null);
                }
            });
        }, null);
    }

    private static boolean visible(final org.bukkit.Location eye, final org.bukkit.Location scene) {
        final org.bukkit.util.Vector delta = scene.toVector().subtract(eye.toVector());
        final double length = delta.length();
        if (length < 0.01D) return true;
        final org.bukkit.util.Vector direction = delta.clone().normalize();
        // A ray crossing an unowned/unloaded region is not evidence; never load or read foreign chunks.
        for (double distance = 0; distance <= length + 0.25D; distance += 0.25D) {
            final org.bukkit.Location sample = eye.clone().add(direction.clone().multiply(Math.min(length, distance)));
            if (!Bukkit.isOwnedByCurrentRegion(sample)) return false;
        }
        return eye.getWorld().rayTraceBlocks(eye, direction, length,
                org.bukkit.FluidCollisionMode.NEVER, true) == null;
    }

    public boolean hasEvidence(final UUID witnessId, final UUID suspectId) {
        return whisperStore.hasEvidence(witnessId, suspectId);
    }

    /** Explicit exposure first commits the final stage, then runs owner-thread effects. */
    public void expose(final Player player) {
        if (player == null || !isWhisperer(player)) return;
        whispererCache.remove(player.getUniqueId());
        whisperStore.forceExpose(player.getUniqueId())
                .whenComplete((state, failure) -> player.getScheduler().run(plugin, task -> {
                    if (failure != null) {
                        reconcileMembership(player);
                        plugin.getLogger().severe("PlayerProfile whisper exposure failed for "
                                + player.getUniqueId() + ": " + rootMessage(failure));
                        return;
                    }
                    applyExposure(player);
                }, null));
    }

    private void applyExposure(final Player player) {
        player.getWorld().spawnParticle(Particle.SOUL,
                player.getLocation().add(0.0D, 1.0D, 0.0D),
                60, 0.6D, 1.0D, 0.6D, 0.05D);
        player.getWorld().spawnParticle(Particle.SQUID_INK,
                player.getLocation().add(0.0D, 1.5D, 0.0D),
                30, 0.5D, 0.8D, 0.5D, 0.03D);
        player.getWorld().playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 1.2F, 0.5F);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0F, 0.4F);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.BLINDNESS, 60, 0, false, false, false));
        AdvancementService.award(player, "exiled");
        player.sendMessage(messageManager.get("whisper-exile-result",
                "&5Lelepleződtél és száműzött lettél. Ez nem DARK-tagság és nem eskü. Új Suttogó-rítus legkorábban 24 óra múlva, a száműzetés feloldása után lehetséges."));
        if (configManager.getBoolean("factions.whisper.expose-broadcast", true)) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "whisper-exposed",
                    "<dark_purple>💀 A Suttogás lelepleződött: <white>{player}</white> a Néma Királynő szolgája!</dark_purple>",
                    Map.of("player", player.getName())));
        }
    }

    public void deliverWhisper(final Player sender, final String message) {
        final UUID senderId = sender.getUniqueId();
        whisperStore.channelAlias(senderId).whenComplete((alias, failure) -> {
            if (failure != null) {
                sender.getScheduler().run(plugin, task -> sender.sendMessage(messageManager.get(
                        "whisper-profile-unavailable", "&cA titkos profil most nem érhető el. Próbáld újra.")), null);
                return;
            }
            deliverWhisperLine(senderId, alias, message);
        });
    }

    private void deliverWhisperLine(final UUID senderId, final String alias, final String message) {
        if (!canHearWhispersCached(senderId)) return;
        final net.kyori.adventure.text.Component line = messageManager.getMessage(
                "whisper-chat-line",
                "<dark_purple>✧ Suttogás</dark_purple> <gray>{sender}:</gray> <light_purple>{message}</light_purple>",
                Map.of("sender", alias, "message", message));
        for (final Player online : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (!canHearWhispersCached(online.getUniqueId())
                    && !online.getUniqueId().equals(senderId)) continue;
            online.getScheduler().run(plugin, task -> {
                if (canHearWhispers(online)
                        || online.getUniqueId().equals(senderId)) {
                    online.sendMessage(line);
                    online.playSound(online.getLocation(),
                            Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.4F, 1.6F);
                }
            }, null);
        }
    }

    public boolean canBecomeWhisperer(final Player player) {
        if (player == null) return false;
        try { return !ritualsInFlight.contains(player.getUniqueId()) && whisperStore.canEnter(player.getUniqueId()); }
        catch (final RuntimeException unavailable) { return false; }
    }

    public boolean ritualPending(final UUID playerId) {
        if (ritualsInFlight.contains(playerId)) return true;
        try { return whisperStore.pendingRite(playerId).isPresent(); }
        catch (final RuntimeException unavailable) { return false; }
    }

    /** Prepare intent first, revalidate resources on owner, save resources, then publish the role. */
    public void beginRite(final Player player, final double hpCost, final Runnable committed) {
        final UUID id = player.getUniqueId();
        if (!canBecomeWhisperer(player) || !ritualsInFlight.add(id)) return;
        final var inventory = player.getInventory();
        final var before = hu.taliann.icesmp.storage.ItemMutationJournal.encodeInventory(inventory.getContents());
        final var afterItems = hu.taliann.icesmp.storage.ItemMutationJournal.decodeInventory(before);
        final int hand = inventory.getHeldItemSlot();
        afterItems[hand] = afterItems[hand].clone();
        afterItems[hand].setAmount(afterItems[hand].getAmount() - 1);
        if (afterItems[hand].getAmount() == 0) afterItems[hand] = null;
        final var after = hu.taliann.icesmp.storage.ItemMutationJournal.encodeInventory(afterItems);
        final var rite = new PlayerProfileWhisperStore.Rite(UUID.randomUUID(), before, after,
                player.getHealth(), player.getHealth() - hpCost);
        whisperStore.prepareRite(id, rite).whenComplete((prepared, failure) -> {
            if (failure != null || !Boolean.TRUE.equals(prepared)) {
                ritualsInFlight.remove(id);
                riteFailure(player, failure);
                return;
            }
            player.getScheduler().run(plugin, task -> {
                final var actual = hu.taliann.icesmp.storage.ItemMutationJournal.encodeInventory(inventory.getContents());
                if (player.isDead() || !actual.equals(before) || player.getHealth() != rite.healthBefore()) {
                    finishRite(player, rite, false, null);
                    return;
                }
                try {
                    inventory.setContents(afterItems);
                    player.setHealth(rite.healthAfter());
                    player.saveData();
                    finishRite(player, rite, true, committed);
                } catch (final RuntimeException resourceFailure) {
                    // Retain the durable intent; never blindly refund or grant after an ambiguous save.
                    ritualsInFlight.remove(id);
                    riteFailure(player, resourceFailure);
                }
            }, () -> ritualsInFlight.remove(id));
        });
    }

    private void finishRite(final Player player, final PlayerProfileWhisperStore.Rite rite,
                            final boolean grant, final Runnable committed) {
        final UUID id = player.getUniqueId();
        whisperStore.finishRite(id, rite.operation(), grant).whenComplete((finished, failure) -> {
            ritualsInFlight.remove(id);
            if (failure != null || !Boolean.TRUE.equals(finished)) {
                riteFailure(player, failure);
                return;
            }
            player.getScheduler().run(plugin, task -> {
                reconcileMembership(player);
                if (grant) {
                    AdvancementService.award(player, "whisperer");
                    if (committed != null) committed.run();
                    else player.sendMessage(messageManager.get("whisper-rite-recovered",
                            "&5A félbemaradt rítus mentését helyreállítottuk. A Suttogás befogadott."));
                } else player.sendMessage(messageManager.get("whisper-rite-retry",
                        "&7A rítus megszakadt, mielőtt az áldozat megtörtént. Próbáld újra."));
            }, null);
        });
    }

    private void recoverRite(final Player player, final int attempt) {
        player.getScheduler().runDelayed(plugin, task -> {
            try {
                final var pending = whisperStore.pendingRite(player.getUniqueId());
                if (pending.isEmpty()) { reconcileMembership(player); return; }
                final var rite = pending.orElseThrow();
                final var actual = hu.taliann.icesmp.storage.ItemMutationJournal.encodeInventory(player.getInventory().getContents());
                if (actual.equals(rite.before())) finishRite(player, rite, false, null);
                else if (actual.equals(rite.after())) finishRite(player, rite, true, null);
                else riteFailure(player, new IllegalStateException("rite inventory differs from both saved snapshots; admin review required"));
            } catch (final hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority.ProfileNotReadyException unavailable) {
                if (attempt < 12) recoverRite(player, attempt + 1);
                else riteFailure(player, unavailable);
            } catch (final RuntimeException failure) { riteFailure(player, failure); }
        }, null, attempt == 0 ? 1L : 10L);
    }

    private void riteFailure(final Player player, final Throwable failure) {
        plugin.getLogger().warning("Whisper rite incomplete for " + player.getUniqueId() + ": " + rootMessage(failure));
        player.getScheduler().run(plugin, task -> player.sendMessage(messageManager.get("whisper-rite-save-failed",
                "&cA rítus nem zárult le. A tartós bizonylat megmaradt; új belépéskor helyreállítjuk. Ha továbbra is zárolt, kérj admin segítséget.")), null);
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        ritualsInFlight.remove(playerId);
        whispererCache.remove(playerId);
    }

    private static String rootMessage(final Throwable failure) {
        if (failure == null) return "unknown failure";
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
