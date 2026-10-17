/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import cz.eida.minecraft.openseasons.effects.ParticleViewers;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Presents (Christmas) and eggs (Easter): a model made of several block displays and an invisible
 * {@link Interaction} entity that can be clicked (right and left button). Nothing is written to the world
 * (presents and eggs are entities, not blocks). Opening gives the player one
 * random loot item and the model vanishes. The entities are temporary: they vanish when the chunk unloads,
 * on restart and at the end of the event.
 */
final class Pickups implements Listener {

    enum Kind {
        PRESENT(SeasonEvent.Type.CHRISTMAS, 0.6f, 0.5f),
        EGG(SeasonEvent.Type.EASTER, 0.45f, 0.45f);

        final SeasonEvent.Type event;
        final float width;
        final float height;

        Kind(SeasonEvent.Type event, float width, float height) {
            this.event = event;
            this.width = width;
            this.height = height;
        }
    }

    record Pickup(Kind kind, Location at, Interaction hitbox, List<Display> parts) {
        boolean valid() {
            return hitbox.isValid();
        }

        void remove() {
            hitbox.remove();
            parts.forEach(Entity::remove);
        }
    }

    /** paper and ribbon colour of a present */
    private static final Material[][] PRESENT_COLORS = {
            {Material.RED_WOOL, Material.YELLOW_WOOL},
            {Material.GREEN_WOOL, Material.RED_WOOL},
            {Material.BLUE_WOOL, Material.WHITE_WOOL},
            {Material.WHITE_WOOL, Material.RED_WOOL},
            {Material.PURPLE_WOOL, Material.YELLOW_WOOL},
            {Material.LIGHT_BLUE_WOOL, Material.WHITE_WOOL}};

    /** colour of an egg and its stripe */
    private static final Material[][] EGG_COLORS = {
            {Material.PINK_CONCRETE, Material.WHITE_CONCRETE},
            {Material.LIGHT_BLUE_CONCRETE, Material.YELLOW_CONCRETE},
            {Material.LIME_CONCRETE, Material.MAGENTA_CONCRETE},
            {Material.YELLOW_CONCRETE, Material.LIGHT_BLUE_CONCRETE},
            {Material.MAGENTA_CONCRETE, Material.WHITE_CONCRETE},
            {Material.ORANGE_CONCRETE, Material.LIME_CONCRETE},
            {Material.CYAN_CONCRETE, Material.PINK_CONCRETE}};

    private final ParticleViewers viewers;
    private final Supplier<EventSettings> settings;
    private final RandomGenerator random = RandomGenerator.getDefault();
    private final Map<UUID, Pickup> byHitbox = new HashMap<>();

    Pickups(ParticleViewers viewers, Supplier<EventSettings> settings) {
        this.viewers = viewers;
        this.settings = settings;
    }

    /** present or egg on the ground; {@code ground} is the centre of the top face of the block */
    Pickup spawn(Kind kind, Location ground) {
        World world = ground.getWorld();
        Location at = ground.clone();
        at.setYaw(random.nextFloat() * 360f);
        at.setPitch(0f);
        List<Display> parts = new ArrayList<>();
        if (kind == Kind.PRESENT) {
            Material[] c = PRESENT_COLORS[random.nextInt(PRESENT_COLORS.length)];
            parts.add(part(world, at, kind, c[0], -0.22f, 0f, -0.22f, 0.44f, 0.36f, 0.44f));
            parts.add(part(world, at, kind, c[0], -0.245f, 0.34f, -0.245f, 0.49f, 0.1f, 0.49f));
            parts.add(part(world, at, kind, c[1], -0.25f, 0.002f, -0.045f, 0.5f, 0.448f, 0.09f));
            parts.add(part(world, at, kind, c[1], -0.045f, 0.002f, -0.25f, 0.09f, 0.448f, 0.5f));
            parts.add(part(world, at, kind, c[1], -0.08f, 0.44f, -0.08f, 0.16f, 0.08f, 0.16f));
        } else {
            Material[] c = EGG_COLORS[random.nextInt(EGG_COLORS.length)];
            parts.add(part(world, at, kind, c[0], -0.13f, 0f, -0.13f, 0.26f, 0.1f, 0.26f));
            parts.add(part(world, at, kind, c[0], -0.16f, 0.07f, -0.16f, 0.32f, 0.17f, 0.32f));
            parts.add(part(world, at, kind, c[1], -0.165f, 0.15f, -0.165f, 0.33f, 0.06f, 0.33f));
            parts.add(part(world, at, kind, c[0], -0.135f, 0.24f, -0.135f, 0.27f, 0.1f, 0.27f));
            parts.add(part(world, at, kind, c[0], -0.09f, 0.33f, -0.09f, 0.18f, 0.07f, 0.18f));
        }
        Location hitAt = ground.clone();
        hitAt.setYaw(0f);
        Interaction hitbox = world.spawn(hitAt, Interaction.class, i -> {
            EventEntities.mark(i, kind.event);
            i.setInteractionWidth(kind.width);
            i.setInteractionHeight(kind.height);
            i.setResponsive(true);
        });
        Pickup p = new Pickup(kind, ground.clone(), hitbox, List.copyOf(parts));
        byHitbox.put(hitbox.getUniqueId(), p);
        return p;
    }

