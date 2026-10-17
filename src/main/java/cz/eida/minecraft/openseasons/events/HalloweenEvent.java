/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import cz.eida.minecraft.openseasons.effects.EffectRules;
import cz.eida.minecraft.openseasons.effects.ParticleViewers;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spider;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Vindicator;
import org.bukkit.entity.Witch;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Halloween: naturally spawned monsters get Halloween changes (twin,
 * invisibility, speed, burning skeleton arrows, armour, a pumpkin pie in a zombie's hand, at night a vindicator
 * instead of the monster), witches throw extra potions with bad effects and flames flicker around changed
 * monsters. In addition: carved pumpkins and jack o'lanterns on heads, more
 * bats and spiders. The changes apply only to natural spawns ({@code NATURAL}), not to spawners.
 * The helmet changes only on an empty head (autumn pumpkins of other OpenSeasons code stay).
 */
final class HalloweenEvent extends BuiltinEvent {

    /** monster with a Halloween change: flames around it */
    static final String TAG_MOB = "mc215os_halloween";
    /** skeleton whose arrows burn */
    static final String TAG_FLAME = "mc215os_halloween_flame";
    /** monster added by Halloween (twin, vindicator, spider, bat): vanishes at the end */
    static final String TAG_EXTRA = "mc215os_halloween_extra";

    static final int PARTICLE_PERIOD = 7;
    static final double PARTICLE_RADIUS = 14;
    static final int BAT_PERIOD = 200;
    static final int BATS_NEAR = 6;

    /** old effect names → registry key */
    static final Map<String, String> LEGACY_EFFECTS = Map.ofEntries(
            Map.entry("CONFUSION", "nausea"),
            Map.entry("SLOW_DIGGING", "mining_fatigue"),
            Map.entry("FAST_DIGGING", "haste"),
            Map.entry("SLOW", "slowness"),
            Map.entry("INCREASE_DAMAGE", "strength"),
            Map.entry("HEAL", "instant_health"),
            Map.entry("HARM", "instant_damage"),
            Map.entry("JUMP", "jump_boost"),
            Map.entry("DAMAGE_RESISTANCE", "resistance"));

    private static final Color[] HALLOWEEN_LEATHER = {Color.fromRGB(0xE8751A), Color.fromRGB(0x1E1B1B),
            Color.fromRGB(0x5B2A86)};

    HalloweenEvent(Plugin plugin, Supplier<EventSettings> settings, ParticleViewers viewers, EventPlaces places) {
        super(plugin, settings, viewers, places);
    }

    @Override
    SeasonEvent.Type type() {
        return SeasonEvent.Type.HALLOWEEN;
    }

    /** end: added monsters vanish (done by {@link BuiltinEvent#end}), changed ones lose their effects and flames */
    @Override
    protected void stopped(World world) {
        for (LivingEntity e : world.getLivingEntities()) {
            if (!e.getScoreboardTags().contains(TAG_MOB)) continue;
            e.removeScoreboardTag(TAG_MOB);
            e.removeScoreboardTag(TAG_FLAME);
            for (PotionEffectType t : List.of(PotionEffectType.INVISIBILITY, PotionEffectType.SPEED)) {
                PotionEffect pe = e.getPotionEffect(t);
                if (pe != null && pe.isInfinite()) e.removePotionEffect(t);
            }
        }
    }

    // ---------------------------------------------------------------- spawny

