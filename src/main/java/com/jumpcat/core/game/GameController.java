package com.jumpcat.core.game;

import org.bukkit.command.CommandSender;

public interface GameController {
    String getId();
    String getDisplayName();
    void prepare(CommandSender initiator);
    void start(CommandSender initiator);
    void stop(CommandSender initiator);
    String status();
}
