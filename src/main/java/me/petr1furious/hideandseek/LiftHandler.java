package me.petr1furious.hideandseek;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class LiftHandler {
    private final HideAndSeek plugin;

    public LiftHandler(HideAndSeek plugin) {
        this.plugin = plugin;
    }

    boolean isPlayerInLift(Player player) {
        Location playerLocation = player.getLocation();
        Location blockLocation = Utils.getFirstSolidBlock(playerLocation);
        if (blockLocation == null) {
            return false;
        }
        blockLocation.add(0, -1, 0);
        Block block = blockLocation.getBlock();
        return block.getType() == plugin.getGameConfig().getLiftMaterial();
    }

    public void handleLift(Player player) {
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        if (!plugin.getGameConfig().isEnableLifts()) {
            if (player.getAllowFlight()) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
            return;
        }

        boolean playerInLift = isPlayerInLift(player);
        player.setAllowFlight(playerInLift);
        player.setFlying(playerInLift);
    }
}
