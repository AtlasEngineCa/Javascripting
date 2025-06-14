package ca.atlasengine.scripting;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;

public class EventHooks {

    private final ScriptingManager scriptingManager;

    public EventHooks(ScriptingManager scriptingManager) {
        this.scriptingManager = scriptingManager;
    }

    public void registerEventHandlers() {
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();

        globalEventHandler.addListener(PlayerDisconnectEvent.class, event -> {
            final Player player = event.getPlayer();
            scriptingManager.firePlayerLeaveEvent(player);
        });

        globalEventHandler.addListener(PlayerSpawnEvent.class, event -> {
            final Player player = event.getPlayer();
            if (event.isFirstSpawn()) {
                scriptingManager.firePlayerJoinEvent(player);
            }
        });

        globalEventHandler.addListener(PlayerBlockInteractEvent.class, event -> {
            scriptingManager.firePlayerBlockInteractEvent(event.getPlayer(), event.getBlockPosition(), event.getBlock(), event.getHand());
        });

        globalEventHandler.addListener(PlayerMoveEvent.class, event -> {
            final Player player = event.getPlayer();
            scriptingManager.firePlayerMoveEvent(player, event.getNewPosition(), event.isOnGround());
        });

        System.out.println("EventHooks: Registered Hooks");
    }
}


