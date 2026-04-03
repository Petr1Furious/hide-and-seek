package me.petr1furious.hideandseek;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.List;

public class ClassicMatchRuntime extends AbstractMatchRuntime {
    private boolean checkingGameEnd = false;

    public ClassicMatchRuntime(HideAndSeek plugin) {
        super(plugin);
    }

    @Override
    public String start(List<Player> participants) {
        checkingGameEnd = false;
        gamePlayers.clear();

        List<Player> chosen = resolveParticipants(participants);
        if (chosen.isEmpty()) {
            return "No players available to start the game.";
        }

        for (Player player : chosen) {
            registerGamePlayer(player);
            plugin.spawnClassicPlayer(player);
        }

        startHudTask();
        return null;
    }

    @Override
    public String addPlayer(Player player, boolean broadcast) {
        registerGamePlayer(player);
        if (broadcast) {
            broadcastJoin(player);
        }
        plugin.spawnClassicPlayer(player);
        updateHud();
        return null;
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        event.setCancelled(isPlayerInGame(player));
        Component death = event.deathMessage();
        if (death != null) {
            Bukkit.broadcast(death.color(NamedTextColor.GRAY));
        }
        plugin.makePlayerSpectator(player, 0);

        if (!checkingGameEnd) {
            checkingGameEnd = true;
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                checkingGameEnd = false;
                checkGameEnd();
            }, 60L);
        }
    }

    @Override
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, this::updateHud, 1L);
    }

    private void checkGameEnd() {
        int playersInGame = 0;
        Player lastPlayer = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isPlayerInGame(player)) {
                playersInGame++;
                lastPlayer = player;
            }
        }

        if (playersInGame <= 1) {
            if (lastPlayer == null) {
                Bukkit.broadcast(Component.text("Game over! No players left!").color(NamedTextColor.RED));
            } else {
                Bukkit.broadcast(Component.text("Game over! ").color(NamedTextColor.RED)
                    .append(Component.text(lastPlayer.getName()).color(NamedTextColor.BLUE))
                    .append(Component.text(" wins!").color(NamedTextColor.RED)));
            }
            plugin.endGame();
        }
    }

    @Override
    protected void updateHud() {
        if (plugin.getGameStatus() != GameStatus.RUNNING) {
            return;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!gamePlayers.contains(viewer.getUniqueId())) {
                continue;
            }
            if (viewer.getWorld() != plugin.getConfiguredWorld()) {
                continue;
            }

            Scoreboard scoreboard = manager.getNewScoreboard();
            viewer.setScoreboard(scoreboard);
            Objective objective = scoreboard.registerNewObjective(viewer.getName(), Criteria.DUMMY,
                Component.text("Distances").color(NamedTextColor.GOLD));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);

            for (Player target : getTrackedPlayers(viewer)) {
                double distance = viewer.getLocation().distance(target.getLocation());
                int gran = Math.max(1, gameConfig.getDistanceGranularity());
                int roundedDistance = ((int) (distance + 0.5) / gran) * gran;
                String serialized = plugin.serializeHudLine(plugin.getDistanceRange(roundedDistance, gran),
                    target.getName());
                objective.getScore(serialized).setScore(roundedDistance);
            }

            addOrUpdateCountdownLine(objective, viewer);
        }
    }

    @Override
    protected void updateCountdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!gamePlayers.contains(player.getUniqueId())) {
                continue;
            }
            Objective objective = player.getScoreboard().getObjective(player.getName());
            if (objective != null) {
                addOrUpdateCountdownLine(objective, player);
            }
        }
    }

    private void addOrUpdateCountdownLine(Objective objective, Player player) {
        String countdownLabel = plugin.serializeCountdownLine(getSecondsUntilNextHudUpdate());
        for (String entry : player.getScoreboard().getEntries()) {
            if (entry.contains("Refresh:") && !entry.equals(countdownLabel)) {
                player.getScoreboard().resetScores(entry);
            }
        }
        objective.getScore(countdownLabel).setScore(-1);
    }
}
