package me.petr1furious.hideandseek.weapons;

import me.petr1furious.hideandseek.GameConfig;
import me.petr1furious.hideandseek.HideAndSeek;
import me.petr1furious.hideandseek.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class RadarWeapon {
    private final GameConfig config;
    private final HideAndSeek plugin;
    private final String TAG = "radar";

    public RadarWeapon(GameConfig config, HideAndSeek plugin) {
        this.config = config;
        this.plugin = plugin;
    }

    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!Items.isRightClick(event))
            return;
        if (!Items.checkForItem(event.getItem(), TAG))
            return;

        Player player = event.getPlayer();
        event.setCancelled(true);
        if (player.getGameMode() == GameMode.SPECTATOR)
            return;

        RadarConfig radar = config.getRadar();
        int remaining = getRemainingCooldownSeconds(player);
        if (remaining > 0) {
            player.sendActionBar(Component.text("Radar: " + remaining + "s").color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.2f, 0.5f);
            return;
        }
        setCooldown(player, radar);

        Player target = findTarget(player, radar);
        if (target == null) {
            player.sendActionBar(Component.text("No players in cone").color(NamedTextColor.GRAY));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.2f, 0.7f);
            return;
        }

        if (radar.getGlowingEffectLength() > 0) {
            target.addPotionEffect(
                new PotionEffect(PotionEffectType.GLOWING, radar.getGlowingEffectLength() * 20, 0, false, false));
        }

        player.sendActionBar(Component.text("Radar: ").color(NamedTextColor.YELLOW)
            .append(plugin.shouldHideTrackedPlayerNames(player) ? Component.text("enemy", NamedTextColor.AQUA)
                : Component.text(target.getName(), NamedTextColor.AQUA)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.2f, 2f);
        target.playSound(target.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.5f);
    }

    private int getRemainingCooldownSeconds(Player player) {
        return (int) Math.ceil(player.getCooldown(Material.SPYGLASS) / 20.0);
    }

    private void setCooldown(Player player, RadarConfig radar) {
        player.setCooldown(Material.SPYGLASS, Math.toIntExact(radar.getCooldown() * 20L));
    }

    private Player findTarget(Player source, RadarConfig radar) {
        Player nearest = null;
        double bestDistanceSquared = Double.MAX_VALUE;
        double maxDistanceSquared = radar.getMaxDistance() * radar.getMaxDistance();
        double maxAngle = Math.max(0.0, radar.getMaxAngleDistance());
        Vector facing = source.getEyeLocation().getDirection().normalize();

        for (Player player : plugin.getTrackedPlayers(source)) {

            Vector toTarget = player.getEyeLocation().toVector().subtract(source.getEyeLocation().toVector());
            double distanceSquared = toTarget.lengthSquared();
            if (distanceSquared <= 1.0E-6 || distanceSquared > maxDistanceSquared)
                continue;

            double angle = getAngleDegrees(facing, toTarget);
            if (angle > maxAngle)
                continue;

            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                nearest = player;
            }
        }
        return nearest;
    }

    private double getAngleDegrees(Vector facing, Vector toTarget) {
        Vector targetDirection = toTarget.clone().normalize();
        double dot = Math.max(-1.0, Math.min(1.0, facing.dot(targetDirection)));
        return Math.toDegrees(Math.acos(dot));
    }

    public ItemStack createItem(int count) {
        var item = new ItemStack(Material.SPYGLASS);
        Items.addTag(item, TAG);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Radar").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        item.setAmount(count);
        return item;
    }
}
