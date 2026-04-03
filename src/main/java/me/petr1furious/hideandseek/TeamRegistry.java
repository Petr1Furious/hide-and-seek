package me.petr1furious.hideandseek;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class TeamRegistry {
    private static class TeamEntry {
        private final Set<UUID> members = new HashSet<>();
        private String beaconWorldName;
        private Integer beaconX;
        private Integer beaconY;
        private Integer beaconZ;

        private boolean hasBeacon() {
            return beaconWorldName != null && beaconX != null && beaconY != null && beaconZ != null;
        }
    }

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration config;
    private final Map<ColorTeam, TeamEntry> entries = new EnumMap<>(ColorTeam.class);

    public TeamRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teams.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("teams.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        entries.clear();
        for (ColorTeam team : ColorTeam.values()) {
            entries.put(team, new TeamEntry());
        }

        ConfigurationSection teamsSection = config.getConfigurationSection("teams");
        if (teamsSection == null) {
            return;
        }

        for (ColorTeam team : ColorTeam.values()) {
            ConfigurationSection teamSection = teamsSection.getConfigurationSection(team.getId());
            if (teamSection == null) {
                continue;
            }

            TeamEntry entry = entries.get(team);
            for (String rawUuid : teamSection.getStringList("members")) {
                try {
                    entry.members.add(UUID.fromString(rawUuid));
                } catch (IllegalArgumentException ignored) {
                }
            }

            ConfigurationSection beacon = teamSection.getConfigurationSection("beacon");
            if (beacon != null) {
                entry.beaconWorldName = beacon.getString("world");
                entry.beaconX = beacon.getInt("x");
                entry.beaconY = beacon.getInt("y");
                entry.beaconZ = beacon.getInt("z");
            }
        }
    }

    public void save() {
        config.set("teams", null);
        ConfigurationSection teamsSection = config.createSection("teams");
        for (ColorTeam team : ColorTeam.values()) {
            TeamEntry entry = entries.get(team);
            if (entry == null) {
                continue;
            }

            ConfigurationSection teamSection = teamsSection.createSection(team.getId());
            List<String> members = entry.members.stream().map(UUID::toString).sorted().toList();
            teamSection.set("members", members);
            if (entry.hasBeacon()) {
                ConfigurationSection beacon = teamSection.createSection("beacon");
                beacon.set("world", entry.beaconWorldName);
                beacon.set("x", entry.beaconX);
                beacon.set("y", entry.beaconY);
                beacon.set("z", entry.beaconZ);
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save teams.yml", e);
        }
    }

    public Optional<ColorTeam> getTeam(UUID playerId) {
        for (Map.Entry<ColorTeam, TeamEntry> entry : entries.entrySet()) {
            if (entry.getValue().members.contains(playerId)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public Optional<ColorTeam> getTeam(org.bukkit.entity.Player player) {
        return getTeam(player.getUniqueId());
    }

    public void assignPlayer(UUID playerId, ColorTeam team) {
        removePlayer(playerId);
        entries.get(team).members.add(playerId);
        save();
    }

    public void removePlayer(UUID playerId) {
        for (TeamEntry entry : entries.values()) {
            entry.members.remove(playerId);
        }
        save();
    }

    public void swapTeams(ColorTeam first, ColorTeam second) {
        if (first == second) {
            return;
        }

        TeamEntry firstEntry = entries.get(first);
        TeamEntry secondEntry = entries.get(second);
        Set<UUID> firstMembers = new HashSet<>(firstEntry.members);
        firstEntry.members.clear();
        firstEntry.members.addAll(secondEntry.members);
        secondEntry.members.clear();
        secondEntry.members.addAll(firstMembers);
        save();
    }

    public Set<UUID> getMembers(ColorTeam team) {
        return Collections.unmodifiableSet(entries.get(team).members);
    }

    public List<ColorTeam> getAssignedTeams(Collection<? extends org.bukkit.entity.Player> players) {
        Set<ColorTeam> assigned = new HashSet<>();
        for (org.bukkit.entity.Player player : players) {
            getTeam(player).ifPresent(assigned::add);
        }
        List<ColorTeam> ordered = new ArrayList<>(assigned);
        ordered.sort(Enum::compareTo);
        return ordered;
    }

    public List<ColorTeam> getConfiguredTeams() {
        List<ColorTeam> result = new ArrayList<>();
        for (ColorTeam team : ColorTeam.values()) {
            TeamEntry entry = entries.get(team);
            if (entry.hasBeacon() || !entry.members.isEmpty()) {
                result.add(team);
            }
        }
        return result;
    }

    public Location getBeacon(ColorTeam team) {
        TeamEntry entry = entries.get(team);
        if (!entry.hasBeacon()) {
            return null;
        }

        var world = Bukkit.getWorld(entry.beaconWorldName);
        if (world == null) {
            return null;
        }

        return new Location(world, entry.beaconX, entry.beaconY, entry.beaconZ);
    }

    public String getBeaconDescription(ColorTeam team) {
        TeamEntry entry = entries.get(team);
        if (!entry.hasBeacon()) {
            return "no beacon";
        }
        return entry.beaconWorldName + " " + entry.beaconX + "," + entry.beaconY + "," + entry.beaconZ;
    }

    public void setBeacon(ColorTeam team, Location location) {
        Location blockLocation = location.getBlock().getLocation();
        TeamEntry entry = entries.get(team);
        entry.beaconWorldName = blockLocation.getWorld() == null ? null : blockLocation.getWorld().getName();
        entry.beaconX = blockLocation.getBlockX();
        entry.beaconY = blockLocation.getBlockY();
        entry.beaconZ = blockLocation.getBlockZ();
        save();
    }

    public void clearBeacon(ColorTeam team) {
        TeamEntry entry = entries.get(team);
        entry.beaconWorldName = null;
        entry.beaconX = null;
        entry.beaconY = null;
        entry.beaconZ = null;
        save();
    }
}
