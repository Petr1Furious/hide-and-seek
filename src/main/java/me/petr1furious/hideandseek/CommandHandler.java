package me.petr1furious.hideandseek;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.petr1furious.hideandseek.weapons.WeaponConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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

    void startGameCommand(CommandSender sender) {
        String error = plugin.startGame(null);
        if (error != null) {
            sender.sendMessage(Component.text(error).color(NamedTextColor.RED));
        }
    }

    void joinGameCommand(CommandSender sender, Player target) {
        if (plugin.getGameStatus() == GameStatus.NOT_STARTED) {
            sender.sendMessage(Component.text("Game is not running").color(NamedTextColor.RED));
            return;
        }
        String error = plugin.addPlayerToGame(target, true);
        if (error != null) {
            sender.sendMessage(Component.text(error).color(NamedTextColor.RED));
        }
    }

    void giveItems(List<Player> players, ItemStack item) {
        for (var player : players) {
            player.getInventory().addItem(item);
        }
    }

    private ItemStack createItemStack(String item, GameConfig config, int count) {
        String key = item.toLowerCase();
        if (key.equals("infinite_crossbow")) {
            return plugin.getInfiniteCrossbowWeapon().createItem(count);
        } else if (key.equals("oreshnik")) {
            return plugin.getOreshnikWeapon().createItem(count);
        } else if (key.equals("himars")) {
            return plugin.getHimarsWeapon().createItem(count);
        } else if (key.equals("locator")) {
            return plugin.getLocatorWeapon().createItem(count);
        } else if (key.equals("fpv_drone") || key.equals("fpv")) {
            return plugin.getFpvDroneWeapon().createItem(count);
        } else if (key.equals("radar") || key.equals("r")) {
            return plugin.getRadarWeapon().createItem(count);
        } else if (key.equals("grapple_bow") || key.equals("grapple") || key.equals("gb")) {
            return plugin.getGrappleBowWeapon().createItem(count);
        }
        return null;
    }

    private void suggestTeamColors(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
        com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        for (ColorTeam team : ColorTeam.values()) {
            builder.suggest(team.getId());
        }
    }

    private ColorTeam getRequiredColorTeam(String raw) {
        return ColorTeam.fromId(raw);
    }

    private void setTeam(CommandSender sender, ColorTeam team, List<Player> players) {
        for (Player player : players) {
            plugin.getTeamRegistry().assignPlayer(player.getUniqueId(), team);
            plugin.getServer().sendMessage(plugin.buildTeamJoinMessage(player, team));
            if (plugin.getGameStatus() == GameStatus.RUNNING
                && plugin.getGameConfig().getGameMode() == GameModeType.TEAM_BEACON) {
                plugin.handleLiveTeamChange(player, team);
            }
        }
        sender.sendMessage(Component.text("Assigned " + players.size() + " player(s) to " + team.getDisplayName())
            .color(NamedTextColor.GREEN));
    }

    private void clearTeams(CommandSender sender, List<Player> players) {
        if (plugin.getGameStatus() == GameStatus.RUNNING
            && plugin.getGameConfig().getGameMode() == GameModeType.TEAM_BEACON) {
            sender.sendMessage(
                Component.text("Team leave is only allowed between team-beacon matches").color(NamedTextColor.RED));
            return;
        }
        for (Player player : players) {
            plugin.getTeamRegistry().removePlayer(player.getUniqueId());
        }
        sender.sendMessage(Component.text("Cleared team assignments for " + players.size() + " player(s)")
            .color(NamedTextColor.GREEN));
    }

    private void cycleTeam(CommandSender sender, Player player) {
        List<ColorTeam> colors = Arrays.asList(ColorTeam.values());
        Optional<ColorTeam> current = plugin.getTeamRegistry().getTeam(player);
        ColorTeam next = current.map(team -> colors.get((team.ordinal() + 1) % colors.size())).orElse(ColorTeam.RED);
        plugin.getTeamRegistry().assignPlayer(player.getUniqueId(), next);
        plugin.getServer().sendMessage(plugin.buildTeamJoinMessage(player, next));
        if (plugin.getGameStatus() == GameStatus.RUNNING
            && plugin.getGameConfig().getGameMode() == GameModeType.TEAM_BEACON) {
            plugin.handleLiveTeamChange(player, next);
        }
        sender
            .sendMessage(Component.text(player.getName() + " -> " + next.getDisplayName()).color(NamedTextColor.GREEN));
    }

    private void listTeams(CommandSender sender) {
        for (ColorTeam team : ColorTeam.values()) {
            List<String> onlineNames = plugin.getTeamRegistry().getMembers(team).stream()
                .map(uuid -> plugin.getServer().getPlayer(uuid)).filter(player -> player != null && player.isOnline())
                .map(Player::getName).sorted(String::compareToIgnoreCase).toList();
            String beaconInfo = plugin.getTeamRegistry().getBeaconDescription(team);
            sender.sendMessage(Component.text(team.getDisplayName(), team.getTextColor())
                .append(Component.text(": " + onlineNames.size() + " online, " + beaconInfo, NamedTextColor.GRAY)));
            sender.sendMessage(
                Component.text("Players: " + (onlineNames.isEmpty() ? "-" : String.join(", ", onlineNames)),
                    NamedTextColor.DARK_GRAY));
        }
    }

    private void setBeacon(CommandSender sender, Player player, ColorTeam team) {
        var targetBlock = player.getTargetBlockExact(10);
        if (targetBlock == null) {
            sender.sendMessage(Component.text("Look at a block to set the beacon.").color(NamedTextColor.RED));
            return;
        }
        plugin.getTeamRegistry().setBeacon(team, targetBlock.getLocation());
        sender.sendMessage(Component.text(team.getDisplayName() + " beacon set").color(NamedTextColor.GREEN));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildTeamConfigCommand() {
        return Commands.literal("teamconfig")
            .then(Commands.argument("property", StringArgumentType.word()).suggests((ctx, builder) -> {
                for (String property : plugin.getGameConfig().getTeamBeacon().getPropertyNames()) {
                    builder.suggest(property);
                }
                return builder.buildFuture();
            }).executes(ctx -> {
                String property = StringArgumentType.getString(ctx, "property");
                Object currentValue = plugin.getGameConfig().getTeamBeacon().getPropertyValue(property);
                if (currentValue == null) {
                    ctx.getSource().getSender()
                        .sendMessage(Component.text("Unknown team config property").color(NamedTextColor.RED));
                    return Command.SINGLE_SUCCESS;
                }
                ctx.getSource().getSender().sendMessage(
                    Component.text("teamBeacon." + property + " = " + currentValue).color(NamedTextColor.AQUA));
                return Command.SINGLE_SUCCESS;
            }).then(Commands.argument("value", StringArgumentType.greedyString()).executes(ctx -> {
                String property = StringArgumentType.getString(ctx, "property");
                String value = StringArgumentType.getString(ctx, "value");
                TeamBeaconConfig.WeaponSetResult result = plugin.getGameConfig().getTeamBeacon().setProperty(property,
                    value);
                if (result == TeamBeaconConfig.WeaponSetResult.SUCCESS) {
                    saveGameSettings();
                    Object newValue = plugin.getGameConfig().getTeamBeacon().getPropertyValue(property);
                    ctx.getSource().getSender().sendMessage(
                        Component.text("Set teamBeacon." + property + " = " + newValue).color(NamedTextColor.GREEN));
                } else if (result == TeamBeaconConfig.WeaponSetResult.UNKNOWN_PROPERTY) {
                    ctx.getSource().getSender()
                        .sendMessage(Component.text("Unknown team config property").color(NamedTextColor.RED));
                } else if (result == TeamBeaconConfig.WeaponSetResult.PARSE_ERROR) {
                    ctx.getSource().getSender().sendMessage(Component.text("Invalid value").color(NamedTextColor.RED));
                }
                return Command.SINGLE_SUCCESS;
            })));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildModeCommand() {
        return Commands.literal("mode")
            .then(Commands.argument("mode", StringArgumentType.word()).suggests((ctx, builder) -> {
                builder.suggest(GameModeType.CLASSIC.getId());
                builder.suggest(GameModeType.TEAM_BEACON.getId());
                return builder.buildFuture();
            }).executes(ctx -> {
                if (plugin.getGameStatus() == GameStatus.RUNNING) {
                    ctx.getSource().getSender()
                        .sendMessage(Component.text("Stop the game before switching modes").color(NamedTextColor.RED));
                    return Command.SINGLE_SUCCESS;
                }
                GameModeType mode = GameModeType.fromConfig(StringArgumentType.getString(ctx, "mode"));
                plugin.getGameConfig().setGameMode(mode);
                saveGameSettings();
                ctx.getSource().getSender()
                    .sendMessage(Component.text("Game mode set to " + mode.getId()).color(NamedTextColor.GREEN));
                return Command.SINGLE_SUCCESS;
            }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildTeamCommand() {
        return Commands.literal("team").then(Commands.literal("list").executes(ctx -> {
            listTeams(ctx.getSource().getSender());
            return Command.SINGLE_SUCCESS;
        })).then(Commands.literal("join")
            .then(Commands.argument("color", StringArgumentType.word()).suggests((ctx, builder) -> {
                suggestTeamColors(ctx, builder);
                return builder.buildFuture();
            }).then(Commands.argument("players", ArgumentTypes.players()).executes(ctx -> {
                ColorTeam team = getRequiredColorTeam(StringArgumentType.getString(ctx, "color"));
                if (team == null) {
                    ctx.getSource().getSender()
                        .sendMessage(Component.text("Unknown team color").color(NamedTextColor.RED));
                    return Command.SINGLE_SUCCESS;
                }
                List<Player> players = ctx.getArgument("players", PlayerSelectorArgumentResolver.class)
                    .resolve(ctx.getSource());
                setTeam(ctx.getSource().getSender(), team, players);
                return Command.SINGLE_SUCCESS;
            }))))
            .then(Commands.literal("leave").then(Commands.argument("players", ArgumentTypes.players()).executes(ctx -> {
                List<Player> players = ctx.getArgument("players", PlayerSelectorArgumentResolver.class)
                    .resolve(ctx.getSource());
                clearTeams(ctx.getSource().getSender(), players);
                return Command.SINGLE_SUCCESS;
            })))
            .then(Commands.literal("cycle").then(Commands.argument("player", ArgumentTypes.player()).executes(ctx -> {
                Player player = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource())
                    .getFirst();
                cycleTeam(ctx.getSource().getSender(), player);
                return Command.SINGLE_SUCCESS;
            }))).then(Commands.literal("swap")
                .then(Commands.argument("color1", StringArgumentType.word()).suggests((ctx, builder) -> {
                    suggestTeamColors(ctx, builder);
                    return builder.buildFuture();
                }).then(Commands.argument("color2", StringArgumentType.word()).suggests((ctx, builder) -> {
                    suggestTeamColors(ctx, builder);
                    return builder.buildFuture();
                }).executes(ctx -> {
                    ColorTeam first = getRequiredColorTeam(StringArgumentType.getString(ctx, "color1"));
                    ColorTeam second = getRequiredColorTeam(StringArgumentType.getString(ctx, "color2"));
                    if (first == null || second == null) {
                        ctx.getSource().getSender()
                            .sendMessage(Component.text("Unknown team color").color(NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }
                    List<Player> affectedPlayers = new java.util.ArrayList<>(
                        plugin.getServer().getOnlinePlayers().stream().filter(online -> plugin.getTeamRegistry()
                            .getTeam(online).map(team -> team == first || team == second).orElse(false)).toList());
                    plugin.getTeamRegistry().swapTeams(first, second);
                    if (plugin.getGameStatus() == GameStatus.RUNNING
                        && plugin.getGameConfig().getGameMode() == GameModeType.TEAM_BEACON) {
                        for (Player online : affectedPlayers) {
                            plugin.getTeamRegistry().getTeam(online).ifPresent(team -> {
                                plugin.getServer().sendMessage(plugin.buildTeamJoinMessage(online, team));
                                plugin.handleLiveTeamChange(online, team);
                            });
                        }
                    } else {
                        for (Player online : affectedPlayers) {
                            plugin.getTeamRegistry().getTeam(online).ifPresent(
                                team -> plugin.getServer().sendMessage(plugin.buildTeamJoinMessage(online, team)));
                        }
                    }
                    ctx.getSource().getSender().sendMessage(
                        Component.text("Swapped " + first.getDisplayName() + " and " + second.getDisplayName())
                            .color(NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                }))))
            .then(buildTeamBeaconCommand());
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildTeamBeaconCommand() {
        return Commands.literal("beacon")
            .then(Commands.literal("set").requires(source -> source.getExecutor() instanceof Player)
                .then(Commands.argument("color", StringArgumentType.word()).suggests((ctx, builder) -> {
                    suggestTeamColors(ctx, builder);
                    return builder.buildFuture();
                }).executes(ctx -> {
                    ColorTeam team = getRequiredColorTeam(StringArgumentType.getString(ctx, "color"));
                    if (team == null) {
                        ctx.getSource().getSender()
                            .sendMessage(Component.text("Unknown team color").color(NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }
                    setBeacon(ctx.getSource().getSender(), (Player) ctx.getSource().getExecutor(), team);
                    return Command.SINGLE_SUCCESS;
                })))
            .then(Commands.literal("clear")
                .then(Commands.argument("color", StringArgumentType.word()).suggests((ctx, builder) -> {
                    suggestTeamColors(ctx, builder);
                    return builder.buildFuture();
                }).executes(ctx -> {
                    ColorTeam team = getRequiredColorTeam(StringArgumentType.getString(ctx, "color"));
                    if (team == null) {
                        ctx.getSource().getSender()
                            .sendMessage(Component.text("Unknown team color").color(NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }
                    plugin.getTeamRegistry().clearBeacon(team);
                    ctx.getSource().getSender().sendMessage(
                        Component.text(team.getDisplayName() + " beacon cleared").color(NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                })))
            .then(Commands.literal("info").executes(ctx -> {
                listTeams(ctx.getSource().getSender());
                return Command.SINGLE_SUCCESS;
            }).then(Commands.argument("color", StringArgumentType.word()).suggests((ctx, builder) -> {
                suggestTeamColors(ctx, builder);
                return builder.buildFuture();
            }).executes(ctx -> {
                ColorTeam team = getRequiredColorTeam(StringArgumentType.getString(ctx, "color"));
                if (team == null) {
                    ctx.getSource().getSender()
                        .sendMessage(Component.text("Unknown team color").color(NamedTextColor.RED));
                    return Command.SINGLE_SUCCESS;
                }
                String message = plugin.getTeamRegistry().getBeaconDescription(team);
                ctx.getSource().getSender().sendMessage(Component.text(team.getDisplayName(), team.getTextColor())
                    .append(Component.text(": " + message, NamedTextColor.GRAY)));
                return Command.SINGLE_SUCCESS;
            })));
    }

    void registerCommand(String name) {
        var manager = plugin.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = event.registrar();
            var rootBuilder = Commands.literal(name)
                .requires(source -> source.getSender().hasPermission("hideandseek.command"))
                .then(Commands.literal("start").executes(ctx -> {
                    startGameCommand(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                }).then(Commands.argument("targets", ArgumentTypes.players()).executes(ctx -> {
                    List<Player> targets = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class)
                        .resolve(ctx.getSource());
                    String error = plugin.startGame(targets);
                    if (error != null) {
                        ctx.getSource().getSender().sendMessage(Component.text(error).color(NamedTextColor.RED));
                    }
                    return Command.SINGLE_SUCCESS;
                }))).then(Commands.literal("stop").executes(ctx -> {
                    if (plugin.getGameStatus() == GameStatus.NOT_STARTED) {
                        ctx.getSource().getSender()
                            .sendMessage(Component.text("Game is not running").color(NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }
                    plugin.stopGame();
                    return Command.SINGLE_SUCCESS;
                })).then(Commands.literal("restart").executes(ctx -> {
                    if (plugin.getGameStatus() == GameStatus.NOT_STARTED) {
                        ctx.getSource().getSender()
                            .sendMessage(Component.text("Game is not running").color(NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }
                    plugin.stopGame();
                    startGameCommand(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })).then(buildModeCommand()).then(buildTeamCommand()).then(buildTeamConfigCommand()).then(Commands
                    .literal("setcenter").requires(source -> source.getExecutor() instanceof Player).executes(ctx -> {
                        var player = (Player) ctx.getSource().getExecutor();
                        plugin.getGameConfig().setGameCenter(player.getLocation().toVector());
                        plugin.getGameConfig().setGameWorld(player.getWorld().getName());
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
                        ctx.getSource().getSender().sendMessage(
                            Component.text("Game radius set to " + gameRadius).color(NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(Commands.literal("setdistanceupdateinterval")
                    .then(Commands.argument("interval", IntegerArgumentType.integer(1)).executes(ctx -> {
                        int interval = IntegerArgumentType.getInteger(ctx, "interval");
                        plugin.getGameConfig().setDistanceUpdateIntervalTicks(interval * 20);
                        saveGameSettings();
                        ctx.getSource().getSender().sendMessage(Component
                            .text("Distance update interval set to " + interval + "s").color(NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(Commands.literal("setdistancegranularity")
                    .then(Commands.argument("granularity", IntegerArgumentType.integer(1)).executes(ctx -> {
                        int granularity = IntegerArgumentType.getInteger(ctx, "granularity");
                        plugin.getGameConfig().setDistanceGranularity(granularity);
                        saveGameSettings();
                        ctx.getSource().getSender().sendMessage(
                            Component.text("Distance granularity set to " + granularity).color(NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(Commands.literal("setarenaborder")
                    .then(Commands.argument("enable", BoolArgumentType.bool()).executes(ctx -> {
                        boolean enabled = BoolArgumentType.getBool(ctx, "enable");
                        plugin.getGameConfig().setArenaBorderEnabled(enabled);
                        saveGameSettings();
                        ctx.getSource().getSender().sendMessage(Component
                            .text("Arena border " + (enabled ? "enabled" : "disabled")).color(NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(Commands.literal("setarenaborderinitialsize")
                    .then(Commands.argument("size", DoubleArgumentType.doubleArg(1.0)).executes(ctx -> {
                        double size = DoubleArgumentType.getDouble(ctx, "size");
                        plugin.getGameConfig().setArenaBorderInitialSize(size);
                        saveGameSettings();
                        ctx.getSource().getSender().sendMessage(
                            Component.text("Arena border initial size set to " + size).color(NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(Commands.literal("setarenaborderfinalsize")
                    .then(Commands.argument("size", DoubleArgumentType.doubleArg(1.0)).executes(ctx -> {
                        double size = DoubleArgumentType.getDouble(ctx, "size");
                        plugin.getGameConfig().setArenaBorderFinalSize(size);
                        saveGameSettings();
                        ctx.getSource().getSender().sendMessage(
                            Component.text("Arena border final size set to " + size).color(NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(Commands.literal("setarenabordertime")
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(0)).executes(ctx -> {
                        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                        plugin.getGameConfig().setArenaBorderTimeToFinalSeconds(seconds);
                        saveGameSettings();
                        ctx.getSource().getSender().sendMessage(Component
                            .text("Arena border shrink time set to " + seconds + "s").color(NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(Commands.literal("setarenacenterradius")
                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.0)).executes(ctx -> {
                        double radius = DoubleArgumentType.getDouble(ctx, "radius");
                        plugin.getGameConfig().setArenaBorderCenterRadius(radius);
                        saveGameSettings();
                        ctx.getSource().getSender().sendMessage(
                            Component.text("Arena border center radius set to " + radius).color(NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(Commands.literal("set")
                    .then(Commands.argument("weapon", StringArgumentType.string()).suggests((ctx, builder) -> {
                        builder.suggest("infinite_crossbow");
                        builder.suggest("oreshnik");
                        builder.suggest("himars");
                        builder.suggest("locator");
                        builder.suggest("fpv_drone");
                        builder.suggest("radar");
                        builder.suggest("grapple_bow");
                        return builder.buildFuture();
                    }).then(Commands.argument("property", StringArgumentType.string()).suggests((ctx, builder) -> {
                        String weapon = StringArgumentType.getString(ctx, "weapon");
                        WeaponConfig wc = plugin.getGameConfig().getWeaponConfig(weapon);
                        if (wc != null) {
                            for (String p : wc.getPropertyNames())
                                builder.suggest(p);
                        }
                        return builder.buildFuture();
                    }).then(Commands.argument("value", StringArgumentType.greedyString()).executes(ctx -> {
                        String weapon = StringArgumentType.getString(ctx, "weapon");
                        String property = StringArgumentType.getString(ctx, "property");
                        String value = StringArgumentType.getString(ctx, "value");
                        WeaponConfig wc = plugin.getGameConfig().getWeaponConfig(weapon);
                        if (wc == null) {
                            ctx.getSource().getSender()
                                .sendMessage(Component.text("Unknown weapon").color(NamedTextColor.RED));
                            return Command.SINGLE_SUCCESS;
                        }
                        WeaponConfig.WeaponSetResult result = wc.setProperty(property, value);
                        if (result == WeaponConfig.WeaponSetResult.SUCCESS) {
                            plugin.getGameConfig().save();
                            plugin.saveConfig();
                            Object newVal = wc.getPropertyValue(property);
                            ctx.getSource().getSender().sendMessage(Component
                                .text("Set " + weapon + "." + property + " = " + newVal).color(NamedTextColor.GREEN));
                        } else if (result == WeaponConfig.WeaponSetResult.UNKNOWN_PROPERTY) {
                            ctx.getSource().getSender()
                                .sendMessage(Component.text("Unknown property").color(NamedTextColor.RED));
                        } else if (result == WeaponConfig.WeaponSetResult.PARSE_ERROR) {
                            ctx.getSource().getSender()
                                .sendMessage(Component.text("Invalid value").color(NamedTextColor.RED));
                        }
                        return Command.SINGLE_SUCCESS;
                    })))))
                .then(Commands.literal("setlifts")
                    .then(Commands.argument("enable", BoolArgumentType.bool()).executes(ctx -> {
                        boolean enableLifts = BoolArgumentType.getBool(ctx, "enable");
                        plugin.getGameConfig().setEnableLifts(enableLifts);
                        saveGameSettings();
                        ctx.getSource().getSender().sendMessage(Component
                            .text("Lifts " + (enableLifts ? "enabled" : "disabled")).color(NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(Commands.literal("join").requires(source -> source.getExecutor() instanceof Player)
                    .executes(ctx -> {
                        var player = (Player) ctx.getSource().getExecutor();
                        joinGameCommand(player, player);
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(
                    Commands.literal("join").then(Commands.argument("player", ArgumentTypes.player()).executes(ctx -> {
                        var player = (Player) ctx.getSource().getExecutor();
                        Player target = ctx.getArgument("player", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource()).getFirst();
                        joinGameCommand(player, target);
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(Commands.literal("give").then(Commands.argument("players", ArgumentTypes.players())
                    .then(Commands.argument("item", StringArgumentType.string()).suggests((ctx, builder) -> {
                        builder.suggest("infinite_crossbow");
                        builder.suggest("oreshnik");
                        builder.suggest("himars");
                        builder.suggest("locator");
                        builder.suggest("fpv_drone");
                        builder.suggest("radar");
                        builder.suggest("grapple_bow");
                        return builder.buildFuture();
                    }).then(Commands.argument("count", IntegerArgumentType.integer(1)).executes(ctx -> {
                        String item = StringArgumentType.getString(ctx, "item");
                        List<Player> players = ctx.getArgument("players", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource());
                        int count = IntegerArgumentType.getInteger(ctx, "count");
                        ItemStack items = createItemStack(item, plugin.getGameConfig(), count);
                        giveItems(players, items);

                        ctx.getSource().getSender()
                            .sendMessage(Component.text("Given the items to players").color(NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    })).executes(ctx -> {
                        String item = StringArgumentType.getString(ctx, "item");
                        List<Player> players = ctx.getArgument("players", PlayerSelectorArgumentResolver.class)
                            .resolve(ctx.getSource());
                        ItemStack items = createItemStack(item, plugin.getGameConfig(), 1);
                        giveItems(players, items);

                        ctx.getSource().getSender()
                            .sendMessage(Component.text("Given the item to players").color(NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    }))))
                .then(Commands.literal("reload").executes(ctx -> {
                    plugin.reloadConfig();
                    plugin.getGameConfig().load();
                    plugin.getTeamRegistry().load();
                    ctx.getSource().getSender()
                        .sendMessage(Component.text("Config and teams reloaded").color(NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                })).then(Commands.literal("setinventory")
                    .then(Commands.argument("enable", BoolArgumentType.bool()).executes(ctx -> {
                        boolean enableGameInventory = BoolArgumentType.getBool(ctx, "enable");
                        plugin.getGameConfig().setEnableGameInventory(enableGameInventory);
                        saveGameSettings();
                        ctx.getSource().getSender().sendMessage(
                            Component.text("Game inventory " + (enableGameInventory ? "enabled" : "disabled"))
                                .color(NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    })))
                .then(Commands.literal("inventory")
                    .then(Commands.literal("copy").then(Commands.argument("source", ArgumentTypes.player())
                        .then(Commands.argument("targets", ArgumentTypes.players()).executes(ctx -> {
                            var source = ctx.getArgument("source", PlayerSelectorArgumentResolver.class)
                                .resolve(ctx.getSource()).getFirst();
                            var targets = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class)
                                .resolve(ctx.getSource());
                            for (var target : targets) {
                                target.getInventory().setContents(source.getInventory().getContents());
                            }
                            ctx.getSource().getSender()
                                .sendMessage(Component.text("Copied inventory").color(NamedTextColor.GREEN));
                            return Command.SINGLE_SUCCESS;
                        }))))
                    .then(Commands.literal("save").requires(source -> source.getExecutor() instanceof Player)
                        .executes(ctx -> {
                            Player player = (Player) ctx.getSource().getExecutor();
                            plugin.saveGameInventory(player);
                            plugin.saveConfig();
                            player.sendMessage(Component.text("Game inventory saved").color(NamedTextColor.GREEN));
                            return Command.SINGLE_SUCCESS;
                        }))
                    .then(Commands.literal("load").requires(source -> source.getExecutor() instanceof Player)
                        .executes(ctx -> {
                            Player player = (Player) ctx.getSource().getExecutor();
                            plugin.loadGameInventory(player);
                            player.sendMessage(Component.text("Game inventory loaded").color(NamedTextColor.GREEN));
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(Commands.literal("asp").then(Commands.literal("enable").executes(ctx -> {
                    final var cfg = plugin.getGameConfig().getAspConfig();
                    ctx.getSource().getSender()
                        .sendMessage(Component.text("ASP support is currently " + (cfg.enable ? "enabled" : "disabled"))
                            .color(NamedTextColor.AQUA));
                    return Command.SINGLE_SUCCESS;
                }).then(Commands.argument("enable", BoolArgumentType.bool()).executes(ctx -> {
                    final var cfg = plugin.getGameConfig().getAspConfig();
                    final var value = BoolArgumentType.getBool(ctx, "enable");
                    if (cfg.enable == value) {
                        ctx.getSource().getSender().sendMessage(
                            Component.text("ASP support is already " + (cfg.enable ? "enabled" : "disabled"))
                                .color(NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }
                    if (value && (cfg.gameWorldName == null || cfg.templateWorldName == null)) {
                        ctx.getSource().getSender()
                            .sendMessage(Component.text("Set template and game world names before enabling ASP support")
                                .color(NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }
                    cfg.enable = value;
                    saveGameSettings();
                    ctx.getSource().getSender().sendMessage(Component
                        .text("ASP support " + (cfg.enable ? "enabled" : "disabled")).color(NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                }))).then(Commands.literal("template_world").executes(ctx -> {
                    final var cfg = plugin.getGameConfig().getAspConfig();
                    ctx.getSource().getSender()
                        .sendMessage(Component.text("Template world name is currently set to " + cfg.templateWorldName)
                            .color(NamedTextColor.AQUA));
                    return Command.SINGLE_SUCCESS;
                }).then(Commands.argument("world_name", StringArgumentType.string()).executes(ctx -> {
                    final var cfg = plugin.getGameConfig().getAspConfig();
                    final var worldName = StringArgumentType.getString(ctx, "world_name");
                    cfg.templateWorldName = worldName;
                    saveGameSettings();
                    ctx.getSource().getSender().sendMessage(
                        Component.text("Template world name set to " + worldName).color(NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                }))).then(Commands.literal("game_world").executes(ctx -> {
                    final var cfg = plugin.getGameConfig().getAspConfig();
                    ctx.getSource().getSender().sendMessage(Component
                        .text("Game world name is currently set to " + cfg.gameWorldName).color(NamedTextColor.AQUA));
                    return Command.SINGLE_SUCCESS;
                }).then(Commands.argument("world_name", StringArgumentType.string()).executes(ctx -> {
                    final var cfg = plugin.getGameConfig().getAspConfig();
                    final var worldName = StringArgumentType.getString(ctx, "world_name");
                    cfg.gameWorldName = worldName;
                    saveGameSettings();
                    ctx.getSource().getSender()
                        .sendMessage(Component.text("Game world name set to " + worldName).color(NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                }))).then(Commands.literal("reset_world_on_start").executes(ctx -> {
                    final var cfg = plugin.getGameConfig().getAspConfig();
                    ctx.getSource().getSender()
                        .sendMessage(Component.text("resetWorldOnStart is currently set to " + cfg.resetWorldOnStart)
                            .color(NamedTextColor.AQUA));
                    return Command.SINGLE_SUCCESS;
                }).then(Commands.argument("enable", BoolArgumentType.bool()).executes(ctx -> {
                    final var cfg = plugin.getGameConfig().getAspConfig();
                    final var value = BoolArgumentType.getBool(ctx, "enable");
                    cfg.resetWorldOnStart = value;
                    saveGameSettings();
                    ctx.getSource().getSender()
                        .sendMessage(Component.text("Set resetWorldOnStart to " + value).color(NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                }))).then(Commands.literal("reset_world").executes(ctx -> {
                    plugin.getAsp().setupWorld(true);
                    ctx.getSource().getSender()
                        .sendMessage(Component.text("Game world reset").color(NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                }))).build();
            commands.register(rootBuilder);
        });
    }
}