    private static BlockDisplay part(World world, Location at, Kind kind, Material block,
                                     float x, float y, float z, float sx, float sy, float sz) {
        return world.spawn(at, BlockDisplay.class, d -> {
            EventEntities.display(d, kind.event, 1.0f);
            d.setBlock(block.createBlockData());
            d.setBrightness(new Display.Brightness(9, 15));
            d.setTransformation(new Transformation(new Vector3f(x, y, z), new Quaternionf(),
                    new Vector3f(sx, sy, sz), new Quaternionf()));
        });
    }

    /** live presents or eggs in the world */
    List<Pickup> live(World world, Kind kind) {
        prune();
        List<Pickup> out = new ArrayList<>();
        for (Pickup p : byHitbox.values()) {
            if (p.kind() == kind && p.at().getWorld().equals(world)) out.add(p);
        }
        return out;
    }

    void removeAll(World world, Kind kind) {
        Iterator<Pickup> it = byHitbox.values().iterator();
        while (it.hasNext()) {
            Pickup p = it.next();
            if (p.kind() == kind && p.at().getWorld().equals(world)) {
                p.remove();
                it.remove();
            }
        }
    }

    void removeAll() {
        byHitbox.values().forEach(Pickup::remove);
        byHitbox.clear();
    }

    /** every 2 s a little sparkle above presents and eggs with someone within 24 blocks */
    void tick(long tick) {
        if (tick % 40 != 0) return;
        prune();
        for (Pickup p : byHitbox.values()) {
            Location at = p.at();
            boolean near = false;
            for (Player player : at.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(at) < 24 * 24) {
                    near = true;
                    break;
                }
            }
            if (!near) continue;
            Particle particle = p.kind() == Kind.PRESENT ? Particle.FIREWORK : Particle.HAPPY_VILLAGER;
            viewers.spawn(at.getWorld(), particle, at.getX(), at.getY() + 0.7, at.getZ(), 2,
                    0.15, 0.1, 0.15, 0.0, null, false);
        }
    }

    private void prune() {
        Iterator<Pickup> it = byHitbox.values().iterator();
        while (it.hasNext()) {
            Pickup p = it.next();
            if (!p.valid()) {
                p.remove();
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        Pickup p = byHitbox.get(event.getRightClicked().getUniqueId());
        if (p == null) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        open(event.getPlayer(), p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Pickup p = byHitbox.get(event.getAttacked().getUniqueId());
        if (p == null) return;
        event.setCancelled(true);
        open(event.getPlayer(), p);
    }

    /** a player opened a present or egg: one random loot item, the model vanishes */
    void open(Player player, Pickup p) {
        if (byHitbox.remove(p.hitbox().getUniqueId()) == null) return;
        p.remove();
        EventSettings s = settings.get();
        LootTable loot = p.kind() == Kind.PRESENT ? s.christmas().presentLoot() : s.easter().eggLoot();
        Location at = p.at();
        World world = at.getWorld();
        String what = p.kind() == Kind.PRESENT ? "Present" : "Egg";
        Optional<ItemStack> item = roll(loot);
        if (item.isEmpty()) {
            player.sendActionBar(Component.text(p.kind() == Kind.PRESENT ? "The present was empty." : "The egg was empty.",
                    NamedTextColor.GRAY));
        } else {
            ItemStack stack = item.get();
            int amount = stack.getAmount();
            give(player, stack);
            player.sendActionBar(Component.text(what + ": " + amount + "× ", NamedTextColor.GREEN)
                    .append(Component.translatable(stack.translationKey(), NamedTextColor.GOLD)));
        }
        if (p.kind() == Kind.PRESENT) {
            player.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.4f);
            player.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.6f);
            viewers.spawn(world, Particle.FIREWORK, at.getX(), at.getY() + 0.4, at.getZ(), 14, 0.25, 0.25, 0.25, 0.04,
                    null, false);
        } else {
            player.playSound(at, Sound.ENTITY_CHICKEN_EGG, 1f, 1.2f);
            viewers.spawn(world, Particle.DUST, at.getX(), at.getY() + 0.3, at.getZ(), 10, 0.2, 0.2, 0.2, 0,
                    new Particle.DustOptions(Color.fromRGB(0xFFB3DE), 1.0f), false);
            viewers.spawn(world, Particle.HAPPY_VILLAGER, at.getX(), at.getY() + 0.3, at.getZ(), 6, 0.2, 0.2, 0.2, 0,
                    null, false);
        }
    }

    private Optional<ItemStack> roll(LootTable loot) {
        if (loot.isEmpty()) return Optional.empty();
        LootTable.Entry entry = loot.pick(random);
        Material m = Material.matchMaterial(entry.material());
        if (m == null || !m.isItem() || m.isAir()) return Optional.empty();
        return Optional.of(new ItemStack(m, entry.roll(random)));
    }

    /** into the inventory in whole stacks, what does not fit drops at the player */
    private static void give(Player player, ItemStack stack) {
        int left = stack.getAmount();
        int max = Math.max(1, stack.getMaxStackSize());
        while (left > 0) {
            int n = Math.min(left, max);
            left -= n;
            ItemStack part = stack.clone();
            part.setAmount(n);
            player.getInventory().addItem(part).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
    }
}
