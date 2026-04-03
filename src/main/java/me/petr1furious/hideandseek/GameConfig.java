package me.petr1furious.hideandseek;

import me.petr1furious.hideandseek.weapons.*;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class GameConfig {
    FileConfiguration config;

    private Vector gameCenter;
    private String gameWorld;
    private int gameRadius;
    private GameModeType gameMode = GameModeType.CLASSIC;
    private boolean arenaBorderEnabled;
    private double arenaBorderInitialSize;
    private double arenaBorderFinalSize;
    private long arenaBorderTimeToFinalSeconds;
    private double arenaBorderCenterRadius;

    private int distanceUpdateIntervalTicks;
    private int distanceGranularity;

    private boolean enableLifts;
    private Material liftMaterial;

    private ItemStack[] gameInventory;
    private boolean enableGameInventory;

    private final InfiniteCrossbowConfig infiniteCrossbow = new InfiniteCrossbowConfig();
    private final OreshnikConfig oreshnik = new OreshnikConfig();
    private final HimarsConfig himars = new HimarsConfig();
    private final LocatorConfig locator = new LocatorConfig();
    private final FPVDroneConfig fpvDrone = new FPVDroneConfig();
    private final RadarConfig radar = new RadarConfig();
    private final GrappleBowConfig grappleBow = new GrappleBowConfig();

    private final Map<String, WeaponConfig> weaponConfigIndex = new HashMap<>();

    private final ASPConfig aspConfig = new ASPConfig();
    private final TeamBeaconConfig teamBeacon = new TeamBeaconConfig();

    public GameConfig(FileConfiguration config) {
        this.config = config;
        load();
        indexWeapons();
    }

    private void indexWeapons() {
        weaponConfigIndex.clear();
        weaponConfigIndex.put("infinite_crossbow", infiniteCrossbow);
        weaponConfigIndex.put("ic", infiniteCrossbow);
        weaponConfigIndex.put("oreshnik", oreshnik);
        weaponConfigIndex.put("o", oreshnik);
        weaponConfigIndex.put("himars", himars);
        weaponConfigIndex.put("h", himars);
        weaponConfigIndex.put("locator", locator);
        weaponConfigIndex.put("l", locator);
        weaponConfigIndex.put("fpv_drone", fpvDrone);
        weaponConfigIndex.put("fpv", fpvDrone);
        weaponConfigIndex.put("radar", radar);
        weaponConfigIndex.put("r", radar);
        weaponConfigIndex.put("grapple_bow", grappleBow);
        weaponConfigIndex.put("grapple", grappleBow);
        weaponConfigIndex.put("gb", grappleBow);
    }

    public void load() {
        try {
            gameCenter = config.isVector("gameCenter") ? config.getVector("gameCenter") : new Vector(0, 0, 0);
        } catch (Exception e) {
            gameCenter = new Vector(0, 0, 0);
        }

        gameWorld = config.getString("gameWorld", "world");
        gameRadius = config.getInt("gameRadius", 200);
        gameMode = GameModeType.fromConfig(config.getString("gameMode", gameMode.getId()));
        arenaBorderEnabled = config.getBoolean("arenaBorderEnabled", false);
        arenaBorderInitialSize = Math.max(1.0, config.getDouble("arenaBorderInitialSize", 400.0));
        arenaBorderFinalSize = Math.max(1.0, config.getDouble("arenaBorderFinalSize", 100.0));
        arenaBorderTimeToFinalSeconds = Math.max(0L, config.getLong("arenaBorderTimeToFinalSeconds", 600L));
        arenaBorderCenterRadius = Math.max(0.0, config.getDouble("arenaBorderCenterRadius", 0.0));

        distanceUpdateIntervalTicks = config.getInt("distanceUpdateIntervalTicks", 80);
        if (distanceUpdateIntervalTicks < 1)
            distanceUpdateIntervalTicks = 80;
        distanceGranularity = config.getInt("distanceGranularity", 50);
        if (distanceGranularity < 1)
            distanceGranularity = 50;

        enableLifts = config.getBoolean("enableLifts", true);
        try {
            liftMaterial = Material.valueOf(config.getString("liftMaterial", "LIGHT_GRAY_CONCRETE").toUpperCase());
        } catch (IllegalArgumentException e) {
            liftMaterial = Material.LIGHT_GRAY_CONCRETE;
        }
        var weaponsRoot = config.getConfigurationSection("weapons");
        if (weaponsRoot == null)
            weaponsRoot = config.createSection("weapons");
        infiniteCrossbow.load(weaponsRoot.getConfigurationSection("infinite_crossbow"));
        oreshnik.load(weaponsRoot.getConfigurationSection("oreshnik"));
        himars.load(weaponsRoot.getConfigurationSection("himars"));
        locator.load(weaponsRoot.getConfigurationSection("locator"));
        fpvDrone.load(weaponsRoot.getConfigurationSection("fpv_drone"));
        radar.load(weaponsRoot.getConfigurationSection("radar"));
        grappleBow.load(weaponsRoot.getConfigurationSection("grapple_bow"));

        enableGameInventory = config.getBoolean("enableGameInventory", false);
        gameInventory = config.getList("gameInventory", new ArrayList<ItemStack>()).toArray(new ItemStack[0]);

        aspConfig.load(config.getConfigurationSection("asp"));
        teamBeacon.load(config.getConfigurationSection("teamBeacon"));
    }

    public void save() {
        config.set("gameCenter", gameCenter);
        config.set("gameWorld", gameWorld);
        config.set("gameRadius", gameRadius);
        config.set("gameMode", gameMode.getId());
        config.set("arenaBorderEnabled", arenaBorderEnabled);
        config.set("arenaBorderInitialSize", arenaBorderInitialSize);
        config.set("arenaBorderFinalSize", arenaBorderFinalSize);
        config.set("arenaBorderTimeToFinalSeconds", arenaBorderTimeToFinalSeconds);
        config.set("arenaBorderCenterRadius", arenaBorderCenterRadius);
        config.set("distanceUpdateIntervalTicks", distanceUpdateIntervalTicks);
        config.set("distanceGranularity", distanceGranularity);
        config.set("enableLifts", enableLifts);
        config.set("liftMaterial", liftMaterial.toString());
        var weaponsRoot = config.getConfigurationSection("weapons");
        if (weaponsRoot == null)
            weaponsRoot = config.createSection("weapons");
        infiniteCrossbow.save(getOrCreate(weaponsRoot, "infinite_crossbow"));
        oreshnik.save(getOrCreate(weaponsRoot, "oreshnik"));
        himars.save(getOrCreate(weaponsRoot, "himars"));
        locator.save(getOrCreate(weaponsRoot, "locator"));
        fpvDrone.save(getOrCreate(weaponsRoot, "fpv_drone"));
        radar.save(getOrCreate(weaponsRoot, "radar"));
        grappleBow.save(getOrCreate(weaponsRoot, "grapple_bow"));
        config.set("enableGameInventory", enableGameInventory);
        config.set("gameInventory", gameInventory);
        aspConfig.save(getOrCreate(config, "asp"));
        teamBeacon.save(getOrCreate(config, "teamBeacon"));
    }

    private org.bukkit.configuration.ConfigurationSection getOrCreate(
        org.bukkit.configuration.ConfigurationSection parent, String path) {
        var sec = parent.getConfigurationSection(path);
        if (sec == null)
            sec = parent.createSection(path);
        return sec;
    }

    public Vector getGameCenter() {
        return gameCenter;
    }

    public String getGameWorld() {
        return gameWorld;
    }

    public int getGameRadius() {
        return gameRadius;
    }

    public GameModeType getGameMode() {
        return gameMode;
    }

    public boolean isArenaBorderEnabled() {
        return arenaBorderEnabled;
    }

    public double getArenaBorderInitialSize() {
        return arenaBorderInitialSize;
    }

    public double getArenaBorderFinalSize() {
        return arenaBorderFinalSize;
    }

    public long getArenaBorderTimeToFinalSeconds() {
        return arenaBorderTimeToFinalSeconds;
    }

    public double getArenaBorderCenterRadius() {
        return arenaBorderCenterRadius;
    }

    public int getDistanceUpdateIntervalTicks() {
        return distanceUpdateIntervalTicks;
    }

    public int getDistanceGranularity() {
        return distanceGranularity;
    }

    public boolean isEnableLifts() {
        return enableLifts;
    }

    public Material getLiftMaterial() {
        return liftMaterial;
    }

    public InfiniteCrossbowConfig getInfiniteCrossbow() {
        return infiniteCrossbow;
    }

    public OreshnikConfig getOreshnik() {
        return oreshnik;
    }

    public HimarsConfig getHimars() {
        return himars;
    }

    public LocatorConfig getLocator() {
        return locator;
    }

    public FPVDroneConfig getFpvDrone() {
        return fpvDrone;
    }

    public RadarConfig getRadar() {
        return radar;
    }

    public GrappleBowConfig getGrappleBow() {
        return grappleBow;
    }

    public ItemStack[] getGameInventory() {
        return gameInventory;
    }

    public boolean isEnableGameInventory() {
        return enableGameInventory;
    }

    public void setGameCenter(Vector gameCenter) {
        this.gameCenter = gameCenter;
        save();
    }

    public void setGameWorld(String gameWorld) {
        this.gameWorld = gameWorld;
        save();
    }

    public void setGameRadius(int gameRadius) {
        this.gameRadius = gameRadius;
        save();
    }

    public void setGameMode(GameModeType gameMode) {
        this.gameMode = gameMode == null ? GameModeType.CLASSIC : gameMode;
        save();
    }

    public void setArenaBorderEnabled(boolean arenaBorderEnabled) {
        this.arenaBorderEnabled = arenaBorderEnabled;
        save();
    }

    public void setArenaBorderInitialSize(double arenaBorderInitialSize) {
        this.arenaBorderInitialSize = Math.max(1.0, arenaBorderInitialSize);
        save();
    }

    public void setArenaBorderFinalSize(double arenaBorderFinalSize) {
        this.arenaBorderFinalSize = Math.max(1.0, arenaBorderFinalSize);
        save();
    }

    public void setArenaBorderTimeToFinalSeconds(long arenaBorderTimeToFinalSeconds) {
        this.arenaBorderTimeToFinalSeconds = Math.max(0L, arenaBorderTimeToFinalSeconds);
        save();
    }

    public void setArenaBorderCenterRadius(double arenaBorderCenterRadius) {
        this.arenaBorderCenterRadius = Math.max(0.0, arenaBorderCenterRadius);
        save();
    }

    public void setDistanceUpdateIntervalTicks(int distanceUpdateIntervalTicks) {
        this.distanceUpdateIntervalTicks = Math.max(1, distanceUpdateIntervalTicks);
        save();
    }

    public void setDistanceGranularity(int distanceGranularity) {
        this.distanceGranularity = Math.max(1, distanceGranularity);
        save();
    }

    public void setEnableLifts(boolean enableLifts) {
        this.enableLifts = enableLifts;
        save();
    }

    public void setGameInventory(ItemStack[] gameInventory) {
        this.gameInventory = gameInventory;
        save();
    }

    public void setEnableGameInventory(boolean enableGameInventory) {
        this.enableGameInventory = enableGameInventory;
        save();
    }

    public WeaponConfig getWeaponConfig(String weaponName) {
        if (weaponName == null)
            return null;
        return weaponConfigIndex.get(weaponName.toLowerCase(Locale.ROOT));
    }

    public ASPConfig getAspConfig() {
        return aspConfig;
    }

    public TeamBeaconConfig getTeamBeacon() {
        return teamBeacon;
    }
}
