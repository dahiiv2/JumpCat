package com.jumpcat.core.game;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class GameRegistry {
    private final Map<String, GameController> controllers = new LinkedHashMap<>();

    public void register(String id, GameController controller) {
        controllers.put(id.toLowerCase(), controller);
    }

    public GameController get(String id) {
        if (id == null) return null;
        return controllers.get(id.toLowerCase());
    }

    public Collection<GameController> list() {
        return controllers.values();
    }
}
