package me.petr1furious.hideandseek;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.util.Vector;

import java.util.Random;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.md_5.bungee.api.ChatColor;

public class HideAndSeek extends JavaPlugin implements Listener {

    private CommandHandler commandHandler;

    private Random random = new Random();

    private GameStatus gameStatus = GameStatus.NOT_STARTED;
    private boolean gameTeleport = true;

    private boolean checkingGameEnd = false;

    private GameConfig gameConfig;

    private LiftHandler liftHandler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        gameConfig = new GameConfig(getConfig());
        commandHandler = new CommandHandler(this);
        commandHandler.registerCommands();
        liftHandler = new LiftHandler();
        registerEvents();
    }

    @Override
    public void onDisable() {
    }

    public GameStatus getGameStatus() {
        return gameStatus;
    }

    public GameConfig getGameConfig() {
        return gameConfig;
    }

    boolean isPlayerInGame(Player player) {
        return gameStatus == GameStatus.RUNNING && player.getGameMode() != GameMode.SPECTATOR
            && player.getGameMode() != GameMode.CREATIVE;
    }

    boolean getGameTeleport() {
        return gameTeleport;
    }

    void addPlayerToGame(Player player, boolean teleport) {
        if (teleport) {
            boolean success = false;
            for (int i = 0; i < 100; i++) {
                Location location = Utils.getFirstSolidBlock(getRandomLocationInSphere());
                if (location != null) {
                    player.teleport(location.add(0.5, 0, 0.5));
                    success = true;
                    break;
                }
            }
            if (!success) {
                Utils.teleportPlayerOnBlock(player);
            }
        } else {
            Utils.teleportPlayerOnBlock(player);
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY,
            Integer.MAX_VALUE, 1, false, false));
        player.setHealth(20);
        player.setFoodLevel(20);
        if (this.gameStatus == GameStatus.ENDED) {
            this.gameStatus = GameStatus.RUNNING;
        }
    }

    void startGame(boolean teleport) {
        gameStatus = GameStatus.RUNNING;
        gameTeleport = teleport;
        checkingGameEnd = false;
        getServer().sendMessage(Component.text("Starting game").color(NamedTextColor.GREEN));

        for (var player : getServer().getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SURVIVAL) {
                addPlayerToGame(player, teleport);
            }
        }

        getServer().getScheduler().cancelTasks(this);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            updateDistances();
        }, 0, 80);
    }

    void stopGame() {
        getServer().sendMessage(Component.text("Stopping game").color(NamedTextColor.RED));
        resetGame();
    }

    void resetPlayer(Player player) {
        var manager = Bukkit.getScoreboardManager();
        player.setScoreboard(manager.getMainScoreboard());
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
            Utils.teleportPlayerOnBlock(player);
        }
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
    }

    void resetGame() {
        gameStatus = GameStatus.NOT_STARTED;

        getServer().getScheduler().cancelTasks(this);

        for (var player : getServer().getOnlinePlayers()) {
            resetPlayer(player);
        }
    }

    void endGame() {
        gameStatus = GameStatus.ENDED;
    }

    void checkGameEnd() {
        checkingGameEnd = false;

        var playersInGame = 0;
        Player lastPlayer = null;
        for (var player : getServer().getOnlinePlayers()) {
            if (isPlayerInGame(player)) {
                playersInGame++;
                lastPlayer = player;
            }
        }

        if (playersInGame <= 1) {
            if (lastPlayer == null) {
                getServer().broadcast(Component.text("Game over! No players left!").color(NamedTextColor.RED));
            } else {
                getServer().broadcast(Component.text("Game over! ").color(NamedTextColor.RED)
                    .append(Component.text(lastPlayer.getName()).color(NamedTextColor.BLUE))
                    .append(Component.text(" wins!").color(NamedTextColor.RED)));
            }
            endGame();
        }
    }

    boolean checkForInfiniteCrossbow(org.bukkit.inventory.ItemStack item) {
        if (item == null) {
            return false;
        }
        if (item.getType() == org.bukkit.Material.CROSSBOW && item.getItemMeta().hasCustomModelData()
            && item.getItemMeta().getCustomModelData() == 1) {
            return true;
        }
        return false;
    }

    void setCrossbowMetaLore(ItemMeta meta, int projectiles) {
        List<Component> metaLore = new java.util.ArrayList<>();
        metaLore.add(Component.text("Crossbow with infinite ammo").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (gameConfig.getMaxLoadedCrossbowProjectiles() > 1) {
            metaLore.add(Component.text("Loaded: " + projectiles + "/" + gameConfig.getMaxLoadedCrossbowProjectiles())
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(metaLore);
    }

    void registerEvents() {
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerDeath(PlayerDeathEvent event) {
                if (gameStatus != GameStatus.RUNNING) {
                    return;
                }

                Player player = event.getPlayer();
                event.setCancelled(isPlayerInGame(player));
                getServer().broadcast(event.deathMessage().color(NamedTextColor.GRAY));
                player.setGameMode(GameMode.SPECTATOR);

                if (!checkingGameEnd) {
                    checkingGameEnd = true;
                    getServer().getScheduler().scheduleSyncDelayedTask(HideAndSeek.this, () -> {
                        checkGameEnd();
                    }, 60);
                }
            }

            @EventHandler
            public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                if (gameStatus != GameStatus.RUNNING) {
                    return;
                }

                getServer().getScheduler().scheduleSyncDelayedTask(HideAndSeek.this, () -> {
                    updateDistances();
                }, 1);
            }

            @EventHandler
            public void onProjectileHit(ProjectileHitEvent event) {
                if (!gameConfig.isEnableExplosions()) {
                    return;
                }

                if (event.getEntity() instanceof Arrow) {
                    var arrow = (Arrow) event.getEntity();
                    Location hitLocation = arrow.getLocation();
                    Utils.spawnExplosion(hitLocation, gameConfig.getExplosionPower());
                    event.getEntity().remove();
                }
            }

            @EventHandler
            public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
                if (!gameConfig.isEnableExplosions()) {
                    return;
                }

                if (event.getDamager() instanceof Arrow) {
                    var arrow = (Arrow) event.getDamager();
                    Location hitLocation = event.getEntity().getLocation();
                    Utils.spawnExplosion(hitLocation, gameConfig.getExplosionPower());

                    var entity = event.getEntity();
                    var shooter = Utils.getEntityShooter(arrow);
                    if (entity instanceof Damageable && shooter != null) {
                        var damageable = (Damageable) entity;
                        damageable.damage(100, DamageSource.builder(DamageType.EXPLOSION)
                            .withDirectEntity(shooter).build());
                        event.setCancelled(true);
                    }
                }
            }

            @EventHandler
            public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
                boolean isLeftClick = event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_AIR
                    || event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK;
                var player = event.getPlayer();
                if (checkForInfiniteCrossbow(event.getItem())) {
                    var crossbow = event.getItem();
                    var meta = (CrossbowMeta) crossbow.getItemMeta();
                    int count = meta.getChargedProjectiles().size();
                    if (count < gameConfig.getMaxLoadedCrossbowProjectiles()) {
                        meta.addChargedProjectile(new org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW));
                        setCrossbowMetaLore(meta, count + 1);
                        crossbow.setItemMeta(meta);

                        if (isLeftClick) {
                            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BIT, 0.1f, 2f);
                        }
                    } else {
                        if (isLeftClick) {
                            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BIT, 0.1f, 0.5f);
                        }
                    }
                }
            }

            @EventHandler
            public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
                liftHandler.handleLift(event.getPlayer(), gameConfig.getLiftMaterial(), gameConfig.isEnableLifts());
            }
        }, this);
    }

    void updateDistances() {
        if (gameStatus != GameStatus.RUNNING) {
            return;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();

        for (Player player1 : getServer().getOnlinePlayers()) {
            Scoreboard scoreboard = manager.getNewScoreboard();
            player1.setScoreboard(scoreboard);
            for (String entry : scoreboard.getEntries()) {
                scoreboard.resetScores(entry);
            }

            Objective playerObjective = scoreboard.getObjective(player1.getName());
            if (playerObjective == null) {
                playerObjective = scoreboard.registerNewObjective(player1.getName(), Criteria.DUMMY,
                    Component.text("Distances").color(NamedTextColor.GOLD));
                playerObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
            }

            for (Player player2 : getServer().getOnlinePlayers()) {
                if (player1 == player2 || !isPlayerInGame(player2))
                    continue;

                double distance = player1.getLocation().distance(player2.getLocation());
                int roundedDistance = (int) (Math.round(distance / 50.0) * 50);
                String distanceRange = getDistanceRange(roundedDistance);
                playerObjective.getScore(ChatColor.GOLD + distanceRange + ChatColor.AQUA + " " + player2.getName())
                    .setScore(roundedDistance);
            }
        }
    }

    String getDistanceRange(int distance) {
        int lowerBound = (distance / 50) * 50;
        int upperBound = lowerBound + 50;
        return lowerBound + "-" + upperBound;
    }

    Location getRandomLocationInCube() {
        Vector gameCenter = gameConfig.getGameCenter();
        double gameRadius = gameConfig.getGameRadius();
        double x = gameCenter.getX() + (random.nextDouble() * 2 - 1) * gameRadius;
        double y = gameCenter.getY() + (random.nextDouble() * 2 - 1) * gameRadius;
        double z = gameCenter.getZ() + (random.nextDouble() * 2 - 1) * gameRadius;
        return new Location(Bukkit.getWorlds().get(0), x, y, z);
    }

    Location getRandomLocationInSphere() {
        Vector gameCenter = gameConfig.getGameCenter();
        double gameRadius = gameConfig.getGameRadius();
        while (true) {
            Location location = getRandomLocationInCube();
            if (location.distance(gameCenter.toLocation(location.getWorld())) <= gameRadius
                && location.getY() >= location.getWorld().getMinHeight()
                && location.getY() <= location.getWorld().getMaxHeight()) {
                return location;
            }
        }
    }
}
