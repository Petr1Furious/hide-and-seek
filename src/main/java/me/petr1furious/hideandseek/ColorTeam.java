package me.petr1furious.hideandseek;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.Locale;

public enum ColorTeam {
    WHITE(NamedTextColor.WHITE, BossBar.Color.WHITE), LIGHT_GRAY(NamedTextColor.GRAY, BossBar.Color.WHITE),
    GRAY(NamedTextColor.DARK_GRAY, BossBar.Color.WHITE), BLACK(NamedTextColor.BLACK, BossBar.Color.WHITE),
    RED(NamedTextColor.RED, BossBar.Color.RED), ORANGE(NamedTextColor.GOLD, BossBar.Color.YELLOW),
    YELLOW(NamedTextColor.YELLOW, BossBar.Color.YELLOW), LIME(NamedTextColor.GREEN, BossBar.Color.GREEN),
    GREEN(NamedTextColor.DARK_GREEN, BossBar.Color.GREEN), CYAN(NamedTextColor.AQUA, BossBar.Color.BLUE),
    LIGHT_BLUE(NamedTextColor.BLUE, BossBar.Color.BLUE), BLUE(NamedTextColor.DARK_BLUE, BossBar.Color.BLUE),
    PURPLE(NamedTextColor.DARK_PURPLE, BossBar.Color.PURPLE),
    MAGENTA(NamedTextColor.LIGHT_PURPLE, BossBar.Color.PURPLE), PINK(NamedTextColor.LIGHT_PURPLE, BossBar.Color.PINK),
    BROWN(NamedTextColor.GOLD, BossBar.Color.YELLOW);

    private final NamedTextColor textColor;
    private final BossBar.Color bossBarColor;
    private final Material woolMaterial;
    private final String id;
    private final String displayName;

    ColorTeam(NamedTextColor textColor, BossBar.Color bossBarColor) {
        this.textColor = textColor;
        this.bossBarColor = bossBarColor;
        this.id = name().toLowerCase(Locale.ROOT);
        this.displayName = buildDisplayName(name());
        this.woolMaterial = Material.valueOf(name() + "_WOOL");
    }

    public NamedTextColor getTextColor() {
        return textColor;
    }

    public BossBar.Color getBossBarColor() {
        return bossBarColor;
    }

    public Material getWoolMaterial() {
        return woolMaterial;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ColorTeam fromId(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.toUpperCase(Locale.ROOT);
        for (ColorTeam team : values()) {
            if (team.name().equals(normalized) || team.id.equals(value.toLowerCase(Locale.ROOT))) {
                return team;
            }
        }
        return null;
    }

    private static String buildDisplayName(String name) {
        String[] parts = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }
}
