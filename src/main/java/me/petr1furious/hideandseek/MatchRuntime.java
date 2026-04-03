package me.petr1furious.hideandseek;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.List;

public interface MatchRuntime {
    String start(List<Player> participants);

    String addPlayer(Player player, boolean broadcast);

    default void end() {
    }

    void stop();

    boolean isPlayerInGame(Player player);

    boolean arePlayersFriendly(Player first, Player second);

    Collection<Player> getTrackedPlayers(Player viewer);

    boolean shouldHideTrackedPlayerNames(Player viewer);

    default void onPlayerDeath(PlayerDeathEvent event) {
    }

    default void onPlayerJoin(PlayerJoinEvent event) {
    }

    default void onPlayerQuit(PlayerQuitEvent event) {
    }

    default void onBlockBreak(BlockBreakEvent event) {
    }

    default void onBlockPlace(BlockPlaceEvent event) {
    }

    default void onEntityExplode(EntityExplodeEvent event) {
    }

    default void onBlockExplode(BlockExplodeEvent event) {
    }

    default void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
    }

    default void onEntityShootBow(EntityShootBowEvent event) {
    }

    default boolean isProtectedBlock(Block block) {
        return false;
    }
}
