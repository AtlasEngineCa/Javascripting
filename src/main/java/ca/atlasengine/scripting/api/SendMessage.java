package ca.atlasengine.scripting.api;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class SendMessage {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendMessage.class);

    /**
     * Sends a message to a specific player.
     * This method is intended to be called from the JavaScript bridge.
     *
     * @param playerUuidString The UUID of the player as a string.
     * @param message          The message to send.
     */
    public void execute(String playerUuidString, String message) {
        if (playerUuidString == null || message == null) {
            LOGGER.error("SendMessageCommand.execute: UUID or message is null.");
            return;
        }
        try {
            UUID playerUuid = UUID.fromString(playerUuidString);
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerUuid);
            if (player != null) {
                player.sendMessage(Component.text(message));
            } else {
                LOGGER.error("SendMessageCommand.execute: Player not found with UUID: {}", playerUuidString);
            }
        } catch (IllegalArgumentException e) {
            LOGGER.error("SendMessageCommand.execute: Invalid UUID format: {}. Error: {}", playerUuidString, e.getMessage());
        } catch (Exception e) {
            LOGGER.error("SendMessageCommand.execute: An unexpected error occurred: {}", e.getMessage());
            e.printStackTrace();
        }
    }
}
