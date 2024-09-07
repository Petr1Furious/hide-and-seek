package me.petr1furious.hideandseek;

import java.util.List;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.CrossbowMeta;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class CommandHandler {
    private final HideAndSeek plugin;

    public CommandHandler(HideAndSeek plugin) {
        this.plugin = plugin;
    }

    public void registerCommands() {
        registerCommand("hideandseek");
        registerCommand("hs");
    }

    private void saveGameSettings() {
        plugin.getGameConfig().save();
        plugin.saveConfig();
    }

    void startGameCommand(Entity executor, int interval, boolean teleport) {
        if (plugin.getGameStatus() == GameStatus.RUNNING) {
            executor.sendMessage(Component.text("Game is already running").color(NamedTextColor.RED));
            return;
        }
        plugin.startGame(interval, teleport);
    }

    void joinGameCommand(Player executor, Player target) {
        if (plugin.isPlayerInGame(target)) {
            executor.sendMessage(Component.text(target.getName()).color(NamedTextColor.AQUA)
                .append(Component.text(" is already in the game").color(NamedTextColor.RED)));
            return;
        }
        if (plugin.getGameStatus() == GameStatus.NOT_STARTED) {
            executor.sendMessage(Component.text("Game is not running").color(NamedTextColor.RED));
            return;
        }
        target.sendMessage(Component.text("You joined the game").color(NamedTextColor.GREEN));
        plugin.addPlayerToGame(target, plugin.getGameTeleport());
    }

    void giveInfiniteCrossbow(List<Player> players) {
        var crossbow = new org.bukkit.inventory.ItemStack(org.bukkit.Material.CROSSBOW);
        var meta = crossbow.getItemMeta();
        meta.displayName(Component.text("Infinite Crossbow").color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.setCustomModelData(1);
        meta.setUnbreakable(true);
        meta.lore(List.of(Component.text("Always loaded").color(NamedTextColor.GRAY)));
        meta.setEnchantmentGlintOverride(true);
        var crossbowMeta = (CrossbowMeta) crossbow.getItemMeta();
        crossbowMeta.addChargedProjectile(new org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW));
        crossbow.setItemMeta(meta);

        for (var player : players) {
            player.getInventory().addItem(crossbow);
        }
    }

    void registerCommand(String name) {
        var manager = plugin.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = event.registrar();
            var hideAndSeekCommand = Commands.literal(name)
                .requires(source -> source.getExecutor().hasPermission("hideandseek.command"))
                .then(Commands.literal("start").executes(ctx -> {
                    startGameCommand(ctx.getSource().getExecutor(), 10, true);
                    return Command.SINGLE_SUCCESS;
                }).then(Commands.argument("interval", IntegerArgumentType.integer(1)).executes(ctx -> {
                    int interval = IntegerArgumentType.getInteger(ctx, "interval");
                    startGameCommand(ctx.getSource().getExecutor(), interval, true);
                    return Command.SINGLE_SUCCESS;
                }).then(Commands.argument("teleport", BoolArgumentType.bool()).executes(ctx -> {
                    int interval = IntegerArgumentType.getInteger(ctx, "interval");
                    boolean teleport = BoolArgumentType.getBool(ctx, "teleport");
                    startGameCommand(ctx.getSource().getExecutor(), interval, teleport);
                    return Command.SINGLE_SUCCESS;
                })))).then(Commands.literal("stop").executes(ctx -> {
                    if (plugin.getGameStatus() == GameStatus.NOT_STARTED) {
                        ctx.getSource().getExecutor()
                            .sendMessage(Component.text("Game is not running").color(NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }
                    plugin.stopGame();
                    return Command.SINGLE_SUCCESS;
                })).then(Commands.literal("setcenter").requires(source -> source.getExecutor() instanceof Player)
                    .executes(ctx -> {
                        var player = (Player) ctx.getSource().getExecutor();
                        plugin.getGameConfig().setGameCenter(player.getWorld().getSpawnLocation().toVector());
                        saveGameSettings();
                        player.sendMessage(
                            Component.text("Game center set to your location").color(NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("setradius")
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1)).executes(ctx -> {
                        int gameRadius = IntegerArgumentType.getInteger(ctx, "radius");
                        plugin.getGameConfig().setGameRadius(gameRadius);
                        saveGameSettings();
                        ctx.getSource().getExecutor().sendMessage(
                            Component.text("Game radius set to " + gameRadius).color(NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(Commands.literal("setexplosions")
                    .then(Commands.argument("enable", BoolArgumentType.bool()).executes(ctx -> {
                        boolean enableExplosions = BoolArgumentType.getBool(ctx, "enable");
                        plugin.getGameConfig().setEnableExplosions(enableExplosions);
                        saveGameSettings();
                        ctx.getSource().getExecutor()
                            .sendMessage(Component.text("Explosions " + (enableExplosions ? "enabled" : "disabled"))
                                .color(NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(Commands.literal("setexplosionpower")
                    .then(Commands.argument("power", DoubleArgumentType.doubleArg()).executes(ctx -> {
                        double explosionPower = DoubleArgumentType.getDouble(ctx, "power");
                        plugin.getGameConfig().setExplosionPower(explosionPower);
                        saveGameSettings();
                        ctx.getSource().getExecutor().sendMessage(
                            Component.text("Explosion power set to " + explosionPower).color(NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(Commands.literal("setlifts")
                    .then(Commands.argument("enable", BoolArgumentType.bool()).executes(ctx -> {
                        boolean enableLifts = BoolArgumentType.getBool(ctx, "enable");
                        plugin.getGameConfig().setEnableLifts(enableLifts);
                        saveGameSettings();
                        ctx.getSource().getExecutor().sendMessage(Component
                            .text("Lifts " + (enableLifts ? "enabled" : "disabled")).color(NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(Commands.literal("join").requires(source -> source.getExecutor() instanceof Player)
                    .executes(ctx -> {
                        var player = (Player) ctx.getSource().getExecutor();
                        joinGameCommand(player, player);
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("player", ArgumentTypes.player()).executes(ctx -> {
                        var player = (Player) ctx.getSource().getExecutor();
                        Player target = ctx.getArgument("player", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource()).getFirst();
                        joinGameCommand(player, target);
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(Commands.literal("give").requires(source -> source.getExecutor() instanceof Player)
                    .then(Commands.argument("players", ArgumentTypes.players())
                        .then(Commands.argument("item", StringArgumentType.string()).suggests((ctx, builder) -> {
                            builder.suggest("infinite_crossbow");
                            return builder.buildFuture();
                        }).executes(ctx -> {
                            String item = StringArgumentType.getString(ctx, "item");
                            List<Player> players = ctx.getArgument("players", PlayerSelectorArgumentResolver.class)
                                .resolve(ctx.getSource());
                            if (item.equals("infinite_crossbow")) {
                                giveInfiniteCrossbow(players);
                            }
                            ctx.getSource().getExecutor().sendMessage(Component.text("Given item to players")
                                .color(NamedTextColor.GREEN));
                            return Command.SINGLE_SUCCESS;
                        }))))
                .then(Commands.literal("reload").executes(ctx -> {
                    plugin.reloadConfig();
                    ctx.getSource().getExecutor().sendMessage(Component.text("Config reloaded").color(NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
            commands.register(hideAndSeekCommand);
        });
    }
}
