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

public class LocatorWeapon {
    private final GameConfig config;
    private final HideAndSeek plugin;
    private final String TAG = "locator";

    public LocatorWeapon(GameConfig config, HideAndSeek plugin) {
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
        LocatorConfig lc = config.getLocator();
        int remaining = getRemainingCooldownSeconds(player);
        if (remaining > 0) {
            player.sendActionBar(Component.text("Locator: " + remaining + "s").color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.2f, 0.5f); // cooldown/fail sound
            return;
        }

        Player nearest = findNearest(player);
        if (nearest == null) {
            player.sendActionBar(Component.text("No players").color(NamedTextColor.GRAY));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.2f, 0.7f); // not found
            return;
        }
        setCooldown(player, lc);

        double dy = nearest.getLocation().getY() - player.getLocation().getY();
        String arrows = buildArrows(dy, lc);
        Component msg = (plugin.shouldHideTrackedPlayerNames(player)
            ? Component.text("Enemy").color(NamedTextColor.AQUA)
            : Component.text(nearest.getName()).color(NamedTextColor.AQUA))
            .append(Component.text(" "))
            .append(Component.text(arrows).color(NamedTextColor.GOLD));
        player.sendActionBar(msg);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.2f, 2f); // success
        if (lc.getEffectLength() > 0) {
            player.addPotionEffect(
                new PotionEffect(PotionEffectType.BLINDNESS, lc.getEffectLength() * 20, 0, false, false));
        }
    }

    private int getRemainingCooldownSeconds(Player player) {
        return (int) Math.ceil(player.getCooldown(Material.COMPARATOR) / 20.0);
    }

    private void setCooldown(Player player, LocatorConfig lc) {
        player.setCooldown(Material.COMPARATOR, Math.toIntExact(lc.getCooldown() * 20L));
    }

    private Player findNearest(Player source) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player p : plugin.getTrackedPlayers(source)) {
            double d = p.getLocation().distanceSquared(source.getLocation());
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        return nearest;
    }

    private String buildArrows(double dy, LocatorConfig lc) {
        double precision = Math.max(0.0001, lc.getPrecision());
        int arrows = (int) Math.ceil(Math.abs(dy) / precision);
        if (arrows == 0)
            return "=";
        arrows = Math.min(arrows, lc.getSteps());
        StringBuilder sb = new StringBuilder();
        char ch = dy > 0 ? '\u2191' : '\u2193';
        for (int i = 0; i < arrows; i++)
            sb.append(ch);
        return sb.toString();
    }

    public ItemStack createItem(int count) {
        var item = new ItemStack(Material.COMPARATOR);
        Items.addTag(item, TAG);
        var meta = item.getItemMeta();
        meta.displayName(
            Component.text("Locator").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        item.setAmount(count);
        return item;
    }
}
