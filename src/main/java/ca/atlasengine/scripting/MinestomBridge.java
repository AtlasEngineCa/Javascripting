package ca.atlasengine.scripting;

import ca.atlasengine.scripting.api.CommandApi;
import ca.atlasengine.scripting.api.BroadcastMessage;
import ca.atlasengine.scripting.api.Schedule;
import ca.atlasengine.scripting.api.SendMessage;
import ca.atlasengine.scripting.api.SetPlayerGamemode;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public class MinestomBridge {
    private final ScriptingManager scriptingManager;
    private final SendMessage sendMessage;
    private final BroadcastMessage broadcastMessage;
    private final SetPlayerGamemode setPlayerGamemode;
    private final Schedule schedule;
    private final CommandApi commandApi;

    public MinestomBridge(ScriptingManager scriptingManager) {
        this.scriptingManager = scriptingManager;
        this.sendMessage = new SendMessage();
        this.broadcastMessage = new BroadcastMessage();
        this.setPlayerGamemode = new SetPlayerGamemode();
        this.schedule = new Schedule(scriptingManager);
        this.commandApi = new CommandApi(scriptingManager);
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
        this.sendMessage.execute(playerUuidString, message);
    }

    @HostAccess.Export
    public void broadcastMessage(String message) {
        this.broadcastMessage.execute(message);
    }

    public boolean setPlayerGamemode(String playerIdentifier, String gameModeName) {
        return this.setPlayerGamemode.execute(playerIdentifier, gameModeName);
    }

    @HostAccess.Export
    public Value schedule(long delayInTicks) {
        return this.schedule.schedule(delayInTicks);
    }

    @HostAccess.Export
    public void registerCommand(Value commandDefinitionValue) {
        commandApi.register(commandDefinitionValue);
    }
}
