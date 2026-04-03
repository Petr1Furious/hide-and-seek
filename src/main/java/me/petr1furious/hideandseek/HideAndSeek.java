package me.petr1furious.hideandseek;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import me.petr1furious.hideandseek.weapons.HimarsWeapon;
import me.petr1furious.hideandseek.weapons.InfiniteCrossbowWeapon;
import me.petr1furious.hideandseek.weapons.OreshnikWeapon;
import me.petr1furious.hideandseek.weapons.LocatorWeapon;
import me.petr1furious.hideandseek.weapons.FPVDroneWeapon;
import me.petr1furious.hideandseek.weapons.RadarWeapon;
import me.petr1furious.hideandseek.weapons.GrappleBowWeapon;

import java.util.Random;
import java.util.List;
import java.util.ArrayList;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

public class HideAndSeek extends JavaPlugin implements Listener {
    private static class WorldBorderState {
        final String worldName;
        final double centerX;
        final double centerZ;
        final double size;
        final double damageAmount;
        final double damageBuffer;
        final int warningDistance;
        final int warningTime;

        WorldBorderState(World world, WorldBorder border) {
            this.worldName = world.getName();
            this.centerX = border.getCenter().getX();
            this.centerZ = border.getCenter().getZ();
            this.size = border.getSize();
            this.damageAmount = border.getDamageAmount();
            this.damageBuffer = border.getDamageBuffer();
            this.warningDistance = border.getWarningDistance();
            this.warningTime = border.getWarningTimeTicks();
        }
    }

    private CommandHandler commandHandler;

    private Random random = new Random();

    private GameStatus gameStatus = GameStatus.NOT_STARTED;

    private GameConfig gameConfig;
    private TeamRegistry teamRegistry;

    private LiftHandler liftHandler;

    private Location activeArenaCenter;
    private double activeArenaSpawnRadius = -1.0;
    private WorldBorderState originalWorldBorderState;

    private InfiniteCrossbowWeapon infiniteCrossbowWeapon;
    private OreshnikWeapon oreshnikWeapon;
    private HimarsWeapon himarsWeapon;
    private LocatorWeapon locatorWeapon;
    private FPVDroneWeapon fpvDroneWeapon;
    private RadarWeapon radarWeapon;
    private GrappleBowWeapon grappleBowWeapon;

    private ASP asp;
    private MatchRuntime activeRuntime;
    private ClassicMatchRuntime classicMatchRuntime;
    private TeamBeaconMatchRuntime teamBeaconMatchRuntime;

