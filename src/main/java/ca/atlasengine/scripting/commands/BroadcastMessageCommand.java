package ca.atlasengine.scripting.commands;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;

public class BroadcastMessageCommand {

    /**
     * Broadcasts a message to all online players.
     * This method is intended to be called from the JavaScript bridge.
     *
     * @param message The message to broadcast.
     */
    public void execute(String message) {
        if (message == null) {
            System.err.println("BroadcastMessageCommand.execute: Message is null.");
            return;
        }
        try {
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player -> {
                player.sendMessage(Component.text(message));
            });
        } catch (Exception e) {
            System.err.println("BroadcastMessageCommand.execute: An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

