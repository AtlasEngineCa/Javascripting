package ca.atlasengine.scripting.api;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;

import java.util.UUID;

public class SetPlayerGamemode {

    public boolean execute(String playerIdentifier, String gameModeName) {
        UUID playerUuid = UUID.fromString(playerIdentifier);
        Player targetPlayer = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerUuid);

        if (targetPlayer == null) return false;

        GameMode gameMode;
        try {
            gameMode = GameMode.valueOf(gameModeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return false;
        }

        targetPlayer.setGameMode(gameMode);
        targetPlayer.sendMessage("Your gamemode has been set to " + gameMode.name().toLowerCase() + ".");
        return true;
    }
}

