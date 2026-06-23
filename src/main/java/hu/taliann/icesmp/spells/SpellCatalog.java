package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.MinionManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.managers.TalentManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

/**
 * Declarative catalog of the themed class and specialization spell pools.
 * Every class and specialization owns at least 10 unique spells; the unlock
 * levels live in config.yml ('classes.*.spell-unlocks' and
 * 'specializations.*.spell-unlocks'). The handcrafted legacy spells are
 * registered separately in IceSMPCore; this catalog adds the expansion pools
 * built from the generic spell building blocks (ConfiguredSpell,
 * ProjectileBurstSpell, BlinkSpell) plus a few bespoke classes.
 */
public final class SpellCatalog {

    private SpellCatalog() {
    }

    public static void registerExpansionSpells(final SpellRegistry registry, final MessageManager mm) {
        registerWizard(registry, mm);
        registerWarrior(registry, mm);
        registerArcher(registry, mm);
        registerAssassin(registry, mm);
        registerElementalist(registry, mm);
        registerNecromancer(registry, mm);
        registerBerserker(registry, mm);
        registerGuardian(registry, mm);
        registerSharpshooter(registry, mm);
        registerBeastMaster(registry, mm);
        registerPoisoner(registry, mm);
        registerPhantom(registry, mm);
        registerDruid(registry, mm);
        registerFeral(registry, mm);
        registerLunar(registry, mm);
        registerPaladin(registry, mm);
        registerHoly(registry, mm);
        registerRetribution(registry, mm);
        registerDeathKnight(registry, mm);
        registerBlood(registry, mm);
        registerFrost(registry, mm);
        registerShaman(registry, mm);
        registerElemental(registry, mm);
        registerEnhancement(registry, mm);
        registerMonk(registry, mm);
        registerWindwalker(registry, mm);
        registerBrewmaster(registry, mm);
        registerTalentSpells(registry, mm);
    }

