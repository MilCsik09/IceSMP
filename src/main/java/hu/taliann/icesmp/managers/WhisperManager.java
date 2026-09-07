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

    private volatile java.util.function.Predicate<UUID> vanished = id -> false;
    private final Map<UUID, Long> witnessGrace = new ConcurrentHashMap<>();
    private final Map<UUID, Long> hintAfter = new ConcurrentHashMap<>();
    private final Map<UUID, Long> leaveConfirmation = new ConcurrentHashMap<>();

    public void setVanishedPredicate(final java.util.function.Predicate<UUID> predicate) {
        vanished = java.util.Objects.requireNonNull(predicate);
    }

    public void respawned(final Player player) {
        witnessGrace.put(player.getUniqueId(), System.currentTimeMillis() + 10_000L);
    }

    public void hint(final Player player) {
        final long now = System.currentTimeMillis();
        if (hintAfter.getOrDefault(player.getUniqueId(), 0L) > now || !canBecomeWhisperer(player)) return;
        hintAfter.put(player.getUniqueId(), now + 60_000L);
        player.sendActionBar(messageManager.getMessage("whisper-rite-hint",
                "<dark_purple>A meghívó lüktet. Hajolj a mélység fölé, és nyújtsd felé a jobb kezed…</dark_purple>"));
        player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.25F, 0.6F);
    }

    public void requestWithdrawal(final Player player) {
        final UUID id = player.getUniqueId();
        if (!isWhisperer(player)) {
            player.sendMessage(messageManager.get("whisper-not-heard", "&8…csak a szél zúg."));
            return;
        }
        final Long deadline = leaveConfirmation.remove(id);
        if (deadline == null || deadline < System.currentTimeMillis()) {
            leaveConfirmation.put(id, System.currentTimeMillis() + 30_000L);
            player.sendMessage(messageManager.get("whisper-leave-confirm",
                    "&5A kapcsolat megszakítása elveszi a titkos előnyöket; 24 óráig nincs új rítus. A bűneid megmaradnak. Csak tiszta állapotban, friss nyom nélkül lehetséges. Megerősítés 30 mp-en belül: &f/suttogas megtagadás"));
            return;
        }
        whisperStore.withdraw(id).whenComplete((result, failure) -> player.getScheduler().run(plugin, task -> {
            if (failure != null) {
                player.sendMessage(messageManager.get("whisper-profile-unavailable", "&cA titkos profil most nem érhető el. Próbáld újra."));
            } else if (result == PlayerProfileWhisperStore.Withdrawal.LEFT) {
                reconcileMembership(player);
                player.sendMessage(messageManager.get("whisper-left", "&7A Suttogás elcsendesült. A civil tagságod és a jogi előzményeid megmaradtak; új rítus 24 óra múlva lehetséges."));
            } else player.sendMessage(messageManager.get("whisper-leave-blocked",
                    "&cA kapcsolat még nem szakítható meg. Fejezd be a rítust, szerezz tiszta állapotot fedezékkel, és várd meg a friss nyomok lejártát. &f/suttogas állapot &7| &f/suttogas megbízás"));
        }, null));
    }

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
        return Math.max(whisperStore.returnRemainingMillis(player.getUniqueId()),
                Math.max(0L, witnessGrace.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis()));
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
        respawned(player);
        recoverRite(player, 0);
        reconcileMembership(player);
    }

    /** Guests and explicit DARK citizens cannot retain the hidden role. */
    public void reconcileMembership(final Player player) {
        if (player == null) return;
        final UUID playerId = player.getUniqueId();
        final FactionType canonicalMembership = factionManager.getChosenFaction(playerId).orElse(null);
        if (canonicalMembership == null || canonicalMembership == FactionType.DARK) {
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

    public record Scene(UUID suspectId, org.bukkit.Location eye, String name,
                        PlayerProfileWhisperStore.Incident incident, boolean identifiable) { }

    /** Capture only on the actor's entity scheduler; witnesses never read foreign actor state. */
    public Scene capture(final Player actor, final PlayerProfileWhisperStore.EvidenceType type) {
        return new Scene(actor.getUniqueId(), actor.getEyeLocation().clone(), actor.getName(),
                new PlayerProfileWhisperStore.Incident(UUID.randomUUID(), type, System.currentTimeMillis()), observable(actor));
    }

    private boolean observable(final Player actor) {
        return !actor.isDead() && actor.getGameMode() != org.bukkit.GameMode.SPECTATOR
                && !actor.isInvisible() && !actor.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)
                && !vanished.test(actor.getUniqueId())
                && witnessGrace.getOrDefault(actor.getUniqueId(), 0L) <= System.currentTimeMillis();
    }

    private java.util.concurrent.CompletableFuture<Boolean> canWitness(final UUID witnessId, final Scene scene, final double radius) {
        final var result = new java.util.concurrent.CompletableFuture<Boolean>();
        final Player witness = Bukkit.getPlayer(witnessId);
        if (witness == null || witnessId.equals(scene.suspectId()) || !scene.identifiable()) {
            result.complete(false); return result;
        }
        witness.getScheduler().run(plugin, task -> {
            final Player subject = Bukkit.getPlayer(scene.suspectId());
            if (System.currentTimeMillis() - scene.incident().observedAt() > 1_000L || !observable(witness)
                    || vanished.test(scene.suspectId()) || subject == null || !witness.canSee(subject)
                    || witness.getWorld() != scene.eye().getWorld()
                    || witness.getEyeLocation().distanceSquared(scene.eye()) > radius * radius) {
                result.complete(false); return;
            }
            hu.taliann.icesmp.factions.WhisperSightline.visible(plugin, witness.getEyeLocation(), scene.eye())
                    .whenComplete((visible, failure) -> { if (failure != null) result.completeExceptionally(failure); else result.complete(Boolean.TRUE.equals(visible)); });
        }, () -> result.complete(false));
        return result.orTimeout(1_500L, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void observe(final UUID witnessId, final Scene scene, final double radius, final String messageKey) {
        canWitness(witnessId, scene, radius).thenAccept(visible -> {
            if (!visible || System.currentTimeMillis() - scene.incident().observedAt() > 1_500L) return;
            final long seconds = Math.max(10L, Math.min(86_400L,
                    configManager.getLong("factions.whisper.witness-seconds", 120L)));
            whisperStore.grantEvidence(witnessId, scene.suspectId(), scene.incident(), seconds * 1_000L)
                    .whenComplete((granted, failure) -> {
                if (failure != null) plugin.getLogger().warning("Whisper evidence save failed: " + rootMessage(failure));
                else if (Boolean.TRUE.equals(granted)) {
                    final Player witness = Bukkit.getPlayer(witnessId);
                    if (witness != null) witness.getScheduler().run(plugin, ignored -> {
                        if (!observable(witness) || vanished.test(scene.suspectId())) return;
                        witness.sendMessage(messageManager.getMessage(messageKey,
                                "<dark_purple>👁 Gyanús tettet láttál. Friss bizonyíték: <white>/suttogas vád {player}</white>.</dark_purple>",
                                Map.of("player", scene.name())));
                    }, null);
                }
            });
        });
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
    public void beginRite(final Player player, final double hpCost, final double radius, final Runnable committed) {
        final UUID id = player.getUniqueId();
        if (!canBecomeWhisperer(player)) return;
        if (!observable(player)) {
            player.sendMessage(messageManager.get("whisper-rite-form",
                    "&7A rítus élő, látható alakot kíván. Belépés vagy újjáéledés után várj 10 másodpercet; vedd le a láthatatlanságot."));
            return;
        }
        if (!ritualsInFlight.add(id)) return;
        final Scene scene = capture(player, PlayerProfileWhisperStore.EvidenceType.RITE);
        final var requestedInventory = hu.taliann.icesmp.storage.ItemMutationJournal.encodeInventory(player.getInventory().getContents());
        final double requestedHealth = player.getHealth();
        final var nearby = player.getNearbyEntities(radius, radius, radius).stream()
                .filter(Player.class::isInstance).limit(65).toList();
        final var checks = nearby.size() > 64
                ? List.of(java.util.concurrent.CompletableFuture.completedFuture(true))
                : nearby.stream().map(entity -> canWitness(entity.getUniqueId(), scene, radius)).toList();
        java.util.concurrent.CompletableFuture.allOf(checks.toArray(java.util.concurrent.CompletableFuture[]::new))
                .whenComplete((ignored, failure) -> player.getScheduler().run(plugin, task -> {
                    if (failure != null || checks.stream().anyMatch(check -> check.getNow(false))) {
                        whisperStore.interruptCandidate(id).whenComplete((saved, saveFailure) -> {
                            ritualsInFlight.remove(id);
                            if (saveFailure != null) { riteFailure(player, saveFailure); return; }
                            player.getScheduler().run(plugin, next -> player.sendMessage(messageManager.get("whisper-rite-witnessed",
                                    "&cSzemek a sötétben — a rítus megszakadt. A meghívód és az életerőd megmaradt. Keress magányt; új próbálkozás 60 mp múlva.")), null);
                        });
                    } else if (System.currentTimeMillis() - scene.incident().observedAt() > 1_500L
                            || !riteConditions(player, scene.eye()) || player.getHealth() != requestedHealth
                            || !requestedInventory.equals(hu.taliann.icesmp.storage.ItemMutationJournal.encodeInventory(player.getInventory().getContents()))) {
                        ritualsInFlight.remove(id);
                        player.sendMessage(messageManager.get("whisper-rite-retry", "&7A rítus megszakadt, mielőtt az áldozat megtörtént. Próbáld újra."));
                    } else prepareRite(player, hpCost, scene.eye(), committed);
                }, () -> ritualsInFlight.remove(id)));
    }

    private boolean riteConditions(final Player player, final org.bukkit.Location origin) {
        if (!observable(player) || player.getWorld() != origin.getWorld()
                || player.getEyeLocation().distanceSquared(origin) > 0.25D
                || player.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL
                || player.getWorld().isDayTime()) return false;
        final var below = player.getLocation().add(0, -0.5, 0);
        if (!Bukkit.isOwnedByCurrentRegion(below)) return false;
        final var type = below.getBlock().getType();
        return type == org.bukkit.Material.SCULK || type == org.bukkit.Material.SCULK_CATALYST;
    }

    private void prepareRite(final Player player, final double hpCost, final org.bukkit.Location origin, final Runnable committed) {
        final UUID id = player.getUniqueId();
        final var inventory = player.getInventory();
        final var before = hu.taliann.icesmp.storage.ItemMutationJournal.encodeInventory(inventory.getContents());
        final var afterItems = hu.taliann.icesmp.storage.ItemMutationJournal.decodeInventory(before);
        final int hand = inventory.getHeldItemSlot();
        if (afterItems[hand] == null || afterItems[hand].getType().isAir() || player.getHealth() <= hpCost + 1) {
            ritualsInFlight.remove(id); return;
        }
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
                if (!riteConditions(player, origin) || !actual.equals(before) || player.getHealth() != rite.healthBefore()) {
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
        hintAfter.remove(playerId);
        leaveConfirmation.remove(playerId);
        witnessGrace.remove(playerId);
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