    LegacyComponentSerializer legacySerializer = LegacyComponentSerializer
        .legacy(LegacyComponentSerializer.SECTION_CHAR);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("teams.yml", false);
        gameConfig = new GameConfig(getConfig());
        teamRegistry = new TeamRegistry(this);
        commandHandler = new CommandHandler(this);
        commandHandler.registerCommands();
        liftHandler = new LiftHandler();
        infiniteCrossbowWeapon = new InfiniteCrossbowWeapon(gameConfig);
        oreshnikWeapon = new OreshnikWeapon(gameConfig);
        himarsWeapon = new HimarsWeapon(gameConfig);
        locatorWeapon = new LocatorWeapon(gameConfig, this);
        fpvDroneWeapon = new FPVDroneWeapon(gameConfig, this);
        radarWeapon = new RadarWeapon(gameConfig, this);
        grappleBowWeapon = new GrappleBowWeapon(gameConfig);
        asp = new ASP(gameConfig.getAspConfig(), this);
        classicMatchRuntime = new ClassicMatchRuntime(this);
        teamBeaconMatchRuntime = new TeamBeaconMatchRuntime(this);
        registerEvents();
    }

    @Override
    public void onDisable() {
        if (fpvDroneWeapon != null) {
            fpvDroneWeapon.endAllSessions();
        }
        if (activeRuntime != null) {
            activeRuntime.stop();
        }
        restoreArenaBorder();
    }

    public GameStatus getGameStatus() {
        return gameStatus;
    }

    public GameConfig getGameConfig() {
        return gameConfig;
    }

    public TeamRegistry getTeamRegistry() {
        return teamRegistry;
    }

    public boolean isPlayerInGame(Player player) {
        return activeRuntime != null && activeRuntime.isPlayerInGame(player);
    }

    public MatchRuntime getActiveRuntime() {
        return activeRuntime;
    }

    public Component buildJoinMessage(Player player) {
        return Component.text().append(Component.text(player.getName(), NamedTextColor.AQUA))
            .append(Component.text(" joined the game", NamedTextColor.GREEN)).build();
    }

    public Component buildTeamJoinMessage(Player player, ColorTeam team) {
        return Component.text().append(Component.text(player.getName(), NamedTextColor.AQUA))
            .append(Component.text(" joined ", NamedTextColor.GREEN))
            .append(Component.text(team.getDisplayName(), team.getTextColor()))
            .append(Component.text(" team", NamedTextColor.GREEN)).build();
    }

    String addPlayerToGame(Player player, boolean broadcast) {
        if (activeRuntime == null) {
            return "Game is not running";
        }
        return activeRuntime.addPlayer(player, broadcast);
    }

    String startGame(List<Player> participants) {
        if (gameStatus == GameStatus.RUNNING) {
            return "Game is already running";
        }
        if (activeRuntime != null) {
            activeRuntime.stop();
            activeRuntime = null;
        }

        asp.setupWorld(false);
        setupArenaBorder();

        activeRuntime = gameConfig.getGameMode() == GameModeType.TEAM_BEACON ? teamBeaconMatchRuntime
            : classicMatchRuntime;
        gameStatus = GameStatus.RUNNING;
        String error = activeRuntime.start(participants);
        if (error != null) {
            activeRuntime.stop();
            activeRuntime = null;
            gameStatus = GameStatus.NOT_STARTED;
            restoreArenaBorder();
            return error;
        }

        getServer().sendMessage(Component.text("Starting game").color(NamedTextColor.GREEN));
        return null;
    }

    void stopGame() {
        getServer().sendMessage(Component.text("Stopping game").color(NamedTextColor.RED));
        if (activeRuntime != null) {
            activeRuntime.stop();
            activeRuntime = null;
        }
        gameStatus = GameStatus.NOT_STARTED;
        restoreArenaBorder();
    }

    void resetPlayer(Player player) {
        resetPlayerScoreboard(player);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
            Utils.teleportPlayerOnBlock(player);
        }
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.GLOWING);
    }

    void resetPlayerScoreboard(Player player) {
        var manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    void resetGame() {
        stopGame();
    }

    void endGame() {
        gameStatus = GameStatus.ENDED;
        if (activeRuntime != null) {
            activeRuntime.end();
        }
        restoreArenaBorder();
    }

    void registerEvents() {
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
            public void onPlayerDeath(PlayerDeathEvent event) {
                if (gameStatus == GameStatus.RUNNING && activeRuntime != null) {
                    activeRuntime.onPlayerDeath(event);
                }
            }

            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                if (gameStatus == GameStatus.RUNNING && activeRuntime != null) {
                    activeRuntime.onPlayerJoin(event);
                }
            }

            @EventHandler
            public void onPlayerQuit(PlayerQuitEvent event) {
                if (gameStatus == GameStatus.RUNNING && activeRuntime != null) {
                    activeRuntime.onPlayerQuit(event);
                }
            }

            @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
            public void onBlockBreak(BlockBreakEvent event) {
                if (gameStatus == GameStatus.RUNNING && activeRuntime != null) {
                    activeRuntime.onBlockBreak(event);
                }
            }

            @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
            public void onBlockPlace(BlockPlaceEvent event) {
                if (gameStatus == GameStatus.RUNNING && activeRuntime != null) {
                    activeRuntime.onBlockPlace(event);
                }
            }

            @EventHandler(ignoreCancelled = true)
            public void onEntityExplode(EntityExplodeEvent event) {
                if (gameStatus == GameStatus.RUNNING && activeRuntime != null) {
                    activeRuntime.onEntityExplode(event);
                }
            }

            @EventHandler(ignoreCancelled = true)
            public void onBlockExplode(BlockExplodeEvent event) {
                if (gameStatus == GameStatus.RUNNING && activeRuntime != null) {
                    activeRuntime.onBlockExplode(event);
                }
            }

            @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
            public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
                if (gameStatus == GameStatus.RUNNING && activeRuntime != null) {
                    activeRuntime.onEntityDamageByEntity(event);
                }
            }

            @EventHandler
            public void onProjectileHit(ProjectileHitEvent event) {
                Projectile projectile = event.getEntity();

                var shooter = Utils.getEntityShooter(projectile);

                Location location;
                if (event.getHitBlock() != null && event.getHitBlockFace() != null) {
                    Vector projectileStart = projectile.getLocation().toVector();
                    Vector projectileDirection = projectile.getVelocity().normalize();

                    Block hitBlock = event.getHitBlock();
                    BlockFace hitFace = event.getHitBlockFace();
                    Vector planeNormal = hitFace.getDirection();
                    Vector planePoint = hitBlock.getLocation().toVector().add(new Vector(0.5, 0.5, 0.5))
                        .add(planeNormal.multiply(0.501));

                    double denominator = planeNormal.dot(projectileDirection);
                    if (Math.abs(denominator) > 1e-6) {
                        double t = planeNormal.dot(planePoint.subtract(projectileStart)) / denominator;
                        Vector intersection = projectileStart.add(projectileDirection.multiply(t));
                        location = intersection.toLocation(projectile.getWorld());
                    } else {
                        location = projectile.getLocation();
                    }
                } else if (event.getHitEntity() != null) {
                    location = event.getHitEntity().getLocation();
                } else {
                    location = projectile.getLocation();
                }

                if (handleProjectileHitLocation(projectile, location, shooter, event.getHitEntity())) {
                    event.setCancelled(true);
                }
            }

            @EventHandler
            public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
                infiniteCrossbowWeapon.onPlayerInteract(event);
                himarsWeapon.onPlayerInteract(event);
                oreshnikWeapon.onPlayerInteract(event);
                locatorWeapon.onPlayerInteract(event);
                fpvDroneWeapon.onPlayerInteract(event);
                radarWeapon.onPlayerInteract(event);
            }

            @EventHandler
            public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
                boolean inDrone = fpvDroneWeapon.isPlayerInDroneMode(event.getPlayer());
                liftHandler.handleLift(event.getPlayer(), gameConfig.getLiftMaterial(), gameConfig.isEnableLifts(),
                    gameStatus, inDrone);
            }

            @EventHandler
            public void onArrowShoot(EntityShootBowEvent event) {
                if (event.getBow() == null) {
                    return;
                }
                ItemStack bow = event.getBow();
                if (bow.getItemMeta().hasCustomModelDataComponent()) {
                    if (bow.getType() == Material.CROSSBOW) {
                        infiniteCrossbowWeapon.onBowShoot(event);
                        himarsWeapon.onBowShoot(event);
                    } else if (bow.getType() == Material.BOW) {
                        grappleBowWeapon.onBowShoot(event);
                    }
                }
                if (gameStatus == GameStatus.RUNNING && activeRuntime != null) {
                    activeRuntime.onEntityShootBow(event);
                }
            }
        }, this);
    }

    boolean handleProjectileHitLocation(Projectile projectile, Location location, Entity shooter, Entity target) {
        if (infiniteCrossbowWeapon.handleProjectileImpact(projectile, location, shooter, target))
            return true;
        if (oreshnikWeapon.handleProjectileImpact(projectile, location, shooter, target))
            return true;
        if (himarsWeapon.handleProjectileImpact(projectile, location, shooter, target))
            return true;
        if (grappleBowWeapon.handleProjectileImpact(projectile, location, shooter, target))
            return true;
        return false;
    }

    public void prepareAlivePlayer(Player player, Location location, boolean keepInvisibility) {
        Location target = location.clone();
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        player.teleport(target);
        player.setGameMode(GameMode.SURVIVAL);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.GLOWING);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        if (keepInvisibility) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
        }
        player.setHealth(20.0);
        player.setFoodLevel(20);
        if (gameConfig.isEnableGameInventory()) {
            loadGameInventory(player);
        }
    }

    public void makePlayerSpectator(Player player, int blindnessSeconds) {
        player.setGameMode(GameMode.SPECTATOR);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        if (blindnessSeconds > 0) {
            player
                .addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessSeconds * 20, 0, false, false));
        }
    }

    public void spawnClassicPlayer(Player player) {
        World targetWorld = getConfiguredWorld();
        if (targetWorld == null) {
            targetWorld = player.getWorld();
        }

        Location spawn = findSpawnAround(getCurrentGameCenter().toLocation(targetWorld),
            Math.max(1, (int) getCurrentGameRadius()));
        if (spawn == null) {
            spawn = targetWorld.getSpawnLocation();
        }
        prepareAlivePlayer(player, spawn, true);
    }

    public Location findSpawnAround(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) {
            return null;
        }

        int attempts = Math.max(32, radius * 4);
        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(random.nextDouble()) * radius;
            int x = (int) Math.round(center.getX() + Math.cos(angle) * distance);
            int z = (int) Math.round(center.getZ() + Math.sin(angle) * distance);
            Location candidate = Utils.getFirstSolidBlock(new Location(world, x, center.getY(), z));
            if (candidate != null) {
                return candidate.add(0.5, 0, 0.5);
            }
        }

        Location fallback = Utils.getFirstSolidBlock(center);
        return fallback == null ? null : fallback.add(0.5, 0, 0.5);
    }

    public boolean arePlayersFriendly(Player first, Player second) {
        return activeRuntime != null && activeRuntime.arePlayersFriendly(first, second);
    }

    public List<Player> getTrackedPlayers(Player viewer) {
        if (activeRuntime == null) {
            return List.of();
        }
        return new ArrayList<>(activeRuntime.getTrackedPlayers(viewer));
    }

    public boolean shouldHideTrackedPlayerNames(Player viewer) {
        return activeRuntime != null && activeRuntime.shouldHideTrackedPlayerNames(viewer);
    }

    public void handleLiveTeamChange(Player player, ColorTeam team) {
        if (activeRuntime instanceof TeamBeaconMatchRuntime teamRuntime) {
            teamRuntime.handleLiveTeamChange(player, team);
        }
    }

    public String serializeHudLine(String left, String right) {
        return serializeRawHudLine(Component.text().append(Component.text(left, NamedTextColor.GOLD))
            .append(Component.text(" " + right, NamedTextColor.AQUA)).build());
    }

    public String serializeRawHudLine(Component component) {
        return legacySerializer.serialize(component);
    }

    public String serializeCountdownLine(int secondsLeft) {
        return serializeRawHudLine(Component.text().append(Component.text("Refresh: ", NamedTextColor.GRAY))
            .append(Component.text(secondsLeft + "s", NamedTextColor.GREEN)).build());
    }

    String getDistanceRange(int distance, int granularity) {
        int lowerBound = (distance / granularity) * granularity;
        int upperBound = lowerBound + granularity;
        if (lowerBound == upperBound) {
            return Integer.toString(lowerBound);
        }
        return lowerBound + "-" + upperBound;
    }

    Location getRandomLocationInCube() {
        Vector gameCenter = getCurrentGameCenter();
        double gameRadius = getCurrentGameRadius();
        double x = gameCenter.getX() + (random.nextDouble() * 2 - 1) * gameRadius;
        double y = gameCenter.getY() + (random.nextDouble() * 2 - 1) * gameRadius;
        double z = gameCenter.getZ() + (random.nextDouble() * 2 - 1) * gameRadius;
        World world = getConfiguredWorld();
        return new Location(world, x, y, z);
    }

    Location getRandomLocationInSphere() {
        Vector gameCenter = getCurrentGameCenter();
        double gameRadius = getCurrentGameRadius();
        while (true) {
            Location location = getRandomLocationInCube();
            if (location.distance(gameCenter.toLocation(location.getWorld())) <= gameRadius
                && location.getY() >= location.getWorld().getMinHeight()
                && location.getY() <= location.getWorld().getMaxHeight()) {
                return location;
            }
        }
    }

    private void setupArenaBorder() {
        restoreArenaBorder();
        activeArenaCenter = null;
        activeArenaSpawnRadius = -1.0;
        if (!gameConfig.isArenaBorderEnabled()) {
            return;
        }

        World world = getConfiguredWorld();

        WorldBorder border = world.getWorldBorder();
        originalWorldBorderState = new WorldBorderState(world, border);

        Location center = chooseArenaBorderCenter(world);
        double initialSize = Math.max(1.0, gameConfig.getArenaBorderInitialSize());
        double finalSize = Math.max(1.0, Math.min(initialSize, gameConfig.getArenaBorderFinalSize()));
        long timeToFinal = Math.max(0L, gameConfig.getArenaBorderTimeToFinalSeconds());

        border.setCenter(center);
        border.setSize(initialSize);
        if (timeToFinal > 0 && initialSize != finalSize) {
            border.changeSize(finalSize, timeToFinal);
        }

        activeArenaCenter = center;
        activeArenaSpawnRadius = Math.max(1.0, Math.min(gameConfig.getGameRadius(), initialSize / 2.0 - 1.0));
    }

    private void restoreArenaBorder() {
        activeArenaCenter = null;
        activeArenaSpawnRadius = -1.0;
        if (originalWorldBorderState == null) {
            return;
        }

        World world = Bukkit.getWorld(originalWorldBorderState.worldName);
        if (world == null) {
            originalWorldBorderState = null;
            return;
        }

        WorldBorder border = world.getWorldBorder();
        border.setCenter(originalWorldBorderState.centerX, originalWorldBorderState.centerZ);
        border.setSize(originalWorldBorderState.size);
        border.setDamageAmount(originalWorldBorderState.damageAmount);
        border.setDamageBuffer(originalWorldBorderState.damageBuffer);
        border.setWarningDistance(originalWorldBorderState.warningDistance);
        border.setWarningTimeTicks(originalWorldBorderState.warningTime);
        originalWorldBorderState = null;
    }

    private Location chooseArenaBorderCenter(World world) {
        Vector gameCenter = gameConfig.getGameCenter();
        double radius = Math.max(0.0, gameConfig.getArenaBorderCenterRadius());
        double offsetX = 0.0;
        double offsetZ = 0.0;
        if (radius > 0.0) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(random.nextDouble()) * radius;
            offsetX = Math.cos(angle) * distance;
            offsetZ = Math.sin(angle) * distance;
        }
        return new Location(world, gameCenter.getX() + offsetX, gameCenter.getY(), gameCenter.getZ() + offsetZ);
    }

    @NotNull
    World getConfiguredWorld() {
        final var world = Bukkit.getWorld(gameConfig.getGameWorld());
        if (world == null) {
            throw new IllegalStateException("Game world '" + gameConfig.getGameWorld() + "' is not loaded");
        }
        return world;
    }

    private Vector getCurrentGameCenter() {
        if (gameStatus != GameStatus.NOT_STARTED && activeArenaCenter != null) {
            return getConfiguredWorld().getWorldBorder().getCenter().toVector();
        }
        return gameConfig.getGameCenter();
    }

    private double getCurrentGameRadius() {
        if (gameStatus != GameStatus.NOT_STARTED && activeArenaSpawnRadius > 0.0) {
            World world = getConfiguredWorld();
            double liveBorderRadius = world.getWorldBorder().getSize() / 2.0 - 1.0;
            return Math.max(1.0, Math.min(activeArenaSpawnRadius, liveBorderRadius));
        }
        return gameConfig.getGameRadius();
    }

    public InfiniteCrossbowWeapon getInfiniteCrossbowWeapon() {
        return infiniteCrossbowWeapon;
    }

    public OreshnikWeapon getOreshnikWeapon() {
        return oreshnikWeapon;
    }

    public HimarsWeapon getHimarsWeapon() {
        return himarsWeapon;
    }

    public LocatorWeapon getLocatorWeapon() {
        return locatorWeapon;
    }

    public FPVDroneWeapon getFpvDroneWeapon() {
        return fpvDroneWeapon;
    }

    public RadarWeapon getRadarWeapon() {
        return radarWeapon;
    }

    public GrappleBowWeapon getGrappleBowWeapon() {
        return grappleBowWeapon;
    }

    public void saveGameInventory(Player player) {
        getGameConfig().setGameInventory(player.getInventory().getContents());
    }

    public void loadGameInventory(Player player) {
        if (getGameConfig().getGameInventory() != null) {
            player.getInventory().clear();
            player.getInventory().setContents(getGameConfig().getGameInventory());
        }
    }

    public ASP getAsp() {
        return asp;
    }
}
