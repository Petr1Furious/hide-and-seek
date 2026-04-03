package me.petr1furious.hideandseek;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

public class TeamBeaconConfig {
    private boolean hidePerPlayerDistances = true;
    private boolean keepInvisibility = true;
    private int protectionRadius = 16;
    private int beaconMaxHealth = 20;
    private int respawnDelaySeconds = 8;
    private int respawnBlindnessSeconds = 8;
    private boolean disableSpawnProtectionOnAttack = true;
    private int spawnProtectionSeconds = 2;
    private boolean friendlyFire = false;
    private boolean beaconBlockBreaksOnlyByEnemies = true;

    public void load(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        hidePerPlayerDistances = section.getBoolean("hidePerPlayerDistances", hidePerPlayerDistances);
        keepInvisibility = section.getBoolean("keepInvisibility", keepInvisibility);
        protectionRadius = Math.max(0, section.getInt("protectionRadius", protectionRadius));
        beaconMaxHealth = Math.max(1, section.getInt("beaconMaxHealth", beaconMaxHealth));
        respawnDelaySeconds = Math.max(0, section.getInt("respawnDelaySeconds", respawnDelaySeconds));
        respawnBlindnessSeconds = Math.max(0, section.getInt("respawnBlindnessSeconds", respawnBlindnessSeconds));
        disableSpawnProtectionOnAttack = section.getBoolean("disableSpawnProtectionOnAttack",
            disableSpawnProtectionOnAttack);
        spawnProtectionSeconds = Math.max(0, section.getInt("spawnProtectionSeconds", spawnProtectionSeconds));
        friendlyFire = section.getBoolean("friendlyFire", friendlyFire);
        beaconBlockBreaksOnlyByEnemies = section.getBoolean("beaconBlockBreaksOnlyByEnemies",
            beaconBlockBreaksOnlyByEnemies);
    }

    public void save(ConfigurationSection section) {
        section.set("hidePerPlayerDistances", hidePerPlayerDistances);
        section.set("keepInvisibility", keepInvisibility);
        section.set("protectionRadius", protectionRadius);
        section.set("beaconMaxHealth", beaconMaxHealth);
        section.set("respawnDelaySeconds", respawnDelaySeconds);
        section.set("respawnBlindnessSeconds", respawnBlindnessSeconds);
        section.set("disableSpawnProtectionOnAttack", disableSpawnProtectionOnAttack);
        section.set("spawnProtectionSeconds", spawnProtectionSeconds);
        section.set("friendlyFire", friendlyFire);
        section.set("beaconBlockBreaksOnlyByEnemies", beaconBlockBreaksOnlyByEnemies);
    }

    public boolean isHidePerPlayerDistances() {
        return hidePerPlayerDistances;
    }

    public boolean isKeepInvisibility() {
        return keepInvisibility;
    }

    public int getProtectionRadius() {
        return protectionRadius;
    }

    public int getBeaconMaxHealth() {
        return beaconMaxHealth;
    }

    public int getRespawnDelaySeconds() {
        return respawnDelaySeconds;
    }

    public int getRespawnBlindnessSeconds() {
        return respawnBlindnessSeconds;
    }

    public boolean isDisableSpawnProtectionOnAttack() {
        return disableSpawnProtectionOnAttack;
    }

    public int getSpawnProtectionSeconds() {
        return spawnProtectionSeconds;
    }

    public boolean isFriendlyFire() {
        return friendlyFire;
    }

    public boolean isBeaconBlockBreaksOnlyByEnemies() {
        return beaconBlockBreaksOnlyByEnemies;
    }

    public List<String> getPropertyNames() {
        return List.of("hidePerPlayerDistances", "keepInvisibility", "protectionRadius", "beaconMaxHealth",
            "respawnDelaySeconds", "respawnBlindnessSeconds", "disableSpawnProtectionOnAttack",
            "spawnProtectionSeconds", "friendlyFire", "beaconBlockBreaksOnlyByEnemies");
    }

    public WeaponSetResult setProperty(String property, String value) {
        if (property == null || value == null) {
            return WeaponSetResult.UNKNOWN_PROPERTY;
        }

        try {
            switch (property) {
            case "hidePerPlayerDistances":
                hidePerPlayerDistances = Boolean.parseBoolean(value);
                return WeaponSetResult.SUCCESS;
            case "keepInvisibility":
                keepInvisibility = Boolean.parseBoolean(value);
                return WeaponSetResult.SUCCESS;
            case "protectionRadius":
                protectionRadius = Math.max(0, Integer.parseInt(value));
                return WeaponSetResult.SUCCESS;
            case "beaconMaxHealth":
                beaconMaxHealth = Math.max(1, Integer.parseInt(value));
                return WeaponSetResult.SUCCESS;
            case "respawnDelaySeconds":
                respawnDelaySeconds = Math.max(0, Integer.parseInt(value));
                return WeaponSetResult.SUCCESS;
            case "respawnBlindnessSeconds":
                respawnBlindnessSeconds = Math.max(0, Integer.parseInt(value));
                return WeaponSetResult.SUCCESS;
            case "disableSpawnProtectionOnAttack":
                disableSpawnProtectionOnAttack = Boolean.parseBoolean(value);
                return WeaponSetResult.SUCCESS;
            case "spawnProtectionSeconds":
                spawnProtectionSeconds = Math.max(0, Integer.parseInt(value));
                return WeaponSetResult.SUCCESS;
            case "friendlyFire":
                friendlyFire = Boolean.parseBoolean(value);
                return WeaponSetResult.SUCCESS;
            case "beaconBlockBreaksOnlyByEnemies":
                beaconBlockBreaksOnlyByEnemies = Boolean.parseBoolean(value);
                return WeaponSetResult.SUCCESS;
            default:
                return WeaponSetResult.UNKNOWN_PROPERTY;
            }
        } catch (NumberFormatException exception) {
            return WeaponSetResult.PARSE_ERROR;
        }
    }

    public Object getPropertyValue(String property) {
        return switch (property) {
        case "hidePerPlayerDistances" -> hidePerPlayerDistances;
        case "keepInvisibility" -> keepInvisibility;
        case "protectionRadius" -> protectionRadius;
        case "beaconMaxHealth" -> beaconMaxHealth;
        case "respawnDelaySeconds" -> respawnDelaySeconds;
        case "respawnBlindnessSeconds" -> respawnBlindnessSeconds;
        case "disableSpawnProtectionOnAttack" -> disableSpawnProtectionOnAttack;
        case "spawnProtectionSeconds" -> spawnProtectionSeconds;
        case "friendlyFire" -> friendlyFire;
        case "beaconBlockBreaksOnlyByEnemies" -> beaconBlockBreaksOnlyByEnemies;
        default -> null;
        };
    }

    public enum WeaponSetResult {
        SUCCESS, UNKNOWN_PROPERTY, PARSE_ERROR
    }
}
