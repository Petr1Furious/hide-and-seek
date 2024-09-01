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
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.md_5.bungee.api.ChatColor;

public class Plugin extends JavaPlugin implements Listener {

    private GameStatus gameStatus = GameStatus.NOT_STARTED;
    private boolean gameTeleport = true;
    private Vector gameCenter;
    private int gameRadius;
    private Random random = new Random();
    private boolean checkingGameEnd = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadGameSettings();
        registerCommand("hideandseek");
        registerCommand("hs");
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
        return gameStatus == GameStatus.RUNNING && player.getGameMode() != GameMode.SPECTATOR
                && player.getGameMode() != GameMode.CREATIVE;
    }

    void addPlayerToGame(Player player, boolean teleport) {
        if (teleport) {
            player.teleport(getFirstSolidBlock(getRandomLocationInSphere()).add(0.5, 0, 0.5));
        } else {
            teleportPlayerOnBlock(player);
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE, 1, false, false));
    }

    void startGame(int interval, boolean teleport) {
        gameStatus = GameStatus.RUNNING;
        gameTeleport = teleport;
        getServer().sendMessage(Component.text("Starting game").color(NamedTextColor.GREEN));

        for (var player : getServer().getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SURVIVAL) {
                addPlayerToGame(player, teleport);
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

    void resetPlayer(Player player) {
        var manager = Bukkit.getScoreboardManager();
        player.setScoreboard(manager.getMainScoreboard());
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
            teleportPlayerOnBlock(player);
        }
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
    }

    void resetGame() {
        gameStatus = GameStatus.NOT_STARTED;

        for (var player : getServer().getOnlinePlayers()) {
            resetPlayer(player);
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

    void startGameCommand(int interval, boolean teleport) {
        if (gameStatus == GameStatus.RUNNING) {
            getServer().broadcast(Component.text("Game is already running").color(NamedTextColor.RED));
            return;
        }
        startGame(interval, teleport);
    }

    void registerCommand(String name) {
        var manager = getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = event.registrar();
            var hideAndSeekCommand = Commands.literal(name)
                    .requires(source -> source.getExecutor().hasPermission("hideandseek.command"))
                    .then(
                            Commands.literal("start")
                                    .executes(ctx -> {
                                        startGameCommand(10, true);
                                        return Command.SINGLE_SUCCESS;
                                    })
                                    .then(
                                            Commands.argument("interval", IntegerArgumentType.integer(1))
                                                    .executes(ctx -> {
                                                        int interval = IntegerArgumentType.getInteger(ctx, "interval");
                                                        startGameCommand(interval, true);
                                                        return Command.SINGLE_SUCCESS;
                                                    })
                                                    .then(
                                                            Commands.argument("teleport", BoolArgumentType.bool())
                                                                    .executes(ctx -> {
                                                                        boolean teleport = BoolArgumentType.getBool(ctx,
                                                                                "teleport");
                                                                        startGameCommand(10, teleport);
                                                                        return Command.SINGLE_SUCCESS;
                                                                    }))))
                    .then(
                            Commands.literal("stop")
                                    .executes(ctx -> {
                                        if (gameStatus == GameStatus.NOT_STARTED) {
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
                    .then(
                            Commands.literal("join")
                                    .requires(source -> source.getExecutor() instanceof Player)
                                    .executes(ctx -> {
                                        Player player = (Player) ctx.getSource().getExecutor();
                                        if (isPlayerInGame(player)) {
                                            player.sendMessage(Component.text("You are already in the game")
                                                    .color(NamedTextColor.RED));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        player.sendMessage(
                                                Component.text("You joined the game").color(NamedTextColor.GREEN));
                                        addPlayerToGame(player, gameTeleport);
                                        return Command.SINGLE_SUCCESS;
                                    }))
                    .then(
                            Commands.literal("join")
                                    .requires(source -> source.getExecutor().hasPermission("hideandseek.command"))
                                    .then(
                                            Commands.argument("player", ArgumentTypes.player())
                                                    .executes(ctx -> {
                                                        String playerName = StringArgumentType.getString(ctx, "player");
                                                        var player = getServer().getPlayer(playerName);
                                                        if (isPlayerInGame(player)) {
                                                            ctx.getSource().getExecutor().sendMessage(Component
                                                                    .text(playerName).color(NamedTextColor.RED).append(
                                                                            Component.text(" is already in the game")
                                                                                    .color(NamedTextColor.RED)));
                                                            return Command.SINGLE_SUCCESS;
                                                        }
                                                        player.sendMessage(Component.text("You joined the game")
                                                                .color(NamedTextColor.GREEN));
                                                        addPlayerToGame(player, gameTeleport);
                                                        return Command.SINGLE_SUCCESS;
                                                    })))
                    .build();
            commands.register(hideAndSeekCommand);
        });
    }

    void endGame() {
        gameStatus = GameStatus.ENDED;
        var manager = Bukkit.getScoreboardManager();
        for (var player : getServer().getOnlinePlayers()) {
            player.setScoreboard(manager.getMainScoreboard());
        }
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
                getServer().broadcast(
                        Component.text("Game over! ").color(NamedTextColor.RED)
                                .append(Component.text(lastPlayer.getName()).color(NamedTextColor.BLUE))
                                .append(Component.text(" wins!").color(NamedTextColor.RED)));
            }
            endGame();
        }
    }

    void registerEvents() {
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerDeath(PlayerDeathEvent event) {
                if (gameStatus == GameStatus.RUNNING) {
                    Player player = event.getPlayer();
                    event.setCancelled(isPlayerInGame(player));
                    getServer().broadcast(event.deathMessage().color(NamedTextColor.GRAY));
                    player.setGameMode(GameMode.SPECTATOR);

                    if (!checkingGameEnd) {
                        checkingGameEnd = true;
                        getServer().getScheduler().scheduleSyncDelayedTask(Plugin.this, () -> {
                            checkGameEnd();
                        }, 60);
                    }
                }
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
