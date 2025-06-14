package ca.atlasengine.scripting;

import ca.atlasengine.scripting.commands.BroadcastMessageCommand;
import ca.atlasengine.scripting.commands.ScheduleCommand;
import ca.atlasengine.scripting.commands.SendMessageCommand;
import ca.atlasengine.scripting.commands.SetPlayerGamemodeCommand;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public class MinestomBridge {
    private final ScriptingManager scriptingManager;
    private final SendMessageCommand sendMessageCommand;
    private final BroadcastMessageCommand broadcastMessageCommand;
    private final SetPlayerGamemodeCommand setPlayerGamemodeCommand;
    private final ScheduleCommand scheduleCommand;

    public MinestomBridge(ScriptingManager scriptingManager) {
        this.scriptingManager = scriptingManager;
        this.sendMessageCommand = new SendMessageCommand();
        this.broadcastMessageCommand = new BroadcastMessageCommand();
        this.setPlayerGamemodeCommand = new SetPlayerGamemodeCommand();
        this.scheduleCommand = new ScheduleCommand(scriptingManager);
    }

    @HostAccess.Export
    public void on(String eventName, Value jsCallback) {
        if (jsCallback == null || !jsCallback.canExecute()) {
            System.err.println("MinestomBridge.on: Invalid or non-executable callback provided for event: " + eventName);
            return;
        }
        scriptingManager.registerJsEventListener(eventName, jsCallback);
    }

    @HostAccess.Export
    public void sendMessage(String playerUuidString, String message) {
        this.sendMessageCommand.execute(playerUuidString, message);
    }

    @HostAccess.Export
    public void broadcastMessage(String message) {
        this.broadcastMessageCommand.execute(message);
    }

    boolean setPlayerGamemode(String playerIdentifier, String gameModeName) {
        return this.setPlayerGamemodeCommand.execute(playerIdentifier, gameModeName);
    }

    @HostAccess.Export
    public Value schedule(long delayInTicks) {
        return this.scheduleCommand.schedule(delayInTicks);
    }

    // Method to be called by ScriptingManager
    void _onScriptReload(String scriptFileName) {
        // Implementation for handling script reloads, if necessary
    }

    void _onScriptStop(String scriptFileName) {
        // Implementation for handling script stops, if necessary
    }
}