    /**
     * At {@code NORMAL}, i.e. before autumn pumpkins and replacements of seasonal monsters ({@code entity.SeasonalMobs}
     * at {@code HIGH}): the Halloween pumpkin takes precedence, the autumn one is only added to an empty head.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        LivingEntity entity = event.getEntity();
        if (!runningAt(entity.getLocation()) || !(entity instanceof Mob) || !(entity instanceof Enemy)) return;
        EventSettings.Halloween h = settings.get().halloween();
        World world = entity.getWorld();
        Location at = entity.getLocation();
        if (!mobChangesAt(at)) return;

        if (!(entity instanceof Vindicator) && EffectRules.night(world.getTime()) && onSurface(at)
                && roll(h.vindicatorsAtNight())) {
            event.setCancelled(true);
            Vindicator v = world.spawn(at, Vindicator.class, CreatureSpawnEvent.SpawnReason.CUSTOM, false, m -> {
                extra(m);
                mutate(m, h, false);
            });
            v.setRemoveWhenFarAway(true);
            return;
        }
        boolean mutated = mutate(entity, h, true);
        if (!(entity instanceof Spider) && onSurface(at) && roll(h.moreSpiders())) {
            Location side = at.clone().add(random.nextDouble() * 2 - 1, 0, random.nextDouble() * 2 - 1);
            world.spawn(side, Spider.class, CreatureSpawnEvent.SpawnReason.CUSTOM, false, s -> {
                extra(s);
                s.setRemoveWhenFarAway(true);
                s.addScoreboardTag(TAG_MOB);
            });
        }
        if (mutated && roll(h.duplicating())) twin(entity);
    }

    /** twin one tick later, when the original has its equipment and effects (a copy of the entity with everything) */
    private void twin(LivingEntity original) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!original.isValid()) return;
            Entity copy = original.copy(original.getLocation().add(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5));
            extra(copy);
        });
    }

    /**
     * Halloween changes of a monster.
     *
     * @param natural natural spawn (otherwise a vindicator or manual triggering), the caller handles the twin
     * @return it got at least one change (then flames flicker around it)
     */
    boolean mutate(LivingEntity mob, EventSettings.Halloween h, boolean natural) {
        boolean changed = false;
        EntityEquipment eq = mob.getEquipment();
        boolean humanoid = mob instanceof Zombie || mob instanceof AbstractSkeleton;
        if (eq != null && humanoid) {
            if (roll(h.pumpkinHeads()) && empty(eq.getHelmet())) {
                eq.setHelmet(new ItemStack(random.nextBoolean() ? Material.JACK_O_LANTERN : Material.CARVED_PUMPKIN));
                eq.setHelmetDropChance(0f);
                changed = true;
            }
            if (roll(h.armored())) {
                changed |= armor(eq);
            }
            if (mob instanceof Zombie && !(mob instanceof Drowned) && roll(h.zombiePie()) && empty(eq.getItemInMainHand())) {
                eq.setItemInMainHand(new ItemStack(Material.PUMPKIN_PIE));
                eq.setItemInMainHandDropChance(0.5f);
                changed = true;
            }
        }
        if (mob instanceof AbstractSkeleton && roll(h.flameSkeletons())) {
            mob.addScoreboardTag(TAG_FLAME);
            changed = true;
        }
        if (roll(h.invisible())) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, true, false));
            changed = true;
        }
        if (roll(h.fast())) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, true, false));
            changed = true;
        }
        if (changed || !natural) mob.addScoreboardTag(TAG_MOB);
        return changed;
    }

    /** a piece of armour into empty slots: leather in Halloween colours, chainmail, iron or gold */
    private boolean armor(EntityEquipment eq) {
        String[] tiers = {"LEATHER", "CHAINMAIL", "IRON", "GOLDEN"};
        String tier = tiers[random.nextInt(tiers.length)];
        boolean any = false;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        String[] names = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
        for (int i = 0; i < slots.length; i++) {
            if (random.nextInt(10) >= 6 || !empty(eq.getItem(slots[i]))) continue;
            Material m = Material.matchMaterial(tier + "_" + names[i]);
            if (m == null) continue;
            ItemStack piece = new ItemStack(m);
            if (piece.getItemMeta() instanceof LeatherArmorMeta meta) {
                meta.setColor(HALLOWEEN_LEATHER[random.nextInt(HALLOWEEN_LEATHER.length)]);
                piece.setItemMeta(meta);
            }
            eq.setItem(slots[i], piece);
            eq.setDropChance(slots[i], 0.05f);
            any = true;
        }
        return any;
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private static boolean onSurface(Location at) {
        return at.getBlockY() >= at.getWorld().getHighestBlockYAt(at) - 1;
    }

    private void extra(Entity e) {
        EventEntities.mark(e, type());
        e.addScoreboardTag(TAG_EXTRA);
    }

    private boolean roll(EventSettings.Chance c) {
        return c.enabled() && random.nextDouble() < c.chance();
    }

    // ---------------------------------------------------------------- arrows and potions

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!runningAt(event.getEntity().getLocation())) return;
        Object shooter = event.getEntity().getShooter();
        if (event.getEntity() instanceof AbstractArrow arrow && shooter instanceof AbstractSkeleton s
                && s.getScoreboardTags().contains(TAG_FLAME)) {
            arrow.setFireTicks(200);
            return;
        }
        EventSettings.Halloween h = settings.get().halloween();
        if (h.witchPotions() && event.getEntity() instanceof ThrownPotion potion && shooter instanceof Witch witch
                && witch.getTarget() instanceof Player && random.nextBoolean()) {
            effect(h.witchEffects()).ifPresent(type -> {
                ItemStack item = potion.getItem();
                if (!(item.getItemMeta() instanceof PotionMeta meta)) return;
                int duration = type.isInstant() ? 1 : type == PotionEffectType.LEVITATION ? 60 + random.nextInt(40)
                        : 100 + random.nextInt(100);
                meta.addCustomEffect(new PotionEffect(type, duration, 0), true);
                meta.setColor(Color.fromRGB(0x5B2A86));
                item.setItemMeta(meta);
                potion.setItem(item);
            });
        }
    }

    /** random effect from the list (also old names like CONFUSION) */
    private Optional<PotionEffectType> effect(List<String> names) {
        if (names.isEmpty()) return Optional.empty();
        String name = names.get(random.nextInt(names.size())).trim();
        return Optional.ofNullable(effectType(name));
    }

    static PotionEffectType effectType(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        String key = LEGACY_EFFECTS.getOrDefault(upper, upper.toLowerCase(Locale.ROOT));
        if (key.startsWith("minecraft:")) key = key.substring("minecraft:".length());
        return Registry.MOB_EFFECT.get(NamespacedKey.minecraft(key));
    }

    /** effect names not in the registry (configuration check after loading) */
    static List<String> unknownEffects(List<String> names) {
        List<String> out = new ArrayList<>();
        for (String n : names) if (effectType(n) == null) out.add(n);
        return out;
    }

    // ---------------------------------------------------------------- particles and bats

    @Override
    void tick(long tick) {
        EventSettings.Halloween h = settings.get().halloween();
        for (String name : worlds()) {
            World world = plugin.getServer().getWorld(name);
            if (world == null) continue;
            if (h.mobParticles() && tick % PARTICLE_PERIOD == 0) flames(world);
            if (tick % BAT_PERIOD == 0 && EffectRules.night(world.getTime())) bats(world, h.moreBats());
        }
    }

    private void flames(World world) {
        for (Player p : viewers.players(world)) {
            for (Entity e : p.getNearbyEntities(PARTICLE_RADIUS, PARTICLE_RADIUS, PARTICLE_RADIUS)) {
                if (!e.getScoreboardTags().contains(TAG_MOB) || !(e instanceof LivingEntity le)) continue;
                Location at = le.getLocation();
                if (!effectsAt(at)) continue;
                Particle particle = random.nextInt(3) == 0 ? Particle.SOUL_FIRE_FLAME : Particle.FLAME;
                p.spawnParticle(particle, at.getX(), at.getY() + le.getHeight() * 0.6, at.getZ(), 2,
                        le.getWidth() * 0.4, le.getHeight() * 0.3, le.getWidth() * 0.4, 0.005);
            }
        }
    }

    private void bats(World world, EventSettings.Chance chance) {
        for (Player p : world.getPlayers()) {
            if (!roll(chance) || !mobChangesAt(p.getLocation())) continue;
            long near = p.getNearbyEntities(32, 32, 32).stream()
                    .filter(e -> e instanceof Bat && e.getScoreboardTags().contains(TAG_EXTRA)).count();
            if (near >= BATS_NEAR) continue;
            spawnBats(p.getLocation(), 1 + random.nextInt(3));
        }
    }

    /** a flock of bats in the air a short way from the place; returns the count */
    int spawnBats(Location near, int count) {
        World world = near.getWorld();
        double[] xz = EventEntities.around(random, near.getX(), near.getZ(), 8, 20);
        Optional<Location> ground = EventEntities.ground(world, xz[0], near.getY(), xz[1]);
        if (ground.isEmpty()) return 0;
        int n = 0;
        for (int i = 0; i < count; i++) {
            Location at = ground.get().clone().add(random.nextDouble() * 2 - 1, 3 + random.nextDouble() * 4,
                    random.nextDouble() * 2 - 1);
            if (!at.getBlock().isPassable()) continue;
            world.spawn(at, Bat.class, CreatureSpawnEvent.SpawnReason.CUSTOM, false, b -> {
                extra(b);
                b.setAwake(true);
                b.setRemoveWhenFarAway(true);
            });
            n++;
        }
        return n;
    }

    /** manual triggering: a zombie with all visible changes (and a twin) a short way ahead of the place */
    Optional<Location> debugMob(Location near) {
        Optional<Location> g = EventEntities.groundAhead(near, 4);
        if (g.isEmpty()) return g;
        EventSettings.Halloween all = allOn(settings.get().halloween());
        Zombie zombie = near.getWorld().spawn(g.get(), Zombie.class, CreatureSpawnEvent.SpawnReason.CUSTOM, false, z -> {
            extra(z);
            z.setRemoveWhenFarAway(true);
            mutate(z, new EventSettings.Halloween(all.mobParticles(), all.witchPotions(), all.witchEffects(),
                    all.duplicating(), EventSettings.Chance.OFF, EventSettings.Chance.OFF, all.flameSkeletons(),
                    all.armored(), all.zombiePie(), all.vindicatorsAtNight(), all.pumpkinHeads(), all.moreBats(),
                    all.moreSpiders()), false);
        });
        twin(zombie);
        return g;
    }

    private static EventSettings.Halloween allOn(EventSettings.Halloween h) {
        EventSettings.Chance on = new EventSettings.Chance(true, 1.0);
        return new EventSettings.Halloween(h.mobParticles(), h.witchPotions(), h.witchEffects(), on, on, on, on, on, on,
                on, on, on, on);
    }
}
