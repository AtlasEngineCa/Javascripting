package ca.atlasengine.scripting.api;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

import java.util.UUID;

public class SendMessage {

    /**
     * Sends a message to a specific player.
     * This method is intended to be called from the JavaScript bridge.
     *
     * @param playerUuidString The UUID of the player as a string.
     * @param message          The message to send.
     */
    public void execute(String playerUuidString, String message) {
        if (playerUuidString == null || message == null) {
            System.err.println("SendMessageCommand.execute: UUID or message is null.");
            return;
        }
        try {
            UUID playerUuid = UUID.fromString(playerUuidString);
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerUuid);
            if (player != null) {
                player.sendMessage(Component.text(message));
            } else {
                System.err.println("SendMessageCommand.execute: Player not found with UUID: " + playerUuidString);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("SendMessageCommand.execute: Invalid UUID format: " + playerUuidString + ". Error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("SendMessageCommand.execute: An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
