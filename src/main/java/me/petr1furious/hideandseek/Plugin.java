package me.petr1furious.hideandseek;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.util.Vector;
import java.util.Random;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.md_5.bungee.api.ChatColor;

public class Plugin extends JavaPlugin implements Listener {

    private boolean gameRunning = false;
    private Vector gameCenter;
    private int gameRadius;
    private Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadGameSettings();
        registerCommands();
        registerEvents();
    }

    @Override
    public void onDisable() {
    }

    private void loadGameSettings() {
        FileConfiguration config = getConfig();
        gameCenter = config.getVector("gameCenter", new Vector(0, 0, 0));
        gameRadius = config.getInt("gameRadius", 100);
    }

    private void saveGameSettings() {
        FileConfiguration config = getConfig();
        config.set("gameCenter", gameCenter);
        config.set("gameRadius", gameRadius);
        saveConfig();
    }

    boolean isPlayerInGame(Player player) {
        return gameRunning && player.getGameMode() != GameMode.SPECTATOR && player.getGameMode() != GameMode.CREATIVE;
    }

    void startGame(int interval) {
        gameRunning = true;
        getServer().sendMessage(Component.text("Starting game").color(NamedTextColor.GREEN));

        for (var player : getServer().getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SURVIVAL) {
                player.teleport(getFirstSolidBlock(getRandomLocationInSphere()).add(0.5, 0, 0.5));
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY,
                        Integer.MAX_VALUE, 1, false, false));
            }
        }

        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            updateDistances();
        }, 0, interval * 20);
    }

    void stopGame() {
        getServer().sendMessage(Component.text("Stopping game").color(NamedTextColor.RED));
        resetGame();
    }

    void resetGame() {
        gameRunning = false;

        var manager = Bukkit.getScoreboardManager();

        for (var player : getServer().getOnlinePlayers()) {
            player.setScoreboard(manager.getMainScoreboard());
            if (player.getGameMode() == GameMode.SPECTATOR) {
                teleportPlayerOnBlock(player);
                player.setGameMode(GameMode.SURVIVAL);
            }
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        }
    }

    boolean playerPassable(Location location) {
        return location.getBlock().isPassable() && location.add(0, 1, 0).getBlock().isPassable();
    }

    Location getFirstSolidBlock(Location location) {
        var world = location.getWorld();
        var y = location.getBlockY();

        while (y > world.getMinHeight()) {
            if (!playerPassable(new Location(world, location.getBlockX(), y, location.getBlockZ()))) {
                break;
            }
            y--;
        }

        if (y == world.getMinHeight()) {
            y = world.getMaxHeight();
            while (y > world.getMinHeight()) {
                if (!playerPassable(new Location(world, location.getBlockX(), y, location.getBlockZ()))) {
                    break;
                }
                y--;
            }
        }

        while (y < world.getMaxHeight()) {
            if (playerPassable(new Location(world, location.getBlockX(), y, location.getBlockZ()))) {
                break;
            }
            y++;
        }

        return new Location(world, location.getBlockX(), y, location.getBlockZ());
    }

    void teleportPlayerOnBlock(Player player) {
        var location = getFirstSolidBlock(player.getLocation());
        if (location != null) {
            location.add(0.5, 0, 0.5);
            location.setYaw(player.getLocation().getYaw());
            location.setPitch(player.getLocation().getPitch());
            player.teleport(location);
        }
    }

    void registerCommands() {
        var manager = getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = event.registrar();
            var hideAndSeekCommand = Commands.literal("hideandseek")
                    .requires(source -> source.getExecutor().hasPermission("hideandseek.command"))
                    .then(
                            Commands.literal("start")
                                    .executes(ctx -> {
                                        if (gameRunning) {
                                            ctx.getSource().getExecutor().sendMessage(Component
                                                    .text("Game is already running").color(NamedTextColor.RED));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        startGame(10);
                                        return Command.SINGLE_SUCCESS;
                                    })
                                    .then(
                                            Commands.argument("interval", IntegerArgumentType.integer(1))
                                                    .executes(ctx -> {
                                                        if (gameRunning) {
                                                            ctx.getSource().getExecutor().sendMessage(Component
                                                                    .text("Game is already running")
                                                                    .color(NamedTextColor.RED));
                                                            return Command.SINGLE_SUCCESS;
                                                        }
                                                        int interval = IntegerArgumentType.getInteger(ctx, "interval");
                                                        startGame(interval);
                                                        return Command.SINGLE_SUCCESS;
                                                    })))
                    .then(
                            Commands.literal("stop")
                                    .executes(ctx -> {
                                        if (!gameRunning) {
                                            ctx.getSource().getExecutor().sendMessage(Component
                                                    .text("Game is not running").color(NamedTextColor.RED));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        stopGame();
                                        return Command.SINGLE_SUCCESS;
                                    }))
                    .then(
                            Commands.literal("setcenter")
                                    .requires(source -> source.getExecutor() instanceof Player)
                                    .executes(ctx -> {
                                        var player = (Player) ctx.getSource().getExecutor();
                                        gameCenter = player.getWorld().getSpawnLocation().toVector();
                                        saveGameSettings();
                                        player.sendMessage(Component.text("Game center set to your location")
                                                .color(NamedTextColor.GREEN));
                                        return Command.SINGLE_SUCCESS;
                                    }))
                    .then(
                            Commands.literal("setradius")
                                    .then(
                                            Commands.argument("radius", IntegerArgumentType.integer(1))
                                                    .executes(ctx -> {
                                                        gameRadius = IntegerArgumentType.getInteger(ctx, "radius");
                                                        saveGameSettings();
                                                        ctx.getSource().getExecutor()
                                                                .sendMessage(Component
                                                                        .text("Game radius set to " + gameRadius)
                                                                        .color(NamedTextColor.GREEN));
                                                        return Command.SINGLE_SUCCESS;
                                                    })))
                    .build();
            commands.register(hideAndSeekCommand);
            commands.register(Commands.literal("hs").redirect(hideAndSeekCommand).build());
        });
    }

    void registerEvents() {
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerDeath(PlayerDeathEvent event) {
                if (gameRunning) {
                    event.setCancelled(isPlayerInGame(event.getPlayer()));
                    getServer().broadcast(event.deathMessage().color(NamedTextColor.GRAY));
                    event.getPlayer().setGameMode(GameMode.SPECTATOR);

                    var playersInGame = 0;
                    Player lastPlayer = null;
                    for (var player : getServer().getOnlinePlayers()) {
                        if (isPlayerInGame(player) && player != event.getPlayer()) {
                            playersInGame++;
                            lastPlayer = player;
                        }
                    }

                    if (playersInGame <= 1) {
                        if (lastPlayer == null) {
                            getServer()
                                    .broadcast(Component.text("Game over! No players left!").color(NamedTextColor.RED));
                        } else {
                            getServer().broadcast(
                                    Component.text("Game over! ")
                                            .color(NamedTextColor.RED)
                                            .append(Component.text(lastPlayer.getName()).color(NamedTextColor.BLUE))
                                            .append(Component.text(" wins!").color(NamedTextColor.RED)));
                        }
                        resetGame();
                    }
                }
            }
        }, this);
    }

    void updateDistances() {
        if (!gameRunning) {
            return;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();

        for (Player player1 : getServer().getOnlinePlayers()) {
            if (!isPlayerInGame(player1))
                continue;

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
        double x = gameCenter.getX() + (random.nextDouble() * 2 - 1) * gameRadius;
        double y = gameCenter.getY() + (random.nextDouble() * 2 - 1) * gameRadius;
        double z = gameCenter.getZ() + (random.nextDouble() * 2 - 1) * gameRadius;
        return new Location(Bukkit.getWorlds().get(0), x, y, z);
    }

    Location getRandomLocationInSphere() {
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
