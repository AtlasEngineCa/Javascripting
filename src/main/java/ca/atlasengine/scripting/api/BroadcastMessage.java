package ca.atlasengine.scripting.api;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BroadcastMessage {

    private static final Logger LOGGER = LoggerFactory.getLogger(BroadcastMessage.class);

    /**
     * Broadcasts a message to all online players.
     * This method is intended to be called from the JavaScript bridge.
     *
     * @param message The message to broadcast.
     */
    public void execute(String message) {
        if (message == null) {
            LOGGER.error("BroadcastMessageCommand.execute: Message is null.");
            return;
        }
        try {
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player -> {
                player.sendMessage(Component.text(message));
            });
        } catch (Exception e) {
            LOGGER.error("BroadcastMessageCommand.execute: An unexpected error occurred: {}", e.getMessage());
            e.printStackTrace();
        }
    }
}

