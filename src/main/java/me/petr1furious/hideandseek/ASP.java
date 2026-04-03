package me.petr1furious.hideandseek;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public class ASP {
    private final ASPConfig config;
    private final Plugin plugin;
    private AdvancedSlimePaperAPI asp;

    public ASP(ASPConfig config, Plugin plugin) {
        this.config = config;
        this.plugin = plugin;
    }

    private void init() {
        if (asp == null) {
            asp = AdvancedSlimePaperAPI.instance();
        }
    }

    private void unloadWorld() {
        final var worldName = Objects.requireNonNull(config.gameWorldName);
        if (asp.getLoadedWorld(worldName) == null) {
            return;
        }
        final var world = Objects.requireNonNull(Bukkit.getWorld(worldName));
        final var players = world.getPlayers();
        final var spawn = Bukkit.getWorlds().getFirst().getSpawnLocation();
        for (final var player : players) {
            player.teleport(spawn);
        }
        boolean unloaded = Bukkit.unloadWorld(world, false);
        if (!unloaded) {
            plugin.getLogger().warning("ASP support: Failed to unload world " + worldName);
        }
    }

    private void copyWorldSettings(World sourceWorld, World destWorld) {
        destWorld.setDifficulty(sourceWorld.getDifficulty());
        for (GameRule<?> gameRule : Registry.GAME_RULE) {
            copyGameRule(sourceWorld, destWorld, gameRule);
        }
    }

    private <T> void copyGameRule(World sourceWorld, World destWorld, GameRule<T> gameRule) {
        try {
            T value = sourceWorld.getGameRuleValue(gameRule);
            if (value != null) {
                destWorld.setGameRule(gameRule, value);
            }
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().fine("ASP support: Skipping unsupported game rule " + gameRule.getKey());
        }
    }

    public void setupWorld(boolean force) {
        if (!config.enable) {
            return;
        }
        if (!force && !config.resetWorldOnStart) {
            return;
        }
        init();
        unloadWorld();
        final var sourceWorldName = Objects.requireNonNull(config.templateWorldName);
        final var destWorldName = Objects.requireNonNull(config.gameWorldName);

        final var sourceSlimeWorld = asp.getLoadedWorld(sourceWorldName);
        if (sourceSlimeWorld == null) {
            throw new IllegalStateException("ASP support: Template world " + sourceWorldName + " is not loaded");
        }
        final var newSlimeWorld = sourceSlimeWorld.clone(destWorldName);
        asp.loadWorld(newSlimeWorld, true);

        World sourceWorld = Bukkit.getWorld(sourceWorldName);
        World destWorld = Bukkit.getWorld(destWorldName);
        if (sourceWorld == null || destWorld == null) {
            plugin.getLogger().warning("ASP support: Could not copy world settings after cloning");
            return;
        }
        copyWorldSettings(sourceWorld, destWorld);
    }
}