    /**
     * Spells granted by ACTIVE talents (ROADMAP phase 2), not by class level — so
     * they are only obtainable through the talent tree (e.g. the 'ascendant'
     * capstone). Available to any class once the talent is taken.
     */
    private static void registerTalentSpells(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "talent_surge", "Talentum Lendület", 300, SpellCostType.XP, 100)
                .selfEffect(PotionEffectType.STRENGTH, 12 * 20, 1)
                .selfEffect(PotionEffectType.RESISTANCE, 12 * 20, 0)
                .selfEffect(PotionEffectType.REGENERATION, 8 * 20, 1)
                .selfEffect(PotionEffectType.ABSORPTION, 12 * 20, 1)
                .particle(Particle.TOTEM_OF_UNDYING, 40).sound(Sound.ITEM_TOTEM_USE, 0.7F, 1.2F)
                .build());
    }

    /**
     * Summoning spells (WoW-style combat pets): the necromancer raises undead
     * minions, the beast master calls wild animals. Registered separately
     * because they need the plugin instance and the MinionManager.
     */
    public static void registerSummonSpells(final SpellRegistry registry, final MessageManager mm,
                                            final JavaPlugin plugin, final MinionManager minionManager,
                                            final ConfigManager configManager, final TalentManager talentManager) {
        // --- NEKROMANTA idézések ---
        registry.register(new SummonMinionsSpell(plugin, minionManager, configManager, talentManager, mm,
                "raise_horde", "Holtak Hada", 240, SpellCostType.XP, 90,
                Zombie.class, 4, 45,
                (mob, owner) -> {
                    final Zombie zombie = (Zombie) mob;
                    zombie.setShouldBurnInDay(false);
                    zombie.setAdult();
                },
                Particle.SOUL, Sound.ENTITY_ZOMBIE_AMBIENT, 0.7F,
                "<dark_gray>A föld megnyílik: a holtak hada engedelmeskedik a hívásodnak.</dark_gray>"));
        registry.register(new SummonMinionsSpell(plugin, minionManager, configManager, talentManager, mm,
                "bone_archers", "Csontíjászok", 240, SpellCostType.XP, 100,
                Skeleton.class, 2, 45,
                (mob, owner) -> {
                    final AbstractSkeleton skeleton = (AbstractSkeleton) mob;
                    skeleton.setShouldBurnInDay(false);
                    skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
                    skeleton.getEquipment().setItemInMainHandDropChance(0.0F);
                },
                Particle.SOUL_FIRE_FLAME, Sound.ENTITY_SKELETON_AMBIENT, 0.7F,
                "<dark_gray>Csontíjászok emelkednek ki a sírjukból, hogy szolgáljanak.</dark_gray>"));

        // --- VADMESTER idézések ---
        registry.register(new SummonMinionsSpell(plugin, minionManager, configManager, talentManager, mm,
                "panda_guard", "Pandaőrség", 240, SpellCostType.HUNGER, 8,
                Panda.class, 2, 40,
                (mob, owner) -> {
                    final Panda panda = (Panda) mob;
                    panda.setMainGene(Panda.Gene.AGGRESSIVE);
                    panda.setHiddenGene(Panda.Gene.AGGRESSIVE);
                },
                Particle.CLOUD, Sound.ENTITY_PANDA_AGGRESSIVE_AMBIENT, 1.0F,
                "<dark_green>Két harcias panda csörtet a segítségedre.</dark_green>"));
        registry.register(new SummonMinionsSpell(plugin, minionManager, configManager, talentManager, mm,
                "wild_pack", "Vad Falka", 240, SpellCostType.HUNGER, 9,
                Wolf.class, 3, 40,
                (mob, owner) -> {
                    final Wolf wolf = (Wolf) mob;
                    wolf.setAngry(true);
                },
                Particle.CLOUD, Sound.ENTITY_WOLF_GROWL, 0.8F,
                "<dark_green>Vad falka tör elő a vadonból, és melléd szegődik.</dark_green>"));
    }

    // ===== VARÁZSLÓ (wizard) =====
    private static void registerWizard(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "mana_dart", "Mana Nyíl", 10, SpellCostType.XP, 8)
                .target(14.0D).damage(3.0D)
                .particle(Particle.ENCHANT, 20).sound(Sound.ENTITY_ALLAY_HURT, 0.8F, 1.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "frost_touch", "Fagyérintés", 45, SpellCostType.XP, 15)
                .target(8.0D).freeze(100).targetEffect(PotionEffectType.SLOWNESS, 80, 1)
                .particle(Particle.SNOWFLAKE, 25).sound(Sound.BLOCK_GLASS_BREAK, 0.7F, 1.5F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "arcane_shield", "Arkán Pajzs", 90, SpellCostType.XP, 30)
                .selfEffect(PotionEffectType.ABSORPTION, 12 * 20, 1)
                .selfEffect(PotionEffectType.RESISTANCE, 12 * 20, 0)
                .particle(Particle.END_ROD, 30).sound(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 1.2F)
                .build());
        registry.register(new BlinkSpell(mm, "blink", "Villanás", 30, SpellCostType.XP, 25,
                8.0D, Particle.PORTAL, Sound.ENTITY_ENDERMAN_TELEPORT));
        registry.register(ConfiguredSpell.builder(mm, "arcane_nova", "Arkán Lökés", 90, SpellCostType.XP, 40)
                .aoe(5.0D).damage(3.0D).knockback(1.1D)
                .particle(Particle.WITCH, 50).sound(Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0F, 0.9F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "gravity_well", "Gravitációs Örvény", 150, SpellCostType.XP, 60)
                .aoe(6.0D).targetEffect(PotionEffectType.LEVITATION, 2 * 20, 1)
                .targetEffect(PotionEffectType.SLOWNESS, 5 * 20, 2)
                .particle(Particle.PORTAL, 60).sound(Sound.BLOCK_BEACON_DEACTIVATE, 1.0F, 0.6F)
                .build());
    }

    // ===== HARCOS (warrior) =====
    private static void registerWarrior(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "battle_shout", "Csatakiáltás", 90, SpellCostType.HUNGER, 4)
                .selfEffect(PotionEffectType.STRENGTH, 15 * 20, 0)
                .selfEffect(PotionEffectType.SPEED, 15 * 20, 0)
                .particle(Particle.SWEEP_ATTACK, 8).sound(Sound.ENTITY_RAVAGER_ROAR, 0.7F, 1.4F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "heroic_leap", "Hősi Szökellés", 25, SpellCostType.HUNGER, 3)
                .dash(1.4D, 0.8D)
                .particle(Particle.CLOUD, 15).sound(Sound.ENTITY_HORSE_JUMP, 0.8F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "pommel_strike", "Markolatütés", 30, SpellCostType.HUNGER, 4)
                .target(4.0D).damage(3.0D).targetEffect(PotionEffectType.SLOWNESS, 2 * 20, 3)
                .particle(Particle.CRIT, 15).sound(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "second_wind", "Második Lendület", 180, SpellCostType.HUNGER, 8)
                .healSelf(6.0D).selfEffect(PotionEffectType.REGENERATION, 6 * 20, 0)
                .particle(Particle.HEART, 10).sound(Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "whirlwind", "Forgószél", 60, SpellCostType.HUNGER, 6)
                .aoe(4.0D).damage(4.0D).knockback(0.7D)
                .particle(Particle.SWEEP_ATTACK, 12).sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "iron_skin", "Vasbőr", 150, SpellCostType.HUNGER, 8)
                .selfEffect(PotionEffectType.RESISTANCE, 10 * 20, 1)
                .particle(Particle.ASH, 30).sound(Sound.BLOCK_ANVIL_LAND, 0.5F, 1.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "intimidate", "Megfélemlítés", 120, SpellCostType.HUNGER, 6)
                .aoe(6.0D).targetEffect(PotionEffectType.WEAKNESS, 8 * 20, 0)
                .targetEffect(PotionEffectType.SLOWNESS, 8 * 20, 0)
                .particle(Particle.SMOKE, 30).sound(Sound.ENTITY_WARDEN_ROAR, 0.5F, 1.5F)
                .build());
    }

    // ===== ÍJÁSZ (archer) =====
    private static void registerArcher(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "hunters_mark", "Vadászjel", 30, SpellCostType.HUNGER, 2)
                .target(20.0D).targetEffect(PotionEffectType.GLOWING, 10 * 20, 0)
                .particle(Particle.GLOW, 20).sound(Sound.ENTITY_ARROW_HIT_PLAYER, 0.8F, 1.4F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "disengage", "Hátraszökkenés", 20, SpellCostType.HUNGER, 3)
                .dash(-1.2D, 0.6D)
                .particle(Particle.CLOUD, 12).sound(Sound.ENTITY_RABBIT_JUMP, 1.0F, 0.8F)
                .build());
        registry.register(new ProjectileBurstSpell(mm, "piercing_bolt", "Átütő Lövedék", 35, SpellCostType.XP, 25,
                ProjectileBurstSpell.ProjectileKind.SPECTRAL_ARROW, 1, 0.0D, 3.2D,
                Sound.ITEM_CROSSBOW_SHOOT, 1.0F, 0.7F));
        registry.register(ConfiguredSpell.builder(mm, "snare", "Csapdázás", 45, SpellCostType.HUNGER, 4)
                .target(14.0D).targetEffect(PotionEffectType.SLOWNESS, 3 * 20, 3)
                .targetEffect(PotionEffectType.JUMP_BOOST, 3 * 20, 200)
                .particle(Particle.ITEM_SLIME, 20).sound(Sound.BLOCK_TRIPWIRE_CLICK_ON, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "camouflage", "Álcázás", 120, SpellCostType.HUNGER, 5)
                .selfEffect(PotionEffectType.INVISIBILITY, 8 * 20, 0)
                .particle(Particle.COMPOSTER, 20).sound(Sound.BLOCK_GRASS_BREAK, 0.8F, 0.8F)
                .build());
        registry.register(new ProjectileBurstSpell(mm, "arrow_storm", "Nyílzápor", 90, SpellCostType.XP, 60,
                ProjectileBurstSpell.ProjectileKind.ARROW, 7, 6.0D, 2.0D,
                Sound.ITEM_CROSSBOW_SHOOT, 1.0F, 1.2F));
        registry.register(ConfiguredSpell.builder(mm, "swift_steps", "Szélléptek", 90, SpellCostType.HUNGER, 5)
                .selfEffect(PotionEffectType.SPEED, 20 * 20, 1)
                .selfEffect(PotionEffectType.JUMP_BOOST, 20 * 20, 1)
                .particle(Particle.CLOUD, 15).sound(Sound.ENTITY_BREEZE_IDLE_AIR, 1.0F, 1.3F)
                .build());
    }

    // ===== ORGYILKOS (assassin) =====
    private static void registerAssassin(final SpellRegistry registry, final MessageManager mm) {
        registry.register(new ProjectileBurstSpell(mm, "dagger_throw", "Tőrhajítás", 15, SpellCostType.HUNGER, 2,
                ProjectileBurstSpell.ProjectileKind.ARROW, 1, 0.0D, 2.6D,
                Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8F, 1.6F));
        registry.register(ConfiguredSpell.builder(mm, "adrenaline", "Adrenalin", 60, SpellCostType.HUNGER, 4)
                .selfEffect(PotionEffectType.SPEED, 8 * 20, 1)
                .selfEffect(PotionEffectType.HASTE, 8 * 20, 1)
                .particle(Particle.CRIT, 15).sound(Sound.ENTITY_PLAYER_BREATH, 1.0F, 1.5F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "hamstring", "Ínmetszés", 35, SpellCostType.HUNGER, 4)
                .target(5.0D).damage(2.0D).targetEffect(PotionEffectType.SLOWNESS, 5 * 20, 2)
                .particle(Particle.DAMAGE_INDICATOR, 10).sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "blackout", "Elsötétítés", 60, SpellCostType.HUNGER, 5)
                .target(10.0D).targetEffect(PotionEffectType.BLINDNESS, 4 * 20, 0)
                .targetEffect(PotionEffectType.WEAKNESS, 6 * 20, 0)
                .particle(Particle.LARGE_SMOKE, 25).sound(Sound.BLOCK_CANDLE_EXTINGUISH, 1.0F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "evasion", "Kitérés", 75, SpellCostType.HUNGER, 5)
                .dash(-0.9D, 0.5D)
                .selfEffect(PotionEffectType.INVISIBILITY, 3 * 20, 0)
                .selfEffect(PotionEffectType.SPEED, 5 * 20, 1)
                .particle(Particle.SMOKE, 20).sound(Sound.ENTITY_BREEZE_SLIDE, 1.0F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "garrote", "Fojtás", 90, SpellCostType.HEALTH, 3)
                .target(3.5D).damage(5.0D).targetEffect(PotionEffectType.SLOWNESS, 3 * 20, 4)
                .particle(Particle.SMOKE, 15).sound(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "shadow_dash", "Árnyéksuhanás", 60, SpellCostType.HUNGER, 5)
                .dash(1.8D, 0.3D)
                .selfEffect(PotionEffectType.INVISIBILITY, 2 * 20, 0)
                .particle(Particle.LARGE_SMOKE, 25).sound(Sound.ENTITY_BREEZE_SLIDE, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "death_mark", "Haláljegy", 120, SpellCostType.HEALTH, 3)
                .target(15.0D).targetEffect(PotionEffectType.GLOWING, 12 * 20, 0)
                .targetEffect(PotionEffectType.WEAKNESS, 12 * 20, 1)
                .particle(Particle.SOUL, 25).sound(Sound.ENTITY_VEX_CHARGE, 1.0F, 0.7F)
                .build());
    }

    // ===== ELEMENTALISTA (wizard spec) =====
    private static void registerElementalist(final SpellRegistry registry, final MessageManager mm) {
        registry.register(new ProjectileBurstSpell(mm, "fireball", "Tűzgolyó", 45, SpellCostType.XP, 40,
                ProjectileBurstSpell.ProjectileKind.SMALL_FIREBALL, 1, 0.0D, 1.8D,
                Sound.ITEM_FIRECHARGE_USE, 1.0F, 1.0F));
        registry.register(ConfiguredSpell.builder(mm, "frost_nova", "Fagyrobbanás", 90, SpellCostType.XP, 60)
                .aoe(6.0D).freeze(120).targetEffect(PotionEffectType.SLOWNESS, 6 * 20, 2)
                .particle(Particle.SNOWFLAKE, 60).sound(Sound.BLOCK_GLASS_BREAK, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "thunder_strike", "Mennykőcsapás", 120, SpellCostType.XP, 80)
                .target(18.0D).damage(6.0D).lightning()
                .particle(Particle.ELECTRIC_SPARK, 40).sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "ember_storm", "Parázsvihar", 120, SpellCostType.XP, 70)
                .aoe(6.0D).damage(2.0D).ignite(4 * 20)
                .particle(Particle.FLAME, 60).sound(Sound.ITEM_FIRECHARGE_USE, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "riptide_pull", "Örvényrántás", 60, SpellCostType.XP, 45)
                .target(14.0D).pull(1.4D).targetEffect(PotionEffectType.SLOWNESS, 2 * 20, 1)
                .particle(Particle.SPLASH, 40).sound(Sound.ENTITY_DROWNED_SHOOT, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "stone_skin", "Kőbőr", 150, SpellCostType.XP, 60)
                .selfEffect(PotionEffectType.RESISTANCE, 12 * 20, 1)
                .selfEffect(PotionEffectType.SLOWNESS, 12 * 20, 0)
                .particle(Particle.ASH, 40).sound(Sound.BLOCK_STONE_PLACE, 1.0F, 0.6F)
                .build());
        registry.register(new ProjectileBurstSpell(mm, "gale_burst", "Viharlöket", 40, SpellCostType.XP, 35,
                ProjectileBurstSpell.ProjectileKind.WIND_CHARGE, 1, 0.0D, 1.6D,
                Sound.ENTITY_BREEZE_SHOOT, 1.0F, 1.0F));
        registry.register(ConfiguredSpell.builder(mm, "elemental_overload", "Elemi Túltöltés", 300, SpellCostType.XP, 150)
                .aoe(7.0D).damage(5.0D).ignite(3 * 20).freeze(80).knockback(0.8D)
                .particle(Particle.FLASH, 5).sound(Sound.ENTITY_GENERIC_EXPLODE, 0.8F, 1.2F)
                .build());
    }

    // ===== NEKROMANTA (wizard spec) =====
    private static void registerNecromancer(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "grave_grasp", "Sírkéz", 45, SpellCostType.HEALTH, 2)
                .target(10.0D).targetEffect(PotionEffectType.SLOWNESS, 4 * 20, 3)
                .targetEffect(PotionEffectType.WITHER, 4 * 20, 0)
                .particle(Particle.SOUL, 25).sound(Sound.BLOCK_SOUL_SAND_BREAK, 1.0F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "soul_siphon", "Lélekszívás", 90, SpellCostType.XP, 50)
                .aoe(5.0D).damage(2.0D).healSelf(4.0D)
                .particle(Particle.SCULK_SOUL, 40).sound(Sound.PARTICLE_SOUL_ESCAPE, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "dread_aura", "Rettegésaura", 120, SpellCostType.HEALTH, 4)
                .aoe(6.0D).targetEffect(PotionEffectType.WITHER, 5 * 20, 0)
                .targetEffect(PotionEffectType.WEAKNESS, 8 * 20, 0)
                .particle(Particle.SQUID_INK, 50).sound(Sound.ENTITY_WITHER_AMBIENT, 0.6F, 0.8F)
                .build());
        registry.register(new ProjectileBurstSpell(mm, "bone_spear", "Csontdárda", 30, SpellCostType.XP, 30,
                ProjectileBurstSpell.ProjectileKind.SPECTRAL_ARROW, 1, 0.0D, 3.0D,
                Sound.ENTITY_SKELETON_SHOOT, 1.0F, 0.7F));
        registry.register(ConfiguredSpell.builder(mm, "plague_touch", "Dögvészérintés", 75, SpellCostType.HEALTH, 3)
                .target(6.0D).targetEffect(PotionEffectType.POISON, 6 * 20, 0)
                .targetEffect(PotionEffectType.WITHER, 4 * 20, 0)
                .targetEffect(PotionEffectType.HUNGER, 10 * 20, 1)
                .particle(Particle.SNEEZE, 25).sound(Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.6F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "death_pact", "Halálpaktum", 150, SpellCostType.XP, 40)
                .selfDamage(4.0D)
                .selfEffect(PotionEffectType.STRENGTH, 15 * 20, 1)
                .selfEffect(PotionEffectType.ABSORPTION, 15 * 20, 1)
                .particle(Particle.SOUL_FIRE_FLAME, 40).sound(Sound.ENTITY_WITHER_SPAWN, 0.4F, 1.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "wraith_form", "Kísértetforma", 180, SpellCostType.HEALTH, 4)
                .selfEffect(PotionEffectType.INVISIBILITY, 10 * 20, 0)
                .selfEffect(PotionEffectType.SPEED, 10 * 20, 1)
                .selfEffect(PotionEffectType.WEAKNESS, 10 * 20, 0)
                .particle(Particle.SOUL, 40).sound(Sound.ENTITY_PHANTOM_AMBIENT, 0.8F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "soul_harvest", "Lélekaratás", 300, SpellCostType.XP, 150)
                .aoe(7.0D).damage(6.0D).healSelf(6.0D)
                .particle(Particle.SCULK_SOUL, 80).sound(Sound.ENTITY_WITHER_DEATH, 0.4F, 1.4F)
                .build());
    }

    // ===== BERSERKER (warrior spec) =====
    private static void registerBerserker(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "rage", "Düh", 90, SpellCostType.HEALTH, 4)
                .selfEffect(PotionEffectType.STRENGTH, 12 * 20, 1)
                .selfEffect(PotionEffectType.SPEED, 12 * 20, 0)
                .particle(Particle.ANGRY_VILLAGER, 12).sound(Sound.ENTITY_RAVAGER_ROAR, 0.8F, 1.1F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "reckless_swing", "Vakmerő Csapás", 25, SpellCostType.HUNGER, 4)
                .target(4.5D).damage(6.0D).selfDamage(2.0D)
                .particle(Particle.CRIT, 20).sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "blood_frenzy", "Vérszomj", 120, SpellCostType.HUNGER, 6)
                .selfDamage(2.0D)
                .selfEffect(PotionEffectType.HASTE, 15 * 20, 1)
                .selfEffect(PotionEffectType.STRENGTH, 15 * 20, 0)
                .particle(Particle.DAMAGE_INDICATOR, 15).sound(Sound.ENTITY_WOLF_GROWL, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "ground_slam", "Földcsapás", 60, SpellCostType.HUNGER, 6)
                .aoe(5.0D).damage(3.0D).launchUp(0.8D)
                .particle(Particle.EXPLOSION, 6).sound(Sound.ENTITY_GENERIC_EXPLODE, 0.7F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "roar", "Üvöltés", 90, SpellCostType.HUNGER, 5)
                .aoe(7.0D).targetEffect(PotionEffectType.SLOWNESS, 6 * 20, 1)
                .targetEffect(PotionEffectType.WEAKNESS, 6 * 20, 0)
                .particle(Particle.CLOUD, 30).sound(Sound.ENTITY_WARDEN_ROAR, 0.7F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "unstoppable", "Megállíthatatlan", 150, SpellCostType.HEALTH, 4)
                .selfEffect(PotionEffectType.ABSORPTION, 12 * 20, 1)
                .selfEffect(PotionEffectType.SPEED, 12 * 20, 1)
                .particle(Particle.TOTEM_OF_UNDYING, 30).sound(Sound.ITEM_TOTEM_USE, 0.5F, 1.5F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "savage_leap", "Vad Ugrás", 40, SpellCostType.HUNGER, 4)
                .dash(1.8D, 0.9D)
                .particle(Particle.CLOUD, 20).sound(Sound.ENTITY_RAVAGER_STEP, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "bloodbath", "Vérfürdő", 150, SpellCostType.HEALTH, 4)
                .aoe(5.0D).damage(5.0D).healSelf(4.0D)
                .particle(Particle.DAMAGE_INDICATOR, 40).sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.5F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "berserk", "Berserk", 300, SpellCostType.HUNGER, 12)
                .selfEffect(PotionEffectType.STRENGTH, 10 * 20, 2)
                .selfEffect(PotionEffectType.SPEED, 10 * 20, 1)
                .selfEffect(PotionEffectType.RESISTANCE, 10 * 20, 0)
                .particle(Particle.ANGRY_VILLAGER, 25).sound(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5F, 1.4F)
                .build());
    }

    // ===== VÉDELMEZŐ (warrior spec) =====
    private static void registerGuardian(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "taunt", "Kihívás", 60, SpellCostType.HUNGER, 4)
                .aoe(6.0D).targetEffect(PotionEffectType.SLOWNESS, 4 * 20, 1)
                .selfEffect(PotionEffectType.RESISTANCE, 6 * 20, 0)
                .particle(Particle.ANGRY_VILLAGER, 15).sound(Sound.ITEM_SHIELD_BLOCK, 1.0F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "fortify", "Megerősítés", 120, SpellCostType.HUNGER, 6)
                .selfEffect(PotionEffectType.ABSORPTION, 30 * 20, 1)
                .particle(Particle.END_ROD, 25).sound(Sound.BLOCK_ANVIL_USE, 0.6F, 1.4F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "shield_wall", "Pajzsfal", 150, SpellCostType.HUNGER, 8)
                .selfEffect(PotionEffectType.RESISTANCE, 8 * 20, 2)
                .selfEffect(PotionEffectType.SLOWNESS, 8 * 20, 1)
                .particle(Particle.ASH, 40).sound(Sound.ITEM_SHIELD_BLOCK, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "healing_word", "Gyógyító Szó", 120, SpellCostType.HUNGER, 6)
                .aoe(8.0D).friendly()
                .targetEffect(PotionEffectType.REGENERATION, 8 * 20, 0)
                .targetEffect(PotionEffectType.ABSORPTION, 12 * 20, 0)
                .healSelf(2.0D)
                .particle(Particle.HEART, 30).sound(Sound.BLOCK_BELL_RESONATE, 0.8F, 1.4F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "iron_bastion", "Vasbástya", 150, SpellCostType.HUNGER, 7)
                .selfEffect(PotionEffectType.RESISTANCE, 12 * 20, 0)
                .selfEffect(PotionEffectType.FIRE_RESISTANCE, 12 * 20, 0)
                .particle(Particle.LAVA, 10).sound(Sound.BLOCK_NETHERITE_BLOCK_PLACE, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "shield_charge", "Pajzsroham", 45, SpellCostType.HUNGER, 4)
                .dash(1.6D, 0.3D)
                .selfEffect(PotionEffectType.RESISTANCE, 3 * 20, 1)
                .particle(Particle.CLOUD, 15).sound(Sound.ITEM_SHIELD_BLOCK, 1.0F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "aegis", "Égisz", 180, SpellCostType.HUNGER, 8)
                .aoe(8.0D).friendly()
                .targetEffect(PotionEffectType.RESISTANCE, 10 * 20, 0)
                .particle(Particle.END_ROD, 50).sound(Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "retribution", "Megtorlás", 45, SpellCostType.HUNGER, 5)
                .target(5.0D).damage(4.0D).knockback(1.3D)
                .particle(Particle.SWEEP_ATTACK, 8).sound(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 1.0F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "last_stand", "Végső Ellenállás", 300, SpellCostType.HUNGER, 10)
                .selfEffect(PotionEffectType.ABSORPTION, 15 * 20, 3)
                .selfEffect(PotionEffectType.REGENERATION, 8 * 20, 2)
                .particle(Particle.TOTEM_OF_UNDYING, 50).sound(Sound.ITEM_TOTEM_USE, 0.7F, 1.0F)
                .build());
    }

    // ===== MESTERLÖVÉSZ (archer spec) =====
    private static void registerSharpshooter(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "deadeye", "Sasles", 60, SpellCostType.HUNGER, 4)
                .aoe(20.0D).targetEffect(PotionEffectType.GLOWING, 10 * 20, 0)
                .selfEffect(PotionEffectType.NIGHT_VISION, 15 * 20, 0)
                .particle(Particle.GLOW, 30).sound(Sound.ENTITY_ENDER_EYE_LAUNCH, 1.0F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "headshot", "Fejlövés", 60, SpellCostType.XP, 40)
                .target(15.0D).damage(7.0D)
                .particle(Particle.CRIT, 25).sound(Sound.ENTITY_ARROW_HIT_PLAYER, 1.0F, 0.6F)
                .build());
        registry.register(new ProjectileBurstSpell(mm, "double_tap", "Duplalövés", 30, SpellCostType.HUNGER, 4,
                ProjectileBurstSpell.ProjectileKind.ARROW, 2, 2.0D, 2.8D,
                Sound.ITEM_CROSSBOW_SHOOT, 1.0F, 1.4F));
        registry.register(ConfiguredSpell.builder(mm, "flare", "Jelzőfény", 90, SpellCostType.HUNGER, 4)
                .aoe(15.0D).targetEffect(PotionEffectType.GLOWING, 8 * 20, 0)
                .selfEffect(PotionEffectType.NIGHT_VISION, 20 * 20, 0)
                .particle(Particle.FIREWORK, 40).sound(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0F, 1.0F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "kneecap", "Térdlövés", 45, SpellCostType.HUNGER, 5)
                .target(15.0D).damage(2.0D).targetEffect(PotionEffectType.SLOWNESS, 4 * 20, 3)
                .particle(Particle.DAMAGE_INDICATOR, 12).sound(Sound.ENTITY_ARROW_HIT, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "high_perch", "Magasles", 60, SpellCostType.HUNGER, 4)
                .dash(0.2D, 1.3D)
                .selfEffect(PotionEffectType.SLOW_FALLING, 10 * 20, 0)
                .particle(Particle.CLOUD, 20).sound(Sound.ENTITY_BREEZE_JUMP, 1.0F, 1.0F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "perfect_focus", "Tökéletes Fókusz", 120, SpellCostType.HUNGER, 6)
                .selfEffect(PotionEffectType.HASTE, 15 * 20, 1)
                .selfEffect(PotionEffectType.NIGHT_VISION, 15 * 20, 0)
                .selfEffect(PotionEffectType.SPEED, 15 * 20, 0)
                .particle(Particle.ENCHANT, 30).sound(Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0F, 1.6F)
                .build());
        registry.register(new ProjectileBurstSpell(mm, "spectral_volley", "Szellemsortűz", 90, SpellCostType.XP, 60,
                ProjectileBurstSpell.ProjectileKind.SPECTRAL_ARROW, 5, 5.0D, 2.4D,
                Sound.ITEM_CROSSBOW_SHOOT, 1.0F, 0.9F));
        registry.register(ConfiguredSpell.builder(mm, "masterful_shot", "Mesterlövés", 300, SpellCostType.XP, 80)
                .target(20.0D).damage(12.0D)
                .particle(Particle.FLASH, 3).sound(Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.7F, 1.6F)
                .build());
    }

    // ===== VADMESTER (archer spec) =====
    private static void registerBeastMaster(final SpellRegistry registry, final MessageManager mm) {
        registry.register(new WolfCallSpell(mm));
        registry.register(new BeeSwarmSpell(mm));
        registry.register(new PrimalBondSpell(mm));
        registry.register(ConfiguredSpell.builder(mm, "thick_hide", "Vastag Irha", 120, SpellCostType.HUNGER, 6)
                .selfEffect(PotionEffectType.RESISTANCE, 12 * 20, 0)
                .selfEffect(PotionEffectType.ABSORPTION, 12 * 20, 0)
                .particle(Particle.ASH, 25).sound(Sound.ENTITY_ARMADILLO_HURT_REDUCED, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "predator_senses", "Ragadozó Érzékek", 90, SpellCostType.HUNGER, 4)
                .aoe(18.0D).targetEffect(PotionEffectType.GLOWING, 8 * 20, 0)
                .selfEffect(PotionEffectType.NIGHT_VISION, 20 * 20, 0)
                .particle(Particle.GLOW, 30).sound(Sound.ENTITY_OCELOT_AMBIENT, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "stampede", "Csorda Rohama", 60, SpellCostType.HUNGER, 5)
                .dash(1.7D, 0.4D)
                .selfEffect(PotionEffectType.SPEED, 6 * 20, 2)
                .particle(Particle.CLOUD, 25).sound(Sound.ENTITY_HORSE_GALLOP, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "falcon_strike", "Sólyomcsapás", 60, SpellCostType.HUNGER, 5)
                .target(12.0D).damage(5.0D).targetEffect(PotionEffectType.BLINDNESS, 20, 0)
                .particle(Particle.SWEEP_ATTACK, 8).sound(Sound.ENTITY_PHANTOM_SWOOP, 1.0F, 1.4F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "king_of_beasts", "Vadak Ura", 300, SpellCostType.HUNGER, 10)
                .aoe(7.0D).targetEffect(PotionEffectType.WEAKNESS, 10 * 20, 0)
                .targetEffect(PotionEffectType.SLOWNESS, 10 * 20, 0)
                .selfEffect(PotionEffectType.STRENGTH, 10 * 20, 1)
                .selfEffect(PotionEffectType.RESISTANCE, 10 * 20, 0)
                .particle(Particle.CLOUD, 40).sound(Sound.ENTITY_RAVAGER_ROAR, 1.0F, 0.6F)
                .build());
    }

    // ===== MÉREGKEVERŐ (assassin spec) =====
    private static void registerPoisoner(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "toxin_dart", "Toxinnyíl", 20, SpellCostType.HUNGER, 3)
                .target(12.0D).damage(1.0D).targetEffect(PotionEffectType.POISON, 4 * 20, 0)
                .particle(Particle.SNEEZE, 12).sound(Sound.ENTITY_LLAMA_SPIT, 1.0F, 1.3F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "acid_splash", "Savfröccs", 45, SpellCostType.HUNGER, 4)
                .aoe(4.0D).damage(2.0D).targetEffect(PotionEffectType.POISON, 4 * 20, 0)
                .particle(Particle.ITEM_SLIME, 30).sound(Sound.ENTITY_SLIME_SQUISH, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "numbing_agent", "Zsibbasztószer", 60, SpellCostType.HUNGER, 5)
                .target(8.0D).targetEffect(PotionEffectType.SLOWNESS, 6 * 20, 1)
                .targetEffect(PotionEffectType.MINING_FATIGUE, 6 * 20, 1)
                .targetEffect(PotionEffectType.WEAKNESS, 6 * 20, 0)
                .particle(Particle.WITCH, 20).sound(Sound.ENTITY_WITCH_THROW, 1.0F, 0.8F)
                .build());
        registry.register(new AntidoteSpell(mm));
        registry.register(ConfiguredSpell.builder(mm, "contagion", "Ragály", 120, SpellCostType.HUNGER, 7)
                .aoe(6.0D).targetEffect(PotionEffectType.POISON, 6 * 20, 0)
                .particle(Particle.SNEEZE, 50).sound(Sound.ENTITY_ZOMBIE_INFECT, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "paralytic_strike", "Bénító Csapás", 75, SpellCostType.HUNGER, 6)
                .target(4.5D).damage(2.0D).targetEffect(PotionEffectType.SLOWNESS, 2 * 20, 4)
                .targetEffect(PotionEffectType.WEAKNESS, 5 * 20, 1)
                .particle(Particle.CRIT, 15).sound(Sound.ENTITY_BEE_STING, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "toxic_cloud", "Mérgező Felhő", 120, SpellCostType.HUNGER, 7)
                .aoe(5.0D).targetEffect(PotionEffectType.POISON, 5 * 20, 0)
                .targetEffect(PotionEffectType.NAUSEA, 8 * 20, 0)
                .particle(Particle.SNEEZE, 80).sound(Sound.ENTITY_PUFFER_FISH_BLOW_OUT, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "debilitating_poison", "Sorvasztó Méreg", 90, SpellCostType.XP, 50)
                .target(8.0D).targetEffect(PotionEffectType.POISON, 6 * 20, 1)
                .targetEffect(PotionEffectType.HUNGER, 12 * 20, 2)
                .particle(Particle.ITEM_SLIME, 25).sound(Sound.ENTITY_SPIDER_AMBIENT, 1.0F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "deathcap", "Gyilkos Galóca", 240, SpellCostType.XP, 70)
                .target(8.0D).damage(4.0D).targetEffect(PotionEffectType.POISON, 8 * 20, 2)
                .targetEffect(PotionEffectType.WEAKNESS, 8 * 20, 1)
                .particle(Particle.SPORE_BLOSSOM_AIR, 40).sound(Sound.BLOCK_FUNGUS_BREAK, 1.0F, 0.5F)
                .build());
    }

    // ===== FANTOM (assassin spec) =====
    private static void registerPhantom(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "ghost_step", "Szellemléptek", 60, SpellCostType.HUNGER, 4)
                .selfEffect(PotionEffectType.INVISIBILITY, 5 * 20, 0)
                .selfEffect(PotionEffectType.SPEED, 5 * 20, 0)
                .particle(Particle.SOUL, 15).sound(Sound.ENTITY_BREEZE_SLIDE, 1.0F, 0.6F)
                .build());
        registry.register(new BlinkSpell(mm, "phase_blink", "Fázisugrás", 45, SpellCostType.HUNGER, 5,
                6.0D, Particle.SOUL, Sound.ENTITY_ENDERMAN_TELEPORT));
        registry.register(ConfiguredSpell.builder(mm, "haunt", "Kísértés", 60, SpellCostType.HUNGER, 5)
                .target(10.0D).targetEffect(PotionEffectType.BLINDNESS, 3 * 20, 0)
                .targetEffect(PotionEffectType.DARKNESS, 6 * 20, 0)
                .targetEffect(PotionEffectType.SLOWNESS, 4 * 20, 0)
                .particle(Particle.SOUL, 25).sound(Sound.ENTITY_PHANTOM_BITE, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "terror", "Rémület", 120, SpellCostType.HUNGER, 6)
                .aoe(6.0D).targetEffect(PotionEffectType.BLINDNESS, 3 * 20, 0)
                .targetEffect(PotionEffectType.SLOWNESS, 5 * 20, 1)
                .particle(Particle.SQUID_INK, 40).sound(Sound.ENTITY_PHANTOM_DEATH, 0.8F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "cold_spot", "Hidegfolt", 90, SpellCostType.HUNGER, 5)
                .aoe(5.0D).freeze(100).targetEffect(PotionEffectType.SLOWNESS, 4 * 20, 1)
                .particle(Particle.SNOWFLAKE, 40).sound(Sound.BLOCK_POWDER_SNOW_STEP, 1.0F, 0.5F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "ethereal_form", "Éteri Forma", 180, SpellCostType.HUNGER, 7)
                .selfEffect(PotionEffectType.INVISIBILITY, 8 * 20, 0)
                .selfEffect(PotionEffectType.RESISTANCE, 8 * 20, 0)
                .selfEffect(PotionEffectType.WEAKNESS, 8 * 20, 0)
                .particle(Particle.END_ROD, 30).sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "phantom_grip", "Fantomszorítás", 75, SpellCostType.HEALTH, 3)
                .target(10.0D).damage(2.0D).targetEffect(PotionEffectType.LEVITATION, 2 * 20, 0)
                .particle(Particle.SOUL, 20).sound(Sound.ENTITY_PHANTOM_FLAP, 1.0F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "dread_whisper", "Rémsuttogás", 90, SpellCostType.HEALTH, 3)
                .target(12.0D).targetEffect(PotionEffectType.WEAKNESS, 8 * 20, 1)
                .targetEffect(PotionEffectType.NAUSEA, 8 * 20, 0)
                .targetEffect(PotionEffectType.DARKNESS, 5 * 20, 0)
                .particle(Particle.SCULK_SOUL, 20).sound(Sound.ENTITY_WARDEN_LISTENING, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "spectre", "Kísértet", 300, SpellCostType.HEALTH, 4)
                .selfEffect(PotionEffectType.INVISIBILITY, 15 * 20, 0)
                .selfEffect(PotionEffectType.SPEED, 15 * 20, 1)
                .selfEffect(PotionEffectType.JUMP_BOOST, 15 * 20, 1)
                .particle(Particle.SOUL, 50).sound(Sound.ENTITY_PHANTOM_AMBIENT, 1.0F, 0.5F)
                .build());
    }

    // ===== DRUIDA (base class) — természet-mágia + shapeshift formák =====
    private static void registerDruid(final SpellRegistry registry, final MessageManager mm) {
        // A négy stance (egymást kizáró statisztika-profilok) — lásd DruidFormSpell.
        registry.register(new DruidFormSpell(mm, DruidFormSpell.Form.BEAR));
        registry.register(new DruidFormSpell(mm, DruidFormSpell.Form.CAT));
        registry.register(new DruidFormSpell(mm, DruidFormSpell.Form.MOONKIN));
        registry.register(new DruidFormSpell(mm, DruidFormSpell.Form.TRAVEL));
        registry.register(ConfiguredSpell.builder(mm, "regrowth", "Sarjadás", 30, SpellCostType.XP, 40)
                .healSelf(6.0D).selfEffect(PotionEffectType.REGENERATION, 6 * 20, 0)
                .particle(Particle.HEART, 20).sound(Sound.BLOCK_BELL_RESONATE, 0.8F, 1.4F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "barkskin", "Kéregbőr", 60, SpellCostType.HUNGER, 4)
                .selfEffect(PotionEffectType.RESISTANCE, 8 * 20, 1)
                .particle(Particle.ASH, 25).sound(Sound.ITEM_SHIELD_BLOCK, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "entangling_roots", "Gyökérfonat", 45, SpellCostType.HUNGER, 4)
                .target(8.0D).targetEffect(PotionEffectType.SLOWNESS, 5 * 20, 4)
                .particle(Particle.SNEEZE, 30).sound(Sound.BLOCK_GLASS_BREAK, 1.0F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "thornlash", "Töviscsapás", 25, SpellCostType.HUNGER, 3)
                .target(5.0D).damage(4.0D).targetEffect(PotionEffectType.POISON, 4 * 20, 0)
                .particle(Particle.CRIT, 15).sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.8F)
                .build());
    }

    // ===== VADŐR (druid spec) — közelharci formák (medve/párduc) =====
    private static void registerFeral(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "savage_claw", "Vad Karom", 20, SpellCostType.HUNGER, 3)
                .target(4.5D).damage(6.0D)
                .particle(Particle.CRIT, 20).sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "feral_charge", "Vad Roham", 35, SpellCostType.HUNGER, 4)
                .dash(1.6D, 0.3D).selfEffect(PotionEffectType.RESISTANCE, 3 * 20, 1)
                .particle(Particle.CLOUD, 15).sound(Sound.ENTITY_RAVAGER_ROAR, 1.0F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "maul", "Marás", 30, SpellCostType.HUNGER, 4)
                .target(4.0D).damage(7.0D).knockback(0.8D)
                .particle(Particle.SWEEP_ATTACK, 8).sound(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "rampage", "Tombolás", 90, SpellCostType.HUNGER, 6)
                .selfEffect(PotionEffectType.STRENGTH, 10 * 20, 1).selfEffect(PotionEffectType.SPEED, 10 * 20, 0)
                .particle(Particle.ANGRY_VILLAGER, 15).sound(Sound.ENTITY_RAVAGER_ROAR, 0.9F, 1.0F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "primal_roar", "Ősi Üvöltés", 75, SpellCostType.HUNGER, 5)
                .aoe(6.0D).targetEffect(PotionEffectType.WEAKNESS, 8 * 20, 0)
                .targetEffect(PotionEffectType.SLOWNESS, 5 * 20, 1)
                .particle(Particle.SWEEP_ATTACK, 20).sound(Sound.ENTITY_RAVAGER_ROAR, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "rejuvenate", "Felfrissülés", 60, SpellCostType.XP, 50)
                .healSelf(4.0D).selfEffect(PotionEffectType.REGENERATION, 8 * 20, 1)
                .particle(Particle.HEART, 20).sound(Sound.BLOCK_BELL_RESONATE, 0.7F, 1.5F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "the_wild_hunt", "Vad Hajsza", 300, SpellCostType.XP, 150)
                .aoe(6.0D).damage(8.0D).knockback(1.0D)
                .selfEffect(PotionEffectType.STRENGTH, 12 * 20, 1).selfEffect(PotionEffectType.SPEED, 12 * 20, 1)
                .particle(Particle.CRIT, 50).sound(Sound.ENTITY_RAVAGER_ROAR, 1.0F, 0.8F)
                .build());
    }

    // ===== HOLDJÓS (druid spec) — távolsági hold-/természet-mágia =====
    private static void registerLunar(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "moonfire", "Holdtűz", 20, SpellCostType.XP, 30)
                .target(16.0D).damage(5.0D).ignite(3 * 20)
                .particle(Particle.END_ROD, 25).sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "starsurge", "Csillagár", 35, SpellCostType.XP, 45)
                .target(18.0D).damage(7.0D)
                .particle(Particle.END_ROD, 30).sound(Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.4F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "solar_beam", "Napsugár", 60, SpellCostType.XP, 55)
                .target(14.0D).damage(6.0D).ignite(4 * 20).targetEffect(PotionEffectType.BLINDNESS, 3 * 20, 0)
                .particle(Particle.FLAME, 30).sound(Sound.ITEM_FIRECHARGE_USE, 1.0F, 1.0F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "hurricane", "Hurrikán", 90, SpellCostType.XP, 60)
                .aoe(6.0D).damage(3.0D).knockback(0.6D).targetEffect(PotionEffectType.SLOWNESS, 4 * 20, 1)
                .particle(Particle.CLOUD, 50).sound(Sound.ENTITY_BREEZE_SHOOT, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "starfall", "Csillaghullás", 120, SpellCostType.XP, 80)
                .aoe(6.0D).damage(6.0D)
                .particle(Particle.END_ROD, 60).sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6F, 1.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "lunar_blessing", "Hold Áldása", 120, SpellCostType.HUNGER, 6)
                .aoe(8.0D).friendly().targetEffect(PotionEffectType.REGENERATION, 8 * 20, 0).healSelf(2.0D)
                .particle(Particle.HEART, 30).sound(Sound.BLOCK_BELL_RESONATE, 0.8F, 1.4F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "celestial_alignment", "Égi Együttállás", 300, SpellCostType.XP, 150)
                .aoe(7.0D).damage(8.0D).selfEffect(PotionEffectType.REGENERATION, 10 * 20, 1)
                .particle(Particle.FLASH, 5).sound(Sound.ENTITY_GENERIC_EXPLODE, 0.7F, 1.4F)
                .build());
    }

    // ===== PAPLOVAG (base class) — szent harci mágia: sújtás, gyógyítás, aurák =====
    private static void registerPaladin(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "smite", "Sújtás", 20, SpellCostType.XP, 30)
                .target(14.0D).damage(5.0D)
                .particle(Particle.END_ROD, 20).sound(Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 1.3F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "flash_heal", "Villámgyógyítás", 30, SpellCostType.XP, 40)
                .healSelf(5.0D).particle(Particle.HEART, 15).sound(Sound.BLOCK_BELL_RESONATE, 0.8F, 1.5F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "devotion_aura", "Áhítat Aurája", 60, SpellCostType.HUNGER, 4)
                .selfEffect(PotionEffectType.RESISTANCE, 10 * 20, 0)
                .particle(Particle.END_ROD, 15).sound(Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.0F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "hammer_of_justice", "Igazság Pörölye", 45, SpellCostType.HUNGER, 4)
                .target(8.0D).damage(3.0D).targetEffect(PotionEffectType.SLOWNESS, 4 * 20, 3)
                .particle(Particle.CRIT, 15).sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "consecration", "Megszentelés", 60, SpellCostType.HUNGER, 5)
                .aoe(5.0D).damage(2.0D).ignite(2 * 20)
                .particle(Particle.FLAME, 30).sound(Sound.ITEM_FIRECHARGE_USE, 1.0F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "lay_on_hands", "Kézrátétel", 150, SpellCostType.XP, 70)
                .healSelf(10.0D).selfEffect(PotionEffectType.REGENERATION, 6 * 20, 1)
                .particle(Particle.HEART, 30).sound(Sound.ITEM_TOTEM_USE, 0.6F, 1.4F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "blessing_of_might", "Erő Áldása", 120, SpellCostType.HUNGER, 5)
                .selfEffect(PotionEffectType.STRENGTH, 15 * 20, 0)
                .particle(Particle.END_ROD, 20).sound(Sound.BLOCK_BELL_RESONATE, 0.8F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "holy_wrath", "Szent Harag", 90, SpellCostType.XP, 60)
                .aoe(6.0D).damage(4.0D).knockback(0.8D)
                .particle(Particle.FLASH, 4).sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7F, 1.4F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "divine_protection", "Isteni Védelem", 120, SpellCostType.HUNGER, 6)
                .selfEffect(PotionEffectType.RESISTANCE, 6 * 20, 2).selfEffect(PotionEffectType.FIRE_RESISTANCE, 8 * 20, 0)
                .particle(Particle.END_ROD, 25).sound(Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "radiance", "Sugárzás", 120, SpellCostType.HUNGER, 6)
                .aoe(8.0D).friendly().targetEffect(PotionEffectType.REGENERATION, 6 * 20, 0).healSelf(3.0D)
                .particle(Particle.HEART, 25).sound(Sound.BLOCK_BELL_RESONATE, 0.8F, 1.4F)
                .build());
    }

    // ===== SZENTLÉLEK (paladin spec) — gyógyítás és csapat-áldások =====
    private static void registerHoly(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "holy_light", "Szent Fény", 30, SpellCostType.XP, 45)
                .healSelf(8.0D).selfEffect(PotionEffectType.REGENERATION, 8 * 20, 1)
                .particle(Particle.HEART, 25).sound(Sound.BLOCK_BELL_RESONATE, 0.8F, 1.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "beacon_of_light", "Fény Jelzőtüze", 90, SpellCostType.HUNGER, 6)
                .aoe(8.0D).friendly().targetEffect(PotionEffectType.REGENERATION, 10 * 20, 1).healSelf(4.0D)
                .particle(Particle.END_ROD, 30).sound(Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "blessing_of_kings", "Királyok Áldása", 120, SpellCostType.HUNGER, 6)
                .aoe(8.0D).friendly()
                .targetEffect(PotionEffectType.STRENGTH, 15 * 20, 0).targetEffect(PotionEffectType.RESISTANCE, 15 * 20, 0)
                .particle(Particle.END_ROD, 30).sound(Sound.BLOCK_BELL_RESONATE, 0.8F, 1.3F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "divine_shield", "Isteni Pajzs", 180, SpellCostType.HUNGER, 8)
                .selfEffect(PotionEffectType.RESISTANCE, 8 * 20, 3).selfEffect(PotionEffectType.ABSORPTION, 12 * 20, 2)
                .particle(Particle.TOTEM_OF_UNDYING, 30).sound(Sound.ITEM_TOTEM_USE, 0.7F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "aura_mastery", "Aura-mesterség", 120, SpellCostType.HUNGER, 6)
                .aoe(8.0D).friendly().targetEffect(PotionEffectType.RESISTANCE, 10 * 20, 1)
                .particle(Particle.END_ROD, 40).sound(Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.4F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "guardian_angel", "Őrangyal", 150, SpellCostType.XP, 70)
                .healSelf(6.0D).selfEffect(PotionEffectType.REGENERATION, 10 * 20, 1).selfEffect(PotionEffectType.ABSORPTION, 15 * 20, 1)
                .particle(Particle.HEART, 30).sound(Sound.ITEM_TOTEM_USE, 0.6F, 1.5F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "avenging_wrath", "Megtorló Harag", 300, SpellCostType.XP, 150)
                .aoe(7.0D).friendly()
                .targetEffect(PotionEffectType.REGENERATION, 12 * 20, 1).targetEffect(PotionEffectType.STRENGTH, 12 * 20, 1).healSelf(8.0D)
                .particle(Particle.TOTEM_OF_UNDYING, 60).sound(Sound.ITEM_TOTEM_USE, 0.8F, 1.0F)
                .build());
    }

    // ===== MEGTORLÓ (paladin spec) — szent közelharci/sújtó sebzés =====
    private static void registerRetribution(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "crusader_strike", "Keresztes Csapás", 20, SpellCostType.HUNGER, 3)
                .target(4.5D).damage(6.0D)
                .particle(Particle.CRIT, 15).sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "judgment", "Ítélet", 30, SpellCostType.XP, 40)
                .target(16.0D).damage(6.0D)
                .particle(Particle.END_ROD, 25).sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6F, 1.5F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "blade_of_justice", "Igazság Pengéje", 35, SpellCostType.HUNGER, 4)
                .target(6.0D).damage(7.0D).knockback(0.6D)
                .particle(Particle.CRIT, 20).sound(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.9F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "divine_storm", "Isteni Vihar", 60, SpellCostType.HUNGER, 5)
                .aoe(5.0D).damage(5.0D).knockback(0.5D)
                .particle(Particle.SWEEP_ATTACK, 20).sound(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 1.0F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "holy_fire", "Szent Tűz", 45, SpellCostType.XP, 50)
                .target(14.0D).damage(5.0D).ignite(3 * 20)
                .particle(Particle.FLAME, 25).sound(Sound.ITEM_FIRECHARGE_USE, 1.0F, 1.1F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "wake_of_ashes", "Hamuébresztés", 90, SpellCostType.XP, 60)
                .aoe(5.0D).damage(4.0D).ignite(4 * 20)
                .particle(Particle.FLAME, 40).sound(Sound.ITEM_FIRECHARGE_USE, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "zeal", "Buzgalom", 75, SpellCostType.HUNGER, 5)
                .selfEffect(PotionEffectType.SPEED, 10 * 20, 0).selfEffect(PotionEffectType.STRENGTH, 10 * 20, 1)
                .particle(Particle.END_ROD, 20).sound(Sound.BLOCK_BELL_RESONATE, 0.8F, 1.3F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "final_verdict", "Végső Ítélet", 300, SpellCostType.XP, 150)
                .aoe(6.0D).damage(9.0D).knockback(1.0D)
                .particle(Particle.FLASH, 5).sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7F, 1.5F)
                .build());
    }

    // ===== HALÁLLOVAG (base class) — rúnikus harc, fagy és vér (önfenntartás) =====
    private static void registerDeathKnight(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "death_strike", "Halálcsapás", 20, SpellCostType.HUNGER, 3)
                .target(4.5D).damage(6.0D).healSelf(3.0D)
                .particle(Particle.SOUL, 15).sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "death_coil", "Halálfonál", 30, SpellCostType.XP, 35)
                .target(14.0D).damage(5.0D).targetEffect(PotionEffectType.WITHER, 4 * 20, 0)
                .particle(Particle.SOUL, 25).sound(Sound.ENTITY_WITHER_AMBIENT, 0.7F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "chains_of_ice", "Jéglánc", 30, SpellCostType.HUNGER, 3)
                .target(12.0D).targetEffect(PotionEffectType.SLOWNESS, 5 * 20, 3)
                .particle(Particle.SNOWFLAKE, 25).sound(Sound.BLOCK_GLASS_BREAK, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "blood_boil", "Vérforralás", 45, SpellCostType.HUNGER, 4)
                .aoe(5.0D).damage(3.0D).targetEffect(PotionEffectType.WITHER, 4 * 20, 0)
                .particle(Particle.SQUID_INK, 30).sound(Sound.ENTITY_WITHER_AMBIENT, 0.6F, 0.9F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "bone_shield", "Csontpajzs", 60, SpellCostType.HUNGER, 5)
                .selfEffect(PotionEffectType.RESISTANCE, 10 * 20, 1).selfEffect(PotionEffectType.ABSORPTION, 10 * 20, 0)
                .particle(Particle.ASH, 25).sound(Sound.BLOCK_SOUL_SAND_BREAK, 1.0F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "icy_touch", "Jeges Érintés", 35, SpellCostType.XP, 35)
                .target(10.0D).damage(3.0D).freeze(60).targetEffect(PotionEffectType.SLOWNESS, 3 * 20, 1)
                .particle(Particle.SNOWFLAKE, 20).sound(Sound.BLOCK_POWDER_SNOW_STEP, 1.0F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "rune_strike", "Rúnacsapás", 20, SpellCostType.HUNGER, 3)
                .target(4.5D).damage(7.0D)
                .particle(Particle.CRIT, 15).sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "anti_magic_shell", "Mágiapajzs", 90, SpellCostType.HUNGER, 5)
                .selfEffect(PotionEffectType.RESISTANCE, 6 * 20, 1).selfEffect(PotionEffectType.FIRE_RESISTANCE, 8 * 20, 0)
                .particle(Particle.END_ROD, 25).sound(Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "dark_command", "Sötét Parancs", 60, SpellCostType.HUNGER, 4)
                .aoe(6.0D).targetEffect(PotionEffectType.SLOWNESS, 4 * 20, 1).selfEffect(PotionEffectType.RESISTANCE, 6 * 20, 0)
                .particle(Particle.SQUID_INK, 25).sound(Sound.ENTITY_WITHER_AMBIENT, 0.7F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "frost_fever", "Fagyláz", 45, SpellCostType.XP, 40)
                .target(12.0D).damage(2.0D).freeze(80).targetEffect(PotionEffectType.SLOWNESS, 5 * 20, 2)
                .particle(Particle.SNOWFLAKE, 25).sound(Sound.BLOCK_GLASS_BREAK, 1.0F, 0.5F)
                .build());
    }

    // ===== VÉRLOVAG (death knight spec) — tank/önfenntartás életszívással =====
    private static void registerBlood(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "heart_strike", "Szívcsapás", 20, SpellCostType.HUNGER, 3)
                .target(4.5D).damage(6.0D).healSelf(2.0D)
                .particle(Particle.CRIT, 15).sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "vampiric_blood", "Vámpírvér", 120, SpellCostType.HUNGER, 6)
                .healSelf(6.0D).selfEffect(PotionEffectType.ABSORPTION, 15 * 20, 2).selfEffect(PotionEffectType.REGENERATION, 8 * 20, 1)
                .particle(Particle.SQUID_INK, 30).sound(Sound.ENTITY_WITHER_AMBIENT, 0.7F, 1.0F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "marrowrend", "Velőtörés", 30, SpellCostType.HUNGER, 4)
                .target(4.0D).damage(5.0D).selfEffect(PotionEffectType.ABSORPTION, 12 * 20, 1)
                .particle(Particle.ASH, 15).sound(Sound.BLOCK_SOUL_SAND_BREAK, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "blood_mirror", "Vértükör", 90, SpellCostType.HUNGER, 5)
                .selfEffect(PotionEffectType.RESISTANCE, 8 * 20, 2)
                .particle(Particle.SQUID_INK, 25).sound(Sound.ITEM_SHIELD_BLOCK, 1.0F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "rune_tap", "Rúnacsapolás", 60, SpellCostType.HUNGER, 4)
                .healSelf(5.0D).selfEffect(PotionEffectType.RESISTANCE, 5 * 20, 1)
                .particle(Particle.SOUL, 20).sound(Sound.BLOCK_SOUL_SAND_BREAK, 1.0F, 1.0F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "bonestorm", "Csontvihar", 120, SpellCostType.XP, 70)
                .aoe(5.0D).damage(4.0D).healSelf(4.0D).knockback(0.5D)
                .particle(Particle.ASH, 40).sound(Sound.ENTITY_WITHER_AMBIENT, 0.8F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "dancing_rune_weapon", "Táncoló Rúnafegyver", 300, SpellCostType.XP, 150)
                .aoe(6.0D).damage(8.0D).healSelf(8.0D)
                .selfEffect(PotionEffectType.STRENGTH, 12 * 20, 1).selfEffect(PotionEffectType.RESISTANCE, 12 * 20, 1)
                .particle(Particle.CRIT, 50).sound(Sound.ENTITY_WITHER_SPAWN, 0.5F, 1.4F)
                .build());
    }

    // ===== FAGYLOVAG (death knight spec) — fagyos közelharci sebzés =====
    private static void registerFrost(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "obliterate", "Megsemmisítés", 20, SpellCostType.HUNGER, 3)
                .target(4.5D).damage(8.0D)
                .particle(Particle.CRIT, 20).sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "frost_strike", "Fagycsapás", 25, SpellCostType.HUNGER, 3)
                .target(5.0D).damage(6.0D).freeze(40)
                .particle(Particle.SNOWFLAKE, 20).sound(Sound.BLOCK_GLASS_BREAK, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "howling_blast", "Üvöltő Szél", 45, SpellCostType.XP, 45)
                .aoe(5.0D).damage(4.0D).freeze(80).targetEffect(PotionEffectType.SLOWNESS, 4 * 20, 2)
                .particle(Particle.SNOWFLAKE, 40).sound(Sound.ENTITY_BREEZE_SHOOT, 1.0F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "pillar_of_frost", "Fagyoszlop", 90, SpellCostType.HUNGER, 5)
                .selfEffect(PotionEffectType.STRENGTH, 12 * 20, 1).selfEffect(PotionEffectType.RESISTANCE, 8 * 20, 0)
                .particle(Particle.SNOWFLAKE, 30).sound(Sound.BLOCK_POWDER_SNOW_STEP, 1.0F, 0.5F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "remorseless_winter", "Könyörtelen Tél", 75, SpellCostType.XP, 55)
                .aoe(5.0D).damage(3.0D).freeze(60).targetEffect(PotionEffectType.SLOWNESS, 4 * 20, 1)
                .particle(Particle.SNOWFLAKE, 50).sound(Sound.BLOCK_GLASS_BREAK, 1.0F, 0.5F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "frostscythe", "Fagykasza", 35, SpellCostType.HUNGER, 4)
                .aoe(4.0D).damage(5.0D).freeze(40)
                .particle(Particle.SWEEP_ATTACK, 15).sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "chill_streak", "Dermesztő Suhanás", 60, SpellCostType.XP, 50)
                .target(12.0D).damage(5.0D).freeze(60)
                .particle(Particle.SNOWFLAKE, 25).sound(Sound.ENTITY_BREEZE_SHOOT, 1.0F, 0.9F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "breath_of_sindragosa", "Sindragosa Lehelete", 300, SpellCostType.XP, 150)
                .aoe(6.0D).damage(8.0D).freeze(120).targetEffect(PotionEffectType.SLOWNESS, 6 * 20, 2)
                .particle(Particle.SNOWFLAKE, 80).sound(Sound.ENTITY_BREEZE_SHOOT, 1.0F, 0.4F)
                .build());
    }

    // ===== SÁMÁN (base class) — elemi mágia + totem-aurák =====
    private static void registerShaman(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "lightning_bolt", "Villámnyíl", 20, SpellCostType.XP, 30)
                .target(16.0D).damage(5.0D).lightning()
                .particle(Particle.ELECTRIC_SPARK, 25).sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6F, 1.4F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "healing_surge", "Gyógyár", 30, SpellCostType.XP, 40)
                .healSelf(6.0D).particle(Particle.HEART, 15).sound(Sound.BLOCK_BELL_RESONATE, 0.8F, 1.5F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "earth_shock", "Földrengés", 30, SpellCostType.HUNGER, 3)
                .target(10.0D).damage(5.0D).knockback(0.6D)
                .particle(Particle.CRIT, 20).sound(Sound.BLOCK_STONE_PLACE, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "flame_shock", "Lángrengés", 30, SpellCostType.XP, 35)
                .target(12.0D).damage(4.0D).ignite(4 * 20)
                .particle(Particle.FLAME, 20).sound(Sound.ITEM_FIRECHARGE_USE, 1.0F, 1.1F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "frost_shock", "Fagyrengés", 30, SpellCostType.XP, 35)
                .target(12.0D).damage(4.0D).freeze(60).targetEffect(PotionEffectType.SLOWNESS, 4 * 20, 2)
                .particle(Particle.SNOWFLAKE, 20).sound(Sound.BLOCK_GLASS_BREAK, 1.0F, 0.8F)
                .build());
        // healing_stream_totem and searing_totem are now PERSISTENT totems (ShamanTotemSpell,
        // registered in IceSMPCore.registerSpells with the TotemManager) — not instant auras.
        registry.register(ConfiguredSpell.builder(mm, "lightning_shield", "Villámpajzs", 90, SpellCostType.HUNGER, 5)
                .selfEffect(PotionEffectType.RESISTANCE, 8 * 20, 0)
                .particle(Particle.ELECTRIC_SPARK, 20).sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5F, 1.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "ancestral_spirit", "Ősi Szellem", 150, SpellCostType.XP, 70)
                .healSelf(8.0D).selfEffect(PotionEffectType.REGENERATION, 8 * 20, 1)
                .particle(Particle.END_ROD, 25).sound(Sound.BLOCK_BEACON_ACTIVATE, 0.7F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "wind_rush", "Szélroham", 60, SpellCostType.HUNGER, 4)
                .selfEffect(PotionEffectType.SPEED, 8 * 20, 1)
                .particle(Particle.CLOUD, 20).sound(Sound.ENTITY_BREEZE_SHOOT, 1.0F, 1.0F)
                .build());
    }

    // ===== ELEMI (shaman spec) — távolsági elemi tüzérség =====
    private static void registerElemental(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "lava_burst", "Lávakitörés", 25, SpellCostType.XP, 40)
                .target(16.0D).damage(7.0D).ignite(4 * 20)
                .particle(Particle.FLAME, 30).sound(Sound.ITEM_FIRECHARGE_USE, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "chain_lightning", "Láncvillám", 35, SpellCostType.XP, 45)
                .aoe(6.0D).damage(5.0D).lightning()
                .particle(Particle.ELECTRIC_SPARK, 30).sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7F, 1.3F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "earthquake", "Földmoraj", 60, SpellCostType.XP, 55)
                .aoe(6.0D).damage(4.0D).knockback(0.6D)
                .particle(Particle.ASH, 40).sound(Sound.BLOCK_STONE_PLACE, 1.0F, 0.5F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "thunderstorm", "Mennydörgés", 90, SpellCostType.XP, 60)
                .aoe(6.0D).damage(4.0D).knockback(1.0D).lightning()
                .particle(Particle.ELECTRIC_SPARK, 40).sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8F, 1.0F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "elemental_blast", "Elemi Robbanás", 45, SpellCostType.XP, 50)
                .target(14.0D).damage(6.0D).freeze(40).ignite(2 * 20)
                .particle(Particle.FLASH, 4).sound(Sound.ENTITY_GENERIC_EXPLODE, 0.6F, 1.4F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "stormkeeper", "Viharőrző", 75, SpellCostType.HUNGER, 5)
                .selfEffect(PotionEffectType.STRENGTH, 10 * 20, 0)
                .particle(Particle.ELECTRIC_SPARK, 30).sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6F, 1.5F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "ascendance_flame", "Felemelkedés", 300, SpellCostType.XP, 150)
                .aoe(7.0D).damage(8.0D).lightning().ignite(3 * 20)
                .particle(Particle.FLASH, 5).sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8F, 1.2F)
                .build());
    }

    // ===== ERŐSÍTŐ (shaman spec) — elemekkel feltöltött közelharc =====
    private static void registerEnhancement(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "stormstrike", "Viharcsapás", 20, SpellCostType.HUNGER, 3)
                .target(4.5D).damage(7.0D)
                .particle(Particle.ELECTRIC_SPARK, 15).sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "lava_lash", "Lávacsapás", 25, SpellCostType.HUNGER, 3)
                .target(4.5D).damage(6.0D).ignite(3 * 20)
                .particle(Particle.FLAME, 15).sound(Sound.ITEM_FIRECHARGE_USE, 1.0F, 0.9F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "windfury", "Szélharag", 75, SpellCostType.HUNGER, 5)
                .selfEffect(PotionEffectType.SPEED, 10 * 20, 1).selfEffect(PotionEffectType.STRENGTH, 10 * 20, 0)
                .particle(Particle.CLOUD, 20).sound(Sound.ENTITY_BREEZE_SHOOT, 1.0F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "feral_spirit", "Vad Szellem", 90, SpellCostType.HUNGER, 5)
                .selfEffect(PotionEffectType.SPEED, 12 * 20, 1).selfEffect(PotionEffectType.STRENGTH, 12 * 20, 0)
                .particle(Particle.SOUL, 20).sound(Sound.ENTITY_RAVAGER_ROAR, 0.8F, 1.3F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "crash_lightning", "Robbanó Villám", 45, SpellCostType.XP, 45)
                .aoe(5.0D).damage(5.0D).lightning()
                .particle(Particle.ELECTRIC_SPARK, 30).sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7F, 1.1F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "sundering", "Hasítás", 60, SpellCostType.HUNGER, 5)
                .target(5.0D).damage(7.0D).knockback(1.0D)
                .particle(Particle.ASH, 20).sound(Sound.BLOCK_STONE_PLACE, 1.0F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "earthen_weapon", "Földi Fegyver", 90, SpellCostType.HUNGER, 5)
                .selfEffect(PotionEffectType.STRENGTH, 12 * 20, 1).selfEffect(PotionEffectType.RESISTANCE, 8 * 20, 0)
                .particle(Particle.ASH, 20).sound(Sound.BLOCK_STONE_PLACE, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "doom_winds", "Végzetszél", 300, SpellCostType.XP, 150)
                .aoe(6.0D).damage(8.0D).lightning()
                .selfEffect(PotionEffectType.SPEED, 12 * 20, 1).selfEffect(PotionEffectType.STRENGTH, 12 * 20, 1)
                .particle(Particle.ELECTRIC_SPARK, 50).sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8F, 0.9F)
                .build());
    }

    // ===== SZERZETES (base class) — harcművészet, mozgékonyság, csi =====
    private static void registerMonk(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "tiger_palm", "Tigristenyér", 15, SpellCostType.HUNGER, 2)
                .target(4.5D).damage(5.0D)
                .particle(Particle.CRIT, 12).sound(Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0F, 1.1F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "roll", "Gördülés", 20, SpellCostType.HUNGER, 2)
                .dash(1.8D, 0.2D).selfEffect(PotionEffectType.SPEED, 3 * 20, 0)
                .particle(Particle.CLOUD, 12).sound(Sound.ENTITY_PHANTOM_FLAP, 1.0F, 1.3F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "blackout_kick", "Sötét Rúgás", 20, SpellCostType.HUNGER, 3)
                .target(4.0D).damage(6.0D).knockback(0.5D)
                .particle(Particle.SWEEP_ATTACK, 10).sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.9F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "provoke", "Provokálás", 45, SpellCostType.HUNGER, 3)
                .aoe(6.0D).targetEffect(PotionEffectType.SLOWNESS, 3 * 20, 1).selfEffect(PotionEffectType.RESISTANCE, 5 * 20, 0)
                .particle(Particle.ANGRY_VILLAGER, 12).sound(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "vivify", "Élénkítés", 30, SpellCostType.XP, 40)
                .healSelf(5.0D).particle(Particle.HEART, 15).sound(Sound.BLOCK_BELL_RESONATE, 0.8F, 1.5F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "spinning_crane_kick", "Pörgő Darurúgás", 30, SpellCostType.HUNGER, 4)
                .aoe(4.0D).damage(4.0D)
                .particle(Particle.SWEEP_ATTACK, 18).sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 1.1F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "flying_serpent_kick", "Repülő Kígyórúgás", 35, SpellCostType.HUNGER, 3)
                .dash(2.0D, 0.4D).selfEffect(PotionEffectType.SPEED, 3 * 20, 0)
                .particle(Particle.CLOUD, 15).sound(Sound.ENTITY_BREEZE_SHOOT, 1.0F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "expel_harm", "Ártalom Kiűzése", 45, SpellCostType.XP, 45)
                .aoe(4.0D).damage(2.0D).healSelf(4.0D)
                .particle(Particle.HEART, 15).sound(Sound.BLOCK_BELL_RESONATE, 0.7F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "leg_sweep", "Lábseprés", 60, SpellCostType.HUNGER, 4)
                .aoe(5.0D).targetEffect(PotionEffectType.SLOWNESS, 3 * 20, 3).knockback(0.4D)
                .particle(Particle.SWEEP_ATTACK, 15).sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "chi_wave", "Csi Hullám", 30, SpellCostType.XP, 35)
                .target(12.0D).damage(3.0D).healSelf(3.0D)
                .particle(Particle.END_ROD, 20).sound(Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.4F)
                .build());
    }

    // ===== SZÉLFUTÓ (monk spec) — gyors, kombó-alapú közelharci sebzés =====
    private static void registerWindwalker(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "rising_sun_kick", "Felkelő Nap Rúgás", 20, SpellCostType.HUNGER, 3)
                .target(5.0D).damage(7.0D)
                .particle(Particle.CRIT, 15).sound(Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0F, 1.0F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "fists_of_fury", "Düh Ökle", 45, SpellCostType.HUNGER, 5)
                .aoe(5.0D).damage(6.0D).targetEffect(PotionEffectType.SLOWNESS, 3 * 20, 2)
                .particle(Particle.SWEEP_ATTACK, 25).sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "whirling_dragon_punch", "Örvénylő Sárkányütés", 60, SpellCostType.HUNGER, 5)
                .aoe(5.0D).damage(6.0D).knockback(0.6D)
                .particle(Particle.SWEEP_ATTACK, 20).sound(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 1.1F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "touch_of_death", "Halál Érintése", 90, SpellCostType.XP, 60)
                .target(5.0D).damage(9.0D)
                .particle(Particle.SOUL, 20).sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "storm_earth_fire", "Vihar, Föld, Tűz", 90, SpellCostType.HUNGER, 6)
                .selfEffect(PotionEffectType.STRENGTH, 12 * 20, 1).selfEffect(PotionEffectType.SPEED, 12 * 20, 0)
                .particle(Particle.FLAME, 25).sound(Sound.ITEM_FIRECHARGE_USE, 1.0F, 1.0F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "flying_kick", "Repülő Rúgás", 35, SpellCostType.HUNGER, 3)
                .dash(2.0D, 0.4D).selfEffect(PotionEffectType.SPEED, 4 * 20, 0)
                .particle(Particle.CLOUD, 15).sound(Sound.ENTITY_BREEZE_SHOOT, 1.0F, 1.1F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "serenity", "Derű", 300, SpellCostType.XP, 150)
                .aoe(6.0D).damage(9.0D)
                .selfEffect(PotionEffectType.SPEED, 12 * 20, 1).selfEffect(PotionEffectType.STRENGTH, 12 * 20, 1)
                .particle(Particle.END_ROD, 50).sound(Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.0F)
                .build());
    }

    // ===== SÖRFŐZŐ (monk spec) — sör-alapú tankolás és területi kontroll =====
    private static void registerBrewmaster(final SpellRegistry registry, final MessageManager mm) {
        registry.register(ConfiguredSpell.builder(mm, "keg_smash", "Hordócsapás", 25, SpellCostType.HUNGER, 4)
                .aoe(5.0D).damage(4.0D).targetEffect(PotionEffectType.SLOWNESS, 4 * 20, 1)
                .particle(Particle.SWEEP_ATTACK, 20).sound(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.7F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "breath_of_fire", "Tűz Lehelete", 45, SpellCostType.XP, 45)
                .aoe(5.0D).damage(3.0D).ignite(3 * 20)
                .particle(Particle.FLAME, 30).sound(Sound.ITEM_FIRECHARGE_USE, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "purifying_brew", "Tisztító Sör", 45, SpellCostType.XP, 40)
                .healSelf(5.0D).selfEffect(PotionEffectType.RESISTANCE, 6 * 20, 1)
                .particle(Particle.HEART, 15).sound(Sound.BLOCK_BREWING_STAND_BREW, 1.0F, 1.0F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "celestial_brew", "Égi Sör", 120, SpellCostType.HUNGER, 6)
                .selfEffect(PotionEffectType.ABSORPTION, 15 * 20, 2).selfEffect(PotionEffectType.RESISTANCE, 8 * 20, 0)
                .particle(Particle.END_ROD, 25).sound(Sound.BLOCK_BREWING_STAND_BREW, 1.0F, 1.2F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "fortifying_brew", "Erősítő Sör", 120, SpellCostType.HUNGER, 6)
                .selfEffect(PotionEffectType.RESISTANCE, 10 * 20, 2).selfEffect(PotionEffectType.REGENERATION, 8 * 20, 1)
                .particle(Particle.HEART, 20).sound(Sound.BLOCK_BREWING_STAND_BREW, 1.0F, 0.8F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "clash", "Összecsapás", 75, SpellCostType.HUNGER, 5)
                .aoe(6.0D).knockback(0.8D).targetEffect(PotionEffectType.SLOWNESS, 3 * 20, 2)
                .particle(Particle.SWEEP_ATTACK, 20).sound(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.6F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "ironskin_brew", "Vasbőr Sör", 90, SpellCostType.HUNGER, 5)
                .selfEffect(PotionEffectType.RESISTANCE, 8 * 20, 1).selfEffect(PotionEffectType.ABSORPTION, 10 * 20, 0)
                .particle(Particle.ASH, 20).sound(Sound.BLOCK_BREWING_STAND_BREW, 1.0F, 1.1F)
                .build());
        registry.register(ConfiguredSpell.builder(mm, "invoke_niuzao", "Niuzao Megidézése", 300, SpellCostType.XP, 150)
                .aoe(6.0D).damage(7.0D)
                .selfEffect(PotionEffectType.RESISTANCE, 12 * 20, 2).selfEffect(PotionEffectType.ABSORPTION, 15 * 20, 2)
                .particle(Particle.CRIT, 50).sound(Sound.ENTITY_RAVAGER_ROAR, 0.8F, 0.7F)
                .build());
    }
}
