package org.enthusia.events.command;

import org.bukkit.command.CommandSender;

@FunctionalInterface
interface AdminCommandAction {

    boolean execute(CommandSender sender, String[] args);
}
