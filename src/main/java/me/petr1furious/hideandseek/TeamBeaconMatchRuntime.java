package me.petr1furious.hideandseek;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class TeamBeaconMatchRuntime extends AbstractMatchRuntime {
    private static class PendingRespawn {
        private final ColorTeam team;
        private final long respawnAtMillis;
        private final int taskId;

        private PendingRespawn(ColorTeam team, long respawnAtMillis, int taskId) {
            this.team = team;
            this.respawnAtMillis = respawnAtMillis;
            this.taskId = taskId;
        }
    }

    private static class TeamBeaconState {
        private final ColorTeam team;
        private final Location location;
        private final BlockState replacedBlockState;
        private final BossBar bossBar;
        private int health;
        private boolean destroyed;

        private TeamBeaconState(ColorTeam team, Location location, BlockState replacedBlockState, BossBar bossBar,
            int health) {
            this.team = team;
            this.location = location;
            this.replacedBlockState = replacedBlockState;
            this.bossBar = bossBar;
            this.health = health;
        }
    }

    private final TeamRegistry teamRegistry;
    private final TeamBeaconConfig teamConfig;
    private final Map<ColorTeam, TeamBeaconState> beacons = new EnumMap<>(ColorTeam.class);
    private final Map<UUID, PendingRespawn> pendingRespawns = new HashMap<>();
    private final Map<UUID, Long> spawnProtectionExpirations = new HashMap<>();

    public TeamBeaconMatchRuntime(HideAndSeek plugin) {
        super(plugin);
        this.teamRegistry = plugin.getTeamRegistry();
        this.teamConfig = gameConfig.getTeamBeacon();
    }

    @Override
    public String start(List<Player> participants) {
        gamePlayers.clear();
        pendingRespawns.clear();
        spawnProtectionExpirations.clear();
        clearBeacons();

        List<Player> chosen = resolveParticipants(participants);
        if (chosen.isEmpty()) {
            return "No players available to start the game.";
        }

        List<ColorTeam> participatingTeams = teamRegistry.getAssignedTeams(chosen);
        if (participatingTeams.size() < 2) {
            return "Team beacon mode needs players from at least two assigned wool colors.";
        }

        for (Player player : chosen) {
            if (teamRegistry.getTeam(player).isEmpty()) {
                return player.getName() + " has no assigned team color.";
            }
        }

        for (ColorTeam team : participatingTeams) {
            Location beaconLocation = teamRegistry.getBeacon(team);
            if (beaconLocation == null || beaconLocation.getWorld() == null) {
                return team.getDisplayName() + " team has no configured beacon.";
            }
            placeBeacon(team, beaconLocation);
        }

        for (Player player : chosen) {
            registerGamePlayer(player);
            ColorTeam team = teamRegistry.getTeam(player).orElseThrow();
            spawnPlayerAtTeamBeacon(player, team);
        }

        showBossBarsToAllPlayers();
        startHudTask();
        return null;
    }

    @Override
    public String addPlayer(Player player, boolean broadcast) {
        Optional<ColorTeam> team = teamRegistry.getTeam(player);
        if (team.isEmpty()) {
            return player.getName() + " has no assigned team color.";
        }
        TeamBeaconState beacon = beacons.get(team.get());
        if (beacon == null || beacon.destroyed) {
            return "The " + team.get().getDisplayName() + " beacon is not available.";
        }

        registerGamePlayer(player);
        if (broadcast) {
            broadcastJoin(player);
        }
        spawnPlayerAtTeamBeacon(player, team.get());
        showBossBars(player);
        updateHud();
        return null;
    }

    public void handleLiveTeamChange(Player player, ColorTeam newTeam) {
        if (!gamePlayers.contains(player.getUniqueId())) {
            return;
        }

        cancelPendingRespawn(player.getUniqueId());
        spawnProtectionExpirations.remove(player.getUniqueId());
        TeamBeaconState beacon = beacons.get(newTeam);
        if (beacon != null && !beacon.destroyed) {
            spawnPlayerAtTeamBeacon(player, newTeam);
        } else {
            plugin.makePlayerSpectator(player, 0);
        }
        updateHud();
        checkGameEnd();
    }

    @Override
    public void end() {
        super.end();
        clearPendingRespawns();
        spawnProtectionExpirations.clear();
    }

    @Override
    public void stop() {
        super.stop();
        clearPendingRespawns();
        clearBeacons();
        spawnProtectionExpirations.clear();
    }

    @Override
    public boolean arePlayersFriendly(Player first, Player second) {
        Optional<ColorTeam> firstTeam = teamRegistry.getTeam(first);
        Optional<ColorTeam> secondTeam = teamRegistry.getTeam(second);
        return firstTeam.isPresent() && firstTeam.equals(secondTeam);
    }

    @Override
    public Collection<Player> getTrackedPlayers(Player viewer) {
        List<Player> result = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == viewer || !isPlayerInGame(other)) {
                continue;
            }
            if (arePlayersFriendly(viewer, other)) {
                continue;
            }
            result.add(other);
        }
        return result;
    }

    @Override
    public boolean shouldHideTrackedPlayerNames(Player viewer) {
        return shouldHidePerPlayerDistances();
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!gamePlayers.contains(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        Component death = event.deathMessage();
        if (death != null) {
            Bukkit.broadcast(death.color(NamedTextColor.GRAY));
        }

        Optional<ColorTeam> team = teamRegistry.getTeam(player);
        if (team.isEmpty()) {
            plugin.makePlayerSpectator(player, 0);
            checkGameEnd();
            return;
        }

        TeamBeaconState beacon = beacons.get(team.get());
        if (beacon != null && !beacon.destroyed) {
            queueRespawn(player, team.get());
        } else {
            plugin.makePlayerSpectator(player, 0);
        }
        checkGameEnd();
        updateHud();
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!gamePlayers.remove(player.getUniqueId())) {
            return;
        }
        cancelPendingRespawn(player.getUniqueId());
        spawnProtectionExpirations.remove(player.getUniqueId());
        checkGameEnd();
        updateHud();
    }

    @Override
    public void onBlockBreak(BlockBreakEvent event) {
        if (!gamePlayers.contains(event.getPlayer().getUniqueId())) {
            return;
        }

        if (event.getPlayer().getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            event.setCancelled(true);
            return;
        }

        TeamBeaconState beacon = getBeaconByBlock(event.getBlock());
        if (beacon != null) {
            handleBeaconDamage(event, beacon);
            return;
        }

        if (isProtectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent event) {
        if (gamePlayers.contains(event.getPlayer().getUniqueId())
            && event.getPlayer().getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            event.setCancelled(true);
            return;
        }
        if (isProtectedBlock(event.getBlockPlaced())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isProtectedBlock);
    }

    @Override
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isProtectedBlock);
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker != null && teamConfig.isDisableSpawnProtectionOnAttack()) {
            spawnProtectionExpirations.remove(attacker.getUniqueId());
        }

        if (event.getEntity() instanceof Player victim) {
            if (isSpawnProtected(victim)) {
                event.setCancelled(true);
                return;
            }

            if (attacker != null && attacker != victim && !teamConfig.isFriendlyFire()
                && arePlayersFriendly(attacker, victim)) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && teamConfig.isDisableSpawnProtectionOnAttack()) {
            spawnProtectionExpirations.remove(player.getUniqueId());
        }
    }

    @Override
    public boolean isProtectedBlock(Block block) {
        for (TeamBeaconState beacon : beacons.values()) {
            if (isBeaconBlock(block, beacon)) {
                return true;
            }
            if (block.getWorld().equals(beacon.location.getWorld())
                && block.getLocation().distanceSquared(beacon.location) <= teamConfig.getProtectionRadius()
                    * teamConfig.getProtectionRadius()) {
                return true;
            }
        }
        return false;
    }

    private void handleBeaconDamage(BlockBreakEvent event, TeamBeaconState beacon) {
        Player breaker = event.getPlayer();
        if (teamConfig.isBeaconBlockBreaksOnlyByEnemies()) {
            Optional<ColorTeam> breakerTeam = teamRegistry.getTeam(breaker);
            if (breakerTeam.isPresent() && breakerTeam.get() == beacon.team) {
                event.setCancelled(true);
                return;
            }
        }

        event.setCancelled(true);
        beacon.health = Math.max(0, beacon.health - 1);
        updateBossBar(beacon);

        if (beacon.health > 0) {
            beacon.location.getWorld().playSound(beacon.location, org.bukkit.Sound.BLOCK_ANVIL_LAND, 200.0f, 0.8f);
            breaker.sendActionBar(Component
                .text(
                    beacon.team.getDisplayName() + " beacon: " + beacon.health + "/" + teamConfig.getBeaconMaxHealth())
                .color(beacon.team.getTextColor()));
            updateHud();
            return;
        }

        beacon.destroyed = true;
        beacon.location.getBlock().setType(Material.AIR, false);
        beacon.location.getWorld().playSound(beacon.location, org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 200.0f, 0.6f);
        Bukkit.broadcast(Component.text(beacon.team.getDisplayName(), beacon.team.getTextColor())
            .append(Component.text(" beacon destroyed!", NamedTextColor.RED)));
        cancelPendingRespawnsForTeam(beacon.team);
        updateBossBar(beacon);
        updateHud();
        checkGameEnd();
    }

    private void queueRespawn(Player player, ColorTeam team) {
        cancelPendingRespawn(player.getUniqueId());
        plugin.makePlayerSpectator(player, teamConfig.getRespawnBlindnessSeconds());
        long respawnAtMillis = System.currentTimeMillis() + teamConfig.getRespawnDelaySeconds() * 1000L;
        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            pendingRespawns.remove(player.getUniqueId());
            TeamBeaconState beacon = beacons.get(team);
            if (beacon == null || beacon.destroyed || !gamePlayers.contains(player.getUniqueId())) {
                updateHud();
                checkGameEnd();
                return;
            }
            spawnPlayerAtTeamBeacon(player, team);
            updateHud();
            checkGameEnd();
        }, teamConfig.getRespawnDelaySeconds() * 20L);
        pendingRespawns.put(player.getUniqueId(), new PendingRespawn(team, respawnAtMillis, taskId));
    }

    private void spawnPlayerAtTeamBeacon(Player player, ColorTeam team) {
        TeamBeaconState beacon = beacons.get(team);
        Location spawn = findSpawnNearBeacon(beacon.location, 5, 6);
        if (spawn == null) {
            spawn = plugin.findSpawnAround(beacon.location, 5);
        }
        if (spawn == null) {
            spawn = beacon.location.clone().add(0.5, 1.0, 0.5);
        }
        plugin.prepareAlivePlayer(player, spawn, teamConfig.isKeepInvisibility());
        if (teamConfig.getSpawnProtectionSeconds() > 0) {
            spawnProtectionExpirations.put(player.getUniqueId(),
                System.currentTimeMillis() + teamConfig.getSpawnProtectionSeconds() * 1000L);
        }
    }

    private void placeBeacon(ColorTeam team, Location location) {
        Block block = location.getBlock();
        BlockState replacedState = block.getState();
        block.setType(team.getWoolMaterial(), false);
        BossBar bossBar = BossBar.bossBar(
            Component.text(team.getDisplayName() + " Beacon " + teamConfig.getBeaconMaxHealth() + "/"
                + teamConfig.getBeaconMaxHealth(), team.getTextColor()),
            1.0f, team.getBossBarColor(), BossBar.Overlay.PROGRESS);
        beacons.put(team,
            new TeamBeaconState(team, block.getLocation(), replacedState, bossBar, teamConfig.getBeaconMaxHealth()));
    }

    private void clearBeacons() {
        for (TeamBeaconState beacon : beacons.values()) {
            if (beacon.replacedBlockState != null) {
                beacon.replacedBlockState.update(true, false);
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(beacon.bossBar);
            }
        }
        beacons.clear();
    }

    private void showBossBarsToAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (gamePlayers.contains(player.getUniqueId())) {
                showBossBars(player);
            }
        }
    }

    private void showBossBars(Player player) {
        for (TeamBeaconState beacon : beacons.values()) {
            player.showBossBar(beacon.bossBar);
        }
    }

    private void updateBossBar(TeamBeaconState beacon) {
        float progress = Math.max(0.0f, Math.min(1.0f, beacon.health / (float) teamConfig.getBeaconMaxHealth()));
        beacon.bossBar.progress(progress);
        beacon.bossBar.name(Component.text(
            beacon.team.getDisplayName() + " Beacon " + beacon.health + "/" + teamConfig.getBeaconMaxHealth(),
            beacon.team.getTextColor()));
    }

    private void cancelPendingRespawn(UUID playerId) {
        PendingRespawn pendingRespawn = pendingRespawns.remove(playerId);
        if (pendingRespawn != null) {
            Bukkit.getScheduler().cancelTask(pendingRespawn.taskId);
        }
    }

    private void clearPendingRespawns() {
        for (PendingRespawn pendingRespawn : pendingRespawns.values()) {
            Bukkit.getScheduler().cancelTask(pendingRespawn.taskId);
        }
        pendingRespawns.clear();
    }

    private void cancelPendingRespawnsForTeam(ColorTeam team) {
        List<UUID> toCancel = new ArrayList<>();
        for (Map.Entry<UUID, PendingRespawn> entry : pendingRespawns.entrySet()) {
            if (entry.getValue().team == team) {
                toCancel.add(entry.getKey());
            }
        }
        for (UUID playerId : toCancel) {
            cancelPendingRespawn(playerId);
        }
    }

    private TeamBeaconState getBeaconByBlock(Block block) {
        for (TeamBeaconState beacon : beacons.values()) {
            if (isBeaconBlock(block, beacon)) {
                return beacon;
            }
        }
        return null;
    }

    private boolean isBeaconBlock(Block block, TeamBeaconState beacon) {
        return block.getWorld().equals(beacon.location.getWorld()) && block.getX() == beacon.location.getBlockX()
            && block.getY() == beacon.location.getBlockY() && block.getZ() == beacon.location.getBlockZ();
    }

    private boolean isSpawnProtected(Player player) {
        Long expiresAt = spawnProtectionExpirations.get(player.getUniqueId());
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= System.currentTimeMillis()) {
            spawnProtectionExpirations.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private int getAlivePlayers(ColorTeam team) {
        int alive = 0;
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            if (teamRegistry.getTeam(player).orElse(null) == team && isPlayerInGame(player)) {
                alive++;
            }
        }
        return alive;
    }

    private int getPendingRespawns(ColorTeam team) {
        int pending = 0;
        for (PendingRespawn respawn : pendingRespawns.values()) {
            if (respawn.team == team) {
                pending++;
            }
        }
        return pending;
    }

    private int getRemainingBeaconCount() {
        int remaining = 0;
        for (TeamBeaconState beacon : beacons.values()) {
            if (!beacon.destroyed) {
                remaining++;
            }
        }
        return remaining;
    }

    private boolean shouldHidePerPlayerDistances() {
        return teamConfig.isHidePerPlayerDistances() && getRemainingBeaconCount() > 1;
    }

    private boolean isTeamStillAlive(TeamBeaconState beacon) {
        int alive = getAlivePlayers(beacon.team);
        if (alive > 0) {
            return true;
        }

        if (!beacon.destroyed) {
            return getPendingRespawns(beacon.team) > 0;
        }

        return false;
    }

    private Location findSpawnNearBeacon(Location beaconLocation, int radius, int maxYOffset) {
        var world = beaconLocation.getWorld();
        if (world == null) {
            return null;
        }

        int beaconY = beaconLocation.getBlockY();
        List<int[]> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                candidates.add(new int[] { beaconLocation.getBlockX() + dx, beaconLocation.getBlockZ() + dz });
            }
        }
        java.util.Collections.shuffle(candidates);

        for (int[] candidate : candidates) {
            Location spawn = findSpawnAtColumn(world, candidate[0], candidate[1], beaconY, maxYOffset);
            if (spawn != null) {
                return spawn;
            }
        }

        return null;
    }

    private Location findSpawnAtColumn(org.bukkit.World world, int x, int z, int startY, int maxDrop) {
        int minFeetY = Math.max(world.getMinHeight() + 1, startY - maxDrop);
        int maxFeetY = Math.min(world.getMaxHeight() - 1, startY + 1);

        for (int y = maxFeetY; y >= minFeetY; y--) {
            Location feet = new Location(world, x, y, z);
            if (!Utils.playerPassable(feet.clone())) {
                continue;
            }
            if (!world.getBlockAt(x, y - 1, z).isSolid()) {
                continue;
            }
            return feet.add(0.5, 0, 0.5);
        }

        return null;
    }

    private void checkGameEnd() {
        List<ColorTeam> activeTeams = new ArrayList<>();
        for (TeamBeaconState beacon : beacons.values()) {
            if (isTeamStillAlive(beacon)) {
                activeTeams.add(beacon.team);
            }
        }

        if (activeTeams.size() == 1) {
            ColorTeam winner = activeTeams.getFirst();
            Bukkit.broadcast(Component.text("Game over! ", NamedTextColor.RED)
                .append(Component.text(winner.getDisplayName(), winner.getTextColor()))
                .append(Component.text(" team wins!", NamedTextColor.RED)));
            plugin.endGame();
        } else if (activeTeams.isEmpty()) {
            Bukkit.broadcast(Component.text("Game over! No teams left!", NamedTextColor.RED));
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

        for (Player viewer : getOnlineGamePlayers()) {
            Scoreboard scoreboard = manager.getNewScoreboard();
            configureScoreboardTeams(scoreboard);
            viewer.setScoreboard(scoreboard);
            Objective objective = scoreboard.registerNewObjective(viewer.getName(), Criteria.DUMMY,
                Component.text(shouldHidePerPlayerDistances() ? "Teams" : "Distances").color(NamedTextColor.GOLD));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);

            if (shouldHidePerPlayerDistances()) {
                populateTeamSummary(viewer, objective);
            } else {
                populateEnemyDistances(viewer, objective);
                addOrUpdateCountdownLine(viewer, objective);
            }
        }
    }

    private void configureScoreboardTeams(Scoreboard scoreboard) {
        for (ColorTeam colorTeam : ColorTeam.values()) {
            Team scoreboardTeam = scoreboard.registerNewTeam("hs_" + colorTeam.getId());
            scoreboardTeam.color(colorTeam.getTextColor());
            scoreboardTeam.setCanSeeFriendlyInvisibles(true);
        }

        for (Player player : getOnlineGamePlayers()) {
            Optional<ColorTeam> playerTeam = teamRegistry.getTeam(player);
            if (playerTeam.isEmpty()) {
                continue;
            }
            Team scoreboardTeam = scoreboard.getTeam("hs_" + playerTeam.get().getId());
            if (scoreboardTeam != null) {
                scoreboardTeam.addEntry(player.getName());
            }
        }
    }

    @Override
    protected void updateCountdown() {
        if (shouldHidePerPlayerDistances()) {
            updateHud();
            return;
        }

        for (Player player : getOnlineGamePlayers()) {
            Objective objective = player.getScoreboard().getObjective(player.getName());
            if (objective != null) {
                addOrUpdateCountdownLine(player, objective);
            }
        }
    }

    private void populateEnemyDistances(Player viewer, Objective objective) {
        for (Player target : getTrackedPlayers(viewer)) {
            double distance = viewer.getLocation().distance(target.getLocation());
            int gran = Math.max(1, gameConfig.getDistanceGranularity());
            int roundedDistance = ((int) (distance + 0.5) / gran) * gran;
            String line = plugin.serializeHudLine(plugin.getDistanceRange(roundedDistance, gran), target.getName());
            objective.getScore(line).setScore(roundedDistance);
        }
    }

    private void populateTeamSummary(Player viewer, Objective objective) {
        List<TeamBeaconState> states = new ArrayList<>(beacons.values());
        states.sort(
            Comparator.comparing((TeamBeaconState state) -> state.destroyed).thenComparing(state -> state.team.name()));
        int score = states.size() + 2;
        for (TeamBeaconState state : states) {
            int alive = getAlivePlayers(state.team);
            Component status = !state.destroyed ? Component.text(" ✔", NamedTextColor.GRAY)
                : alive > 0 ? Component.text(" " + alive, NamedTextColor.GRAY)
                    : Component.text(" ✘", NamedTextColor.GRAY);
            String line = plugin.serializeRawHudLine(
                Component.text(state.team.getDisplayName(), state.team.getTextColor()).append(status));
            objective.getScore(line).setScore(score--);
        }

        PendingRespawn pendingRespawn = pendingRespawns.get(viewer.getUniqueId());
        if (pendingRespawn != null) {
            long millisLeft = Math.max(0L, pendingRespawn.respawnAtMillis - System.currentTimeMillis());
            int secondsLeft = (int) Math.ceil(millisLeft / 1000.0);
            String line = plugin
                .serializeRawHudLine(Component.text("Respawn: " + secondsLeft + "s", NamedTextColor.GREEN));
            objective.getScore(line).setScore(-1);
        }
    }

    private void addOrUpdateCountdownLine(Player player, Objective objective) {
        String countdownLabel = plugin.serializeCountdownLine(getSecondsUntilNextHudUpdate());
        for (String entry : player.getScoreboard().getEntries()) {
            if (entry.contains("Refresh:") && !entry.equals(countdownLabel)) {
                player.getScoreboard().resetScores(entry);
            }
        }
        objective.getScore(countdownLabel).setScore(-1);
    }
}
