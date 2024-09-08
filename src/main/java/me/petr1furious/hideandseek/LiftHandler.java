package me.petr1furious.hideandseek;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class LiftHandler {

    private int liftCheckHeight = 4;

    private int minLiftMaterialCount = 2;

    private Map<Player, Long> lastSoundTime = new HashMap<>();

    private char[][] liftPattern = {
        {'?', 'x', 'x', '?'},
        {'x', 'o', 'o', 'x'},
        {'x', 'o', 'o', 'x'},
        {'?', 'x', 'x', '?'}
    };

    private int checkLiftSide(Location location, Material liftMaterial, boolean directionUp) {
        int liftMaterialCount = 0;
        for (int i = 0; i < liftCheckHeight; i++) {
            Location blockLocation;
            if (directionUp) {
                blockLocation = location.clone().add(0, i, 0);
            } else {
                blockLocation = location.clone().add(0, -i, 0);
            }
            Block block = blockLocation.getBlock();
            if (block.getType() == liftMaterial) {
                liftMaterialCount++;
            } else if (block.getType() != Material.AIR) {
                return -1;
            }
        }
        return liftMaterialCount;
    }

    private boolean isPlayerInLiftAtPosition(Location location, Material liftMaterial, int x, int z) {
        for (int i = 0; i < liftPattern.length; i++) {
            for (int j = 0; j < liftPattern[i].length; j++) {
                if (liftPattern[i][j] == '?') {
                    continue;
                }

                boolean good = false;
                for (boolean directionUp : new boolean[]{false, true}) {
                    int liftMaterialCount = checkLiftSide(location.clone().add(i - x, 0, j - z), liftMaterial, directionUp);
                    System.out.println(directionUp + " " + liftMaterialCount + " " + liftPattern[i][j]);
                    if (liftMaterialCount == -1) {
                        continue;
                    }

                    if (liftPattern[i][j] == 'o' && liftMaterialCount != 0) {
                        continue;
                    }

                    if (liftPattern[i][j] == 'x' && liftMaterialCount < minLiftMaterialCount) {
                        continue;
                    }

                    good = true;
                    break;
                }

                if (!good) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isPlayerInLift(Player player, Material liftMaterial) {
        Location playerLocation = player.getLocation();

        for (int i = 0; i < liftPattern.length; i++) {
            for (int j = 0; j < liftPattern[i].length; j++) {
                if (liftPattern[i][j] == 'o') {
                    if (isPlayerInLiftAtPosition(playerLocation, liftMaterial, i, j)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void handleLift(Player player, Material liftMaterial, boolean isEnableLifts) {
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        if (!isEnableLifts) {
            if (player.getAllowFlight()) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
            return;
        }

        boolean playerInLift = isPlayerInLift(player, liftMaterial);
        player.setAllowFlight(playerInLift);
        player.setFlying(playerInLift);

        if (playerInLift) {
            long currentTime = System.currentTimeMillis();
            long lastTime = lastSoundTime.getOrDefault(player, 0L);
            if (currentTime - lastTime >= 1000) {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1.0f, 1.0f);
                }
                lastSoundTime.put(player, currentTime);
            }
        }
    }
}
