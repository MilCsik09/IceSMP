package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Companion pets for the Beast Master and Necromancer. The pet can be ANY mob,
 * obtained with a spec-specific capture item:
 * the Beast Master tames any non-hostile animal, the Necromancer binds any hostile
 * mob / undead. The pet's type, level, XP and name persist in the player's PDC and
 * re-apply on summon. Tameable pets follow via vanilla; the rest are kept near the
 * owner by a teleport-follow tick. Levels come from the owner's nearby kills.
 */
public final class PetManager implements hu.taliann.icesmp.session.PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MinionManager minionManager;
    private final SpecializationManager specializationManager;
    private final MessageManager messageManager;
    private final NamespacedKey levelKey;
    private final NamespacedKey xpKey;
    private final NamespacedKey nameKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey entityKey;
    private final NamespacedKey healthModKey;
    private final NamespacedKey damageModKey;
    private final NamespacedKey respawnKey;
    private final NamespacedKey stanceKey;
    private final NamespacedKey armorKey;
    private final NamespacedKey armorDefenseModKey;
    private final NamespacedKey armorHealthModKey;
    /** Élő társsal rendelkező gazdák — a vezérlő tick CSAK rájuk hop-ol (üresjárat-fék). */
    private final java.util.Set<UUID> activeOwners = ConcurrentHashMap.newKeySet();
    /** owner UUID → current combat target UUID (assist / defend). */
    private final Map<UUID, UUID> combatTargets = new ConcurrentHashMap<>();
    /** pet UUID → epoch ms when the pet may attack again. */
    private final Map<UUID, Long> attackReady = new ConcurrentHashMap<>();

    public PetManager(final JavaPlugin plugin, final ConfigManager configManager, final MinionManager minionManager,
                      final SpecializationManager specializationManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.minionManager = minionManager;
        this.specializationManager = specializationManager;
        this.messageManager = messageManager;
        this.levelKey = new NamespacedKey(plugin, "pet_level");
        this.xpKey = new NamespacedKey(plugin, "pet_xp");
        this.nameKey = new NamespacedKey(plugin, "pet_name");
        this.typeKey = new NamespacedKey(plugin, "pet_type");
        this.entityKey = new NamespacedKey(plugin, "pet_entity");
        this.healthModKey = new NamespacedKey(plugin, "pet_health_mod");
        this.damageModKey = new NamespacedKey(plugin, "pet_damage_mod");
        this.respawnKey = new NamespacedKey(plugin, "pet_respawn_at");
        this.stanceKey = new NamespacedKey(plugin, "pet_stance");
        this.armorKey = new NamespacedKey(plugin, "pet_armor");
        this.armorDefenseModKey = new NamespacedKey(plugin, "pet_armor_defense_mod");
        this.armorHealthModKey = new NamespacedKey(plugin, "pet_armor_health_mod");
    }

    public boolean isBeastMaster(final Player player) {
        return specializationManager.getClassSpecialization(player) == SpecializationType.BEAST_MASTER;
    }

    public boolean isNecromancer(final Player player) {
        return specializationManager.getClassSpecialization(player) == SpecializationType.NECROMANCER;
    }

    /** Setter-injektált (a JobManager a PetManager után is elérhető a core-ból). */
    private volatile hu.taliann.icesmp.managers.JobManager jobManager;

    private volatile hu.taliann.icesmp.managers.TalentManager talentManagerRef;

    public void setTalentManager(final hu.taliann.icesmp.managers.TalentManager talentManager) {
        this.talentManagerRef = talentManager;
    }

    public void setJobManager(final hu.taliann.icesmp.managers.JobManager jobManager) {
        this.jobManager = jobManager;
    }

    /** Szentségtelen DK: állandó ghúl-társ (a WoW-hű permanens pet). */
    public boolean isUnholy(final Player player) {
        return specializationManager.getClassSpecialization(player) == SpecializationType.UNHOLY;
    }

    /** Boszorkánymester (kaszt-szintű): állandó démon-familiáris. */
    public boolean isWarlock(final Player player) {
        final hu.taliann.icesmp.managers.JobManager jobs = this.jobManager;
        return jobs != null && jobs.getPrimaryJob(player) == hu.taliann.icesmp.data.JobType.WARLOCK;
    }

    /** A Sötét Paktum-tekercset használó szerepek közös kapuja. */
    public boolean isDarkCapturer(final Player player) {
        return isNecromancer(player) || isUnholy(player) || isWarlock(player);
    }

    public boolean canOwnPet(final Player player) {
        return isBeastMaster(player) || isDarkCapturer(player);
    }

    public int getLevel(final Player player) {
        return Math.max(1, player.getPersistentDataContainer().getOrDefault(levelKey, PersistentDataType.INTEGER, 1));
    }

    public int getXp(final Player player) {
        return player.getPersistentDataContainer().getOrDefault(xpKey, PersistentDataType.INTEGER, 0);
    }

    public String getName(final Player player) {
        return player.getPersistentDataContainer().getOrDefault(nameKey, PersistentDataType.STRING, "Társ");
    }

    /** Whether the player may capture the target with their spec's capture item. */
    public boolean isValidTarget(final Player player, final Entity target) {
        if (!(target instanceof Mob) || target instanceof Player || minionManager.isMinion(target)) {
            return false;
        }
        // Más játékos vanília úton szelídített állata nem lopható el befogással.
        if (target instanceof org.bukkit.entity.Tameable tameable && tameable.isTamed()
                && !(tameable.getOwner() instanceof Player owner && owner.getUniqueId().equals(player.getUniqueId()))) {
            return false;
        }
        // Erő-tiltólista: a meta-törő "legjobb pet" választások (Warden, Ravager,
        // Vasgólem, Elder Guardian, Wither) egyik szerepnek sem foghatók be.
        for (final String banned : configManager.getStringList("pets.capture.blocklist")) {
            if (target.getType().name().equalsIgnoreCase(banned)) {
                return false;
            }
        }
        if (isBeastMaster(player)) {
            return !(target instanceof Monster); // any non-hostile animal/mob
        }
        if (isNecromancer(player)) {
            return target instanceof Monster; // any hostile mob / undead
        }
        // A Szentségtelen és a Boszorkánymester NEM befog, hanem IDÉZ (rituálé-kellékkel).
        return false;
    }

    /** Idézett társ jelölése — a rituálé-út erő-prémiumot ad (nehezebb beszerzés). */
    private NamespacedKey summonedKeyLazy;

    private NamespacedKey summonedKey() {
        if (summonedKeyLazy == null) {
            summonedKeyLazy = new NamespacedKey(plugin, "pet_summoned");
        }
        return summonedKeyLazy;
    }

    public boolean isSummonedPet(final Player player) {
        return player.getPersistentDataContainer()
                .getOrDefault(summonedKey(), PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    /**
     * Rituálé-idézés (Szentségtelen ghúl / Boszorkánymester démon): csak éjjel, a
     * forma a pet-szinttel fejlődik — a magasabb forma új rituálét (új kelléket) kér.
     *
     * @return null siker, különben üzenet-kulcs
     */
    public String ritualSummon(final Player player) {
        final boolean unholy = isUnholy(player);
        final boolean warlock = !unholy && isWarlock(player);
        if (!unholy && !warlock) {
            return "pet-wrong-spec";
        }
        final long time = player.getWorld().getTime();
        if (configManager.getBoolean("pets.summon.night-only", true) && (time < 13000L || time > 23000L)) {
            return "pet-ritual-night-only";
        }
        final int level = getLevel(player);
        final EntityType form;
        final String formName;
        if (unholy) {
            if (level >= configManager.getInt("pets.summon.tier3-level", 25)) {
                form = EntityType.ZOGLIN; formName = "Förtelem";
            } else if (level >= configManager.getInt("pets.summon.tier2-level", 15)) {
                form = EntityType.WITHER_SKELETON; formName = "Csontszolga";
            } else {
                form = EntityType.HUSK; formName = "Ghúl";
            }
        } else {
            if (level >= configManager.getInt("pets.summon.tier3-level", 25)) {
                form = EntityType.MAGMA_CUBE; formName = "Magma-behemót";
            } else if (level >= configManager.getInt("pets.summon.tier2-level", 15)) {
                form = EntityType.BLAZE; formName = "Tűz-démon";
            } else {
                form = EntityType.VEX; formName = "Imp";
            }
        }
        final var pdc = player.getPersistentDataContainer();
        pdc.set(typeKey, PersistentDataType.STRING, form.name());
        pdc.set(summonedKey(), PersistentDataType.BYTE, (byte) 1);
        if ("Társ".equals(getName(player))) {
            pdc.set(nameKey, PersistentDataType.STRING, formName);
        }
        return summon(player);
    }

    /**
     * Captures the clicked mob as the player's companion (replacing any existing one).
     *
     * @return null on success, otherwise a message key
     */
    public String capture(final Player player, final Entity target) {
        if (!canOwnPet(player)) {
            return "pet-not-allowed";
        }
        player.getPersistentDataContainer().remove(summonedKey());
        if (!isValidTarget(player, target) || !(target instanceof Mob mob)) {
            return "pet-invalid-target";
        }

        removeActive(player);
        player.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, mob.getType().name());
        adopt(mob, player);
        return null;
    }

    /**
     * (Re)summons the companion of the player's stored type — or a sensible default
     * (wolf for Beast Master, zombie for Necromancer) if nothing was captured yet.
     *
     * @return null on success, otherwise a message key
     */
    public String summon(final Player player) {
        if (!canOwnPet(player)) {
            return "pet-not-allowed";
        }
        final long respawnAt = player.getPersistentDataContainer()
                .getOrDefault(respawnKey, PersistentDataType.LONG, 0L);
        if (respawnAt > System.currentTimeMillis()) {
            return "pet-respawn-cooldown";
        }

        final EntityType type = resolveType(player);
        if (type == null || type.getEntityClass() == null || !Mob.class.isAssignableFrom(type.getEntityClass())) {
            return "pet-none-captured";
        }

        removeActive(player);
        final Mob mob = (Mob) player.getWorld().spawn(player.getLocation(), type.getEntityClass().asSubclass(Mob.class));
        adopt(mob, player);
        return null;
    }

    public boolean dismiss(final Player player) {
        activeOwners.remove(player.getUniqueId());
        final boolean removed = removeActive(player);
        player.getPersistentDataContainer().remove(entityKey);
        return removed;
    }

    /**
     * Handles a pet's death: clears the combat state for that pet, and if it was the
     * owner's active companion, clears the stored reference and notifies them. The
     * owner-side PDC/message runs on the owner's region thread (Folia-safe); call this
     * from an EntityDeathEvent for minion-tagged mobs.
     */
    public void handlePetDeath(final LivingEntity dead) {
        final UUID ownerId = minionManager.getOwner(dead);
        if (ownerId == null) {
            return;
        }
        final UUID deadId = dead.getUniqueId();
        // A halott entitás kulcsán álló állapot mindig törölhető.
        attackReady.remove(deadId);

        final Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null) {
            return; // offline: the stale entityKey resolves to a dead UUID harmlessly on next summon
        }
        // A GAZDA kulcsán álló állapotot (aktív társ, harci cél) CSAK azonosság-ellenőrzés UTÁN
        // szabad bontani: a permanens társ és a rövid életű spell-minion ugyanazt a tulajdonos-
        // jelölést viseli, ezért egy eldobható minion halála leállította volna az ÉLŐ társ
        // vezérlését. Az ellenőrzés a gazda PDC-jét olvassa, tehát a gazda saját szálán fut.
        owner.getScheduler().run(plugin, task -> {
            final String raw = owner.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
            if (deadId.toString().equals(raw)) {
                activeOwners.remove(ownerId);
                combatTargets.remove(ownerId);
                owner.getPersistentDataContainer().remove(entityKey);
                // A halálnak tétje van: újraidézés csak cooldown után.
                final long cd = Math.max(0L, configManager.getLong(
                        "pets.companion.death-respawn-seconds", 120L)) * 1000L;
                if (cd > 0L) {
                    owner.getPersistentDataContainer().set(respawnKey, PersistentDataType.LONG,
                            System.currentTimeMillis() + cd);
                }
                owner.sendMessage(messageManager.getMessage(
                        "pet-died",
                        "<gray>A társad elesett a harcban. <dark_gray>(/pet summon az új idézéshez)</dark_gray></gray>"));
            }
        }, null);
    }

    public boolean setName(final Player player, final String name) {
        if (name == null || name.isBlank() || name.length() > 24) {
            return false;
        }
        player.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, name);
        final Mob pet = activePet(player);
        if (pet != null) {
            updateName(pet, player);
        }
        return true;
    }

    /**
     * A TÁRS állásmódja a GAZDA PDC-jében él (nem a pet entitásén): így a GUI és a
     * parancs a játékos saját régió-szálán olvassa/írja, a vezérlő tick pedig a
     * tickOwner gazda-oldali snapshotjával viszi át a pet szálára — nincs
     * régió-átnyúló entitás-PDC hozzáférés. (A spell-idézett minionok állásmódja
     * továbbra is a saját entitás-PDC-jükben van.)
     */
    public MinionManager.Stance getStance(final Player player) {
        final String raw = player.getPersistentDataContainer().get(stanceKey, PersistentDataType.STRING);
        if (raw == null) {
            return MinionManager.Stance.ACTIVE;
        }
        try {
            return MinionManager.Stance.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            return MinionManager.Stance.ACTIVE;
        }
    }

    public void setStance(final Player player, final MinionManager.Stance stance) {
        player.getPersistentDataContainer().set(stanceKey, PersistentDataType.STRING, stance.name());
    }

    public MinionManager.Stance cycleStance(final Player player) {
        final MinionManager.Stance next = switch (getStance(player)) {
            case ACTIVE -> MinionManager.Stance.PASSIVE;
            case PASSIVE -> MinionManager.Stance.STAY;
            case STAY -> MinionManager.Stance.ACTIVE;
        };
        setStance(player, next);
        return next;
    }

    /** A kattintott entitás a játékos aktív társa-e (gazda-PDC alapján, a gazda szálán hívandó). */
    public boolean isActivePetEntity(final Player player, final Entity clicked) {
        final Mob pet = activePet(player);
        return pet != null && pet.getUniqueId().equals(clicked.getUniqueId());
    }

    public boolean hasActivePet(final Player player) {
        return activePet(player) != null;
    }

    public int nextLevelCost(final Player player) {
        return levelCost(getLevel(player));
    }

    public long respawnRemainingSeconds(final Player player) {
        final long at = player.getPersistentDataContainer().getOrDefault(respawnKey, PersistentDataType.LONG, 0L);
        return Math.max(0L, (at - System.currentTimeMillis() + 999L) / 1000L);
    }

    /** Társvért: a jelzés a gazda PDC-jében él, így újraidézéskor is visszakerül a társra. */
    public boolean hasPetArmor(final Player player) {
        return player.getPersistentDataContainer().getOrDefault(armorKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    /**
     * Felszereli a Társvértet a játékos aktív társára.
     *
     * @return null siker, különben üzenet-kulcs
     */
    public String equipArmor(final Player player, final Entity clicked) {
        if (!canOwnPet(player)) {
            return "pet-not-allowed";
        }
        if (hasPetArmor(player)) {
            return "pet-armor-already";
        }
        final Mob pet = activePet(player);
        if (pet == null || !pet.getUniqueId().equals(clicked.getUniqueId())) {
            return "pet-armor-not-pet";
        }
        player.getPersistentDataContainer().set(armorKey, PersistentDataType.BYTE, (byte) 1);
        applyEquipment(pet);
        return null;
    }

    private void applyEquipment(final LivingEntity pet) {
        applyModifier(pet, Attribute.ARMOR, armorDefenseModKey,
                Math.max(0.0D, configManager.getDouble("pets.equipment.armor-bonus", 4.0D)));
        applyModifier(pet, Attribute.MAX_HEALTH, armorHealthModKey,
                Math.max(0.0D, configManager.getDouble("pets.equipment.health-bonus", 4.0D)));
    }

    /** Awards companion XP for the owner; levels up (rebuffing the active pet) on threshold. */
    public void addXp(final Player player, final int amount) {
        if (amount <= 0 || !canOwnPet(player)) {
            return;
        }
        final int maxLevel = Math.max(1, configManager.getInt("pets.companion.max-level", 30));
        int level = getLevel(player);
        if (level >= maxLevel) {
            return;
        }

        int xp = getXp(player) + amount;
        boolean leveled = false;
        int cost = levelCost(level);
        while (level < maxLevel && xp >= cost) {
            xp -= cost;
            level++;
            leveled = true;
            cost = levelCost(level);
        }

        player.getPersistentDataContainer().set(xpKey, PersistentDataType.INTEGER, xp);
        if (leveled) {
            player.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
            if (level >= maxLevel) {
                AdvancementService.award(player, "pet_bond");
            }
            final Mob pet = activePet(player);
            if (pet != null) {
                applyBuffs(pet, level, false);
                updateName(pet, player);
            }
            player.sendMessage(messageManager.getMessage(
                    "pet-level-up",
                    "<dark_green>🐾 A társad szintet lépett: <white>{level}</white></dark_green>",
                    Map.of("level", String.valueOf(level))));
        }
    }

    /**
     * Owner-side gate of the combat-target flow: the owner has a pet-owning spec AND a live
     * companion. Reads the OWNER's PDC — must run on the owner's region thread (Folia).
     */
    public boolean canReceiveCombatTarget(final Player owner) {
        return owner != null && canOwnPet(owner) && activePet(owner) != null;
    }

    /**
     * Target-side filter of the combat-target flow: the target is alive, not the owner and not
     * one of the owner's own minions (a pet never turns on its allies). Reads the TARGET's
     * state/PDC — must run on the target's region thread (Folia).
     */
    public boolean isEligibleCombatTarget(final UUID ownerId, final LivingEntity target) {
        return ownerId != null && target != null && !target.isDead() && target.isValid()
                && !target.getUniqueId().equals(ownerId)
                && !minionManager.isOwnedBy(target, ownerId);
    }

    /**
     * Records a validated combat target for the owner's pet (assist/defend). Concurrent-map
     * write — safe from any region thread once both sides were validated on their own threads
     * ({@link #canReceiveCombatTarget} / {@link #isEligibleCombatTarget}); the pet controller
     * {@link #tick()} re-validates the target anyway.
     */
    public void putCombatTarget(final UUID ownerId, final UUID targetId) {
        if (ownerId != null && targetId != null) {
            combatTargets.put(ownerId, targetId);
        }
    }

    /**
     * Drives every active companion each scheduler pass. The pet's behaviour is
     * controlled entirely by the plugin (not the mob's own AI): in ACTIVE stance it
     * chases its target via the pathfinder and lands plugin-applied hits, so even a
     * peaceful animal fights like a real pet; with no target it follows the owner.
     * STAY holds position, PASSIVE only follows.
     */
    public void tick() {
        final double followSq = Math.pow(Math.max(4.0D, configManager.getDouble("pets.companion.follow-distance", 16.0D)), 2);
        final double followStartSq = Math.pow(Math.max(2.0D, configManager.getDouble("pets.companion.follow-start-distance", 5.0D)), 2);
        final double reach = Math.max(1.5D, configManager.getDouble("pets.companion.attack-reach", 2.6D));
        final double aggro = Math.max(0.0D, configManager.getDouble("pets.companion.aggro-range", 10.0D));
        final double leash = Math.max(8.0D, configManager.getDouble("pets.companion.leash-range", 24.0D));
        final double chaseSpeed = Math.max(0.1D, configManager.getDouble("pets.companion.chase-speed", 1.3D));
        final long cooldownMs = Math.max(200L, configManager.getInt("pets.companion.attack-cooldown-ticks", 16) * 50L);

        // Folia: read each owner's PDC + location on the OWNER's region thread, snapshot it,
        // then hop to the PET's region thread for all pet mutations (the pet may be elsewhere).
        for (final UUID ownerId : activeOwners) {
            final Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null) {
                activeOwners.remove(ownerId);
                continue;
            }
            owner.getScheduler().run(plugin, ownerTask ->
                    tickOwner(owner, followSq, followStartSq, reach, aggro, leash, chaseSpeed, cooldownMs), null);
        }
    }

    private void tickOwner(final Player owner, final double followSq, final double followStartSq, final double reach,
                           final double aggro, final double leash, final double chaseSpeed, final long cooldownMs) {
        final String raw = owner.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        if (raw == null) {
            return;
        }
        final Entity entity;
        try {
            entity = Bukkit.getEntity(UUID.fromString(raw));
        } catch (final IllegalArgumentException exception) {
            return;
        }
        if (!(entity instanceof Mob pet)) {
            return;
        }
        final UUID ownerId = owner.getUniqueId();
        final int level = getLevel(owner);
        final Location ownerLoc = owner.getLocation();
        final World ownerWorld = owner.getWorld();
        final MinionManager.Stance stance = getStance(owner);
        pet.getScheduler().run(plugin, petTask ->
                runPetTick(pet, ownerId, stance, ownerLoc, ownerWorld, level, followSq, followStartSq, reach, aggro, leash, chaseSpeed, cooldownMs), null);
    }

    private void runPetTick(final Mob pet, final UUID ownerId, final MinionManager.Stance stance,
                            final Location ownerLoc, final World ownerWorld,
                            final int level, final double followSq, final double followStartSq, final double reach,
                            final double aggro, final double leash, final double chaseSpeed, final long cooldownMs) {
        if (!pet.isValid()) {
            return;
        }

        if (stance == MinionManager.Stance.STAY) {
            combatTargets.remove(ownerId);
            // Világváltásnál a STAY pet sem maradhat árván a régi világban.
            if (!pet.getWorld().equals(ownerWorld)) {
                pet.teleportAsync(ownerLoc);
            }
            return; // hold position — no follow, no combat
        }

        LivingEntity target = null;
        if (stance == MinionManager.Stance.ACTIVE) {
            target = resolveTarget(ownerId, ownerWorld, ownerLoc, leash);
            if (target == null) {
                target = acquireNearbyThreat(pet, ownerId, aggro);
                if (target != null) {
                    combatTargets.put(ownerId, target.getUniqueId());
                }
            }
        } else {
            combatTargets.remove(ownerId); // PASSIVE never fights
        }

        if (target != null) {
            attack(pet, target, level, reach, chaseSpeed, cooldownMs);
            return;
        }

        // No target → follow the owner. Every pet trails its owner by default: it walks toward
        // them once it lags past the follow-start radius, and teleports to catch up only when it
        // falls too far behind or ends up in another world.
        followOwner(pet, ownerLoc, ownerWorld, followSq, followStartSq, chaseSpeed);
    }

    /** Keeps an idle pet near its owner (snapshot location): walk to trail, teleport to catch up. */
    private void followOwner(final Mob pet, final Location ownerLoc, final World ownerWorld, final double followSq,
                             final double followStartSq, final double chaseSpeed) {
        if (!ownerWorld.equals(pet.getWorld())) {
            pet.teleportAsync(ownerLoc);
            return;
        }
        final double distSq = pet.getLocation().distanceSquared(ownerLoc);
        if (distSq > followSq) {
            pet.teleportAsync(ownerLoc);
        } else if (distSq > followStartSq) {
            pet.getPathfinder().moveTo(ownerLoc, chaseSpeed);
        }
    }

    /** Validates the stored combat target (alive, same world, within the owner's leash). */
    private LivingEntity resolveTarget(final UUID ownerId, final World ownerWorld, final Location ownerLoc, final double leash) {
        final UUID id = combatTargets.get(ownerId);
        if (id == null) {
            return null;
        }
        final Entity entity = Bukkit.getEntity(id);
        if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()
                || living.getUniqueId().equals(ownerId)
                || !living.getWorld().equals(ownerWorld)
                || living.getLocation().distanceSquared(ownerLoc) > leash * leash) {
            combatTargets.remove(ownerId);
            return null;
        }
        return living;
    }

    /** Picks the nearest hostile mob around the pet to defend against (excludes allies). */
    private LivingEntity acquireNearbyThreat(final Mob pet, final UUID ownerId, final double aggro) {
        if (aggro <= 0.0D) {
            return null;
        }
        LivingEntity best = null;
        double bestSq = aggro * aggro;
        for (final Entity nearby : pet.getNearbyEntities(aggro, aggro, aggro)) {
            if (!(nearby instanceof Monster monster) || monster.isDead() || !monster.isValid()
                    || minionManager.isOwnedBy(monster, ownerId)) {
                continue;
            }
            final double sq = monster.getLocation().distanceSquared(pet.getLocation());
            if (sq < bestSq) {
                bestSq = sq;
                best = monster;
            }
        }
        return best;
    }

    /** Chases the target via the pathfinder and lands a plugin-applied hit on cooldown. */
    private void attack(final Mob pet, final LivingEntity target, final int level, final double reach,
                        final double chaseSpeed, final long cooldownMs) {
        pet.setTarget(target); // reinforce mobs that do have attack AI
        if (pet.getLocation().distanceSquared(target.getLocation()) > reach * reach) {
            pet.getPathfinder().moveTo(target, chaseSpeed); // AI-independent chase
            return;
        }
        final long now = System.currentTimeMillis();
        final Long ready = attackReady.get(pet.getUniqueId());
        if (ready != null && now < ready) {
            return;
        }
        attackReady.put(pet.getUniqueId(), now + cooldownMs);
        pet.swingMainHand();
        target.damage(petDamage(level), pet); // the plugin lands the hit, whatever the mob is
    }

    private double petDamage(final int level) {
        final double base = Math.max(0.5D, configManager.getDouble("pets.companion.attack-damage-base", 3.0D));
        final double perLevel = Math.max(0.0D, configManager.getDouble("pets.companion.damage-per-level", 0.5D));
        return base + (level * perLevel);
    }

    private EntityType resolveType(final Player player) {
        final String stored = player.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        if (stored != null) {
            try {
                return EntityType.valueOf(stored.toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException ignored) {
                // Fall through to the default.
            }
        }
        return isNecromancer(player) ? EntityType.ZOMBIE : EntityType.WOLF;
    }

    private void adopt(final Mob mob, final Player player) {
        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);
        if (mob instanceof Tameable tameable) {
            tameable.setTamed(true);
            tameable.setOwner(player);
        }
        // A pet BÁRMILYEN mob lehet (Beast Master / Necromancer): a közös keményítés fedi a
        // zombi/csontváz/phantom nappali égést ÉS a piglin/hoglin overworld-zombisodását is.
        EventSpawnGuard.prepare(mob);
        // Idézett társ prémiuma: bónusz-szintekkel skálázott statok (a rituálé-beszerzés ára).
        final int buffLevel = getLevel(player)
                + (isSummonedPet(player) ? Math.max(0, configManager.getInt("pets.summon.bonus-levels", 5)) : 0);
        applyBuffs(mob, buffLevel, true);
        // A gazda max-health talentjei a PERMANENS társat is erősítik (a minionokkal
        // azonos megosztási arány — a két rendszer skálázása konzisztens).
        final hu.taliann.icesmp.managers.TalentManager talents = this.talentManagerRef;
        if (talents != null) {
            final double share = Math.max(0.0D, talents.getEffectTotal(player, "max-health")
                    * Math.max(0.0D, configManager.getDouble("pets.talent-health-share", 0.5D)));
            final org.bukkit.attribute.AttributeInstance hp =
                    mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (share > 0.0D && hp != null) {
                hp.setBaseValue(hp.getBaseValue() + share);
                mob.setHealth(hp.getValue());
            }
        }
        if (hasPetArmor(player)) {
            applyEquipment(mob);
            final AttributeInstance hp = mob.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) {
                mob.setHealth(hp.getValue());
            }
        }
        updateName(mob, player);
        minionManager.tag(mob, player.getUniqueId());
        player.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, mob.getUniqueId().toString());
        activeOwners.add(player.getUniqueId());
    }

    private int levelCost(final int level) {
        final int base = Math.max(1, configManager.getInt("pets.companion.base-xp", 10));
        final int increment = Math.max(0, configManager.getInt("pets.companion.increment-per-level", 5));
        return base + ((level - 1) * increment);
    }

    /**
     * (Re)applies the level-based attribute buffs. {@code heal} fully restores the
     * pet to its new max health — only wanted on summon/adopt. On level-up the cap
     * grows but current health is NOT topped up, so a kill mid-fight can't instantly
     * heal a near-dead pet to full.
     */
    private void applyBuffs(final LivingEntity pet, final int level, final boolean heal) {
        final double healthPerLevel = Math.max(0.0D, configManager.getDouble("pets.companion.health-per-level", 2.0D));
        final double damagePerLevel = Math.max(0.0D, configManager.getDouble("pets.companion.damage-per-level", 0.5D));

        // Idempotent attribute modifiers (re-applied on level-up without compounding).
        applyModifier(pet, Attribute.MAX_HEALTH, healthModKey, level * healthPerLevel);
        applyModifier(pet, Attribute.ATTACK_DAMAGE, damageModKey, level * damagePerLevel);

        if (heal) {
            final AttributeInstance maxHealth = pet.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                pet.setHealth(maxHealth.getValue());
            }
        }
    }

    private void applyModifier(final LivingEntity pet, final Attribute attribute, final NamespacedKey key, final double amount) {
        final AttributeInstance instance = pet.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        for (final AttributeModifier modifier : instance.getModifiers()) {
            if (key.equals(modifier.getKey())) {
                instance.removeModifier(modifier);
            }
        }
        if (amount != 0.0D) {
            instance.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void updateName(final Mob pet, final Player player) {
        pet.customName(Component.text(getName(player) + " [Lv " + getLevel(player) + "]", NamedTextColor.GREEN));
        pet.setCustomNameVisible(true);
    }

    /**
     * Clears the owner-keyed combat-target entry on logout so a player who disconnects mid-combat
     * does not leave a stale {@code combatTargets} entry. ({@code attackReady} is pet-UUID-keyed and
     * pruned on pet death/removal.)
     */
    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId != null) {
            activeOwners.remove(playerId);
            combatTargets.remove(playerId);
            // A gazda nélkül maradt társ/minionok despawnolnak (PDC-ből újraidézhető) —
            // nem maradhat árva, örök-persistent entitás a világban.
            minionManager.removeAllOwned(playerId);
        }
    }

    private Mob activePet(final Player player) {
        final String raw = player.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            final Entity entity = Bukkit.getEntity(UUID.fromString(raw));
            return entity instanceof Mob mob && mob.isValid() ? mob : null;
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean removeActive(final Player player) {
        combatTargets.remove(player.getUniqueId());
        final String raw = player.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        if (raw == null) {
            return false;
        }
        try {
            final UUID petId = UUID.fromString(raw);
            attackReady.remove(petId);
            final Entity entity = Bukkit.getEntity(petId);
            if (entity != null) {
                entity.getScheduler().run(plugin, task -> entity.remove(), null);
                return true;
            }
        } catch (final IllegalArgumentException ignored) {
            // Malformed stored id.
        }
        return false;
    }
}
