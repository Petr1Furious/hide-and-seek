package me.petr1furious.hideandseek;

import java.util.Locale;

public enum GameModeType {
    CLASSIC("classic"), TEAM_BEACON("team_beacon");

    private final String id;

    GameModeType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static GameModeType fromConfig(String value) {
        if (value == null) {
            return CLASSIC;
        }

        String normalized = value.toLowerCase(Locale.ROOT);
        for (GameModeType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return CLASSIC;
    }
}
