package me.petr1furious.hideandseek;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

abstract class AbstractMatchRuntime implements MatchRuntime {
    protected final HideAndSeek plugin;
    protected final GameConfig gameConfig;
    protected final Set<UUID> gamePlayers = new HashSet<>();

    private int hudTaskId = -1;
    private int ticksUntilNextHudUpdate = 0;

    protected AbstractMatchRuntime(HideAndSeek plugin) {
        this.plugin = plugin;
        this.gameConfig = plugin.getGameConfig();
    }

    protected List<Player> resolveParticipants(List<Player> participants) {
        if (participants != null && !participants.isEmpty()) {
            return new ArrayList<>(participants);
        }

        List<Player> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            result.add(player);
        }
        return result;
    }

    protected void registerGamePlayer(Player player) {
        gamePlayers.add(player.getUniqueId());
    }

    protected void unregisterGamePlayer(Player player) {
        gamePlayers.remove(player.getUniqueId());
    }

    protected void broadcastJoin(Player player) {
        for (UUID uuid : gamePlayers) {
            Player current = Bukkit.getPlayer(uuid);
            if (current != null && current.isOnline()) {
                current.sendMessage(plugin.buildJoinMessage(player));
            }
        }
    }

    protected void startHudTask() {
        stopHudTask();
        ticksUntilNextHudUpdate = gameConfig.getDistanceUpdateIntervalTicks();
        updateHud();
        hudTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (plugin.getGameStatus() != GameStatus.RUNNING || plugin.getActiveRuntime() != this) {
                return;
            }
            ticksUntilNextHudUpdate -= 20;
            updateCountdown();
            if (ticksUntilNextHudUpdate <= 0) {
                updateHud();
                ticksUntilNextHudUpdate = gameConfig.getDistanceUpdateIntervalTicks();
            }
        }, 20L, 20L);
    }

    protected void stopHudTask() {
        if (hudTaskId != -1) {
            Bukkit.getScheduler().cancelTask(hudTaskId);
            hudTaskId = -1;
        }
    }

    protected int getSecondsUntilNextHudUpdate() {
        return Math.max(0, ticksUntilNextHudUpdate / 20);
    }

    protected Collection<Player> getOnlineGamePlayers() {
        List<Player> result = new ArrayList<>();
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                result.add(player);
            }
        }
        return result;
    }

    @Override
    public boolean isPlayerInGame(Player player) {
        return plugin.getGameStatus() == GameStatus.RUNNING && gamePlayers.contains(player.getUniqueId())
            && player.getGameMode() != GameMode.SPECTATOR && player.getGameMode() != GameMode.CREATIVE
            && player.getWorld() == plugin.getConfiguredWorld();
    }

    @Override
    public boolean arePlayersFriendly(Player first, Player second) {
        return false;
    }

    @Override
    public Collection<Player> getTrackedPlayers(Player viewer) {
        List<Player> result = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == viewer || !isPlayerInGame(other)) {
                continue;
            }
            result.add(other);
        }
        return result;
    }

    @Override
    public boolean shouldHideTrackedPlayerNames(Player viewer) {
        return false;
    }

    @Override
    public void end() {
        stopHudTask();
        for (UUID uuid : new ArrayList<>(gamePlayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.resetPlayerScoreboard(player);
            }
        }
    }

    @Override
    public void stop() {
        stopHudTask();
        for (UUID uuid : new ArrayList<>(gamePlayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.resetPlayer(player);
            }
        }
        gamePlayers.clear();
    }

    protected abstract void updateHud();

    protected void updateCountdown() {
    }
}
