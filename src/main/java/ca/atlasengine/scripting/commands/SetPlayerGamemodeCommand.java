package ca.atlasengine.scripting.commands;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class SetPlayerGamemodeCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetPlayerGamemodeCommand.class);

    public boolean execute(String playerIdentifier, String gameModeName) {
        UUID playerUuid = UUID.fromString(playerIdentifier);
        Player targetPlayer = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerUuid);

        if (targetPlayer == null) {
            LOGGER.warn("SetPlayerGamemodeCommand: Player not found: {}", playerIdentifier);
            return false;
        }

        GameMode gameMode;
        try {
            gameMode = GameMode.valueOf(gameModeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("SetPlayerGamemodeCommand: Invalid gamemode: {}. Valid modes are: SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR.", gameModeName);
            return false;
        }

        targetPlayer.setGameMode(gameMode);
        LOGGER.info("SetPlayerGamemodeCommand: Successfully set gamemode of player {} to {}", targetPlayer.getUsername(), gameMode.name());
        targetPlayer.sendMessage("Your gamemode has been set to " + gameMode.name().toLowerCase() + ".");
        return true;
    }
}

