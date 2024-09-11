package me.petr1furious.hideandseek;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;

public class GameConfig {
    FileConfiguration config;

    private Vector gameCenter;
    private int gameRadius;
    private boolean enableExplosions;
    private double explosionPower;
    private boolean enableLifts;
    private Material liftMaterial;
    private int maxLoadedCrossbowProjectiles;

    public GameConfig(FileConfiguration config) {
        this.config = config;
        load();
    }

    public void load() {
        gameCenter = config.getVector("gameCenter", new Vector(0, 0, 0));
        gameRadius = config.getInt("gameRadius", 100);
        enableExplosions = config.getBoolean("enableExplosions", true);
        explosionPower = config.getDouble("explosionPower", 2.0f);
        enableLifts = config.getBoolean("enableLifts", true);
        liftMaterial = Material.valueOf(config.getString("liftMaterial", "LIGHT_GRAY_CONCRETE"));
        maxLoadedCrossbowProjectiles = config.getInt("maxLoadedCrossbowProjectiles", 1);
    }

    public void save() {
        config.set("gameCenter", gameCenter);
        config.set("gameRadius", gameRadius);
        config.set("enableExplosions", enableExplosions);
        config.set("explosionPower", explosionPower);
        config.set("enableLifts", enableLifts);
        config.set("liftMaterial", liftMaterial.toString());
        config.set("maxLoadedCrossbowProjectiles", maxLoadedCrossbowProjectiles);
    }

    public Vector getGameCenter() {
        return gameCenter;
    }

    public int getGameRadius() {
        return gameRadius;
    }

    public boolean isEnableExplosions() {
        return enableExplosions;
    }

    public double getExplosionPower() {
        return explosionPower;
    }

    public boolean isEnableLifts() {
        return enableLifts;
    }

    public Material getLiftMaterial() {
        return liftMaterial;
    }

    public int getMaxLoadedCrossbowProjectiles() {
        return maxLoadedCrossbowProjectiles;
    }

    public void setGameCenter(Vector gameCenter) {
        this.gameCenter = gameCenter;
        save();
    }

    public void setGameRadius(int gameRadius) {
        this.gameRadius = gameRadius;
        save();
    }

    public void setEnableExplosions(boolean enableExplosions) {
        this.enableExplosions = enableExplosions;
        save();
    }

    public void setExplosionPower(double explosionPower) {
        this.explosionPower = explosionPower;
        save();
    }

    public void setEnableLifts(boolean enableLifts) {
        this.enableLifts = enableLifts;
        save();
    }

    public void setMaxLoadedCrossbowProjectiles(int maxLoadedCrossbowProjectiles) {
        this.maxLoadedCrossbowProjectiles = maxLoadedCrossbowProjectiles;
        save();
    }
}
