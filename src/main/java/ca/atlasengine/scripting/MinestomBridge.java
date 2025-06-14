package ca.atlasengine.scripting;

import ca.atlasengine.scripting.commands.BroadcastMessageCommand; // Added import
import ca.atlasengine.scripting.commands.SendMessageCommand;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.TaskSchedule;

public class MinestomBridge {
    private final ScriptingManager scriptingManager;
    private final SendMessageCommand sendMessageCommand;
    private final BroadcastMessageCommand broadcastMessageCommand;

    public MinestomBridge(ScriptingManager scriptingManager) {
        this.scriptingManager = scriptingManager;
        this.sendMessageCommand = new SendMessageCommand();
        this.broadcastMessageCommand = new BroadcastMessageCommand();
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
        // Call the instance method from the SendMessageCommand instance
        this.sendMessageCommand.execute(playerUuidString, message);
    }

    @HostAccess.Export
    public void broadcastMessage(String message) {
        // Call the instance method from the BroadcastMessageCommand instance
        this.broadcastMessageCommand.execute(message);
    }

    @HostAccess.Export
    public Value schedule(long delayInTicks) {
        ScriptInstance scriptInstance = scriptingManager.getCurrentScriptInstance();
        String scriptFileName = scriptingManager.getCurrentScriptFileName(); // Get script context

        if (scriptInstance == null) {
            System.err.println("MinestomBridge.schedule: Cannot schedule task, no active script instance.");
            // Return a JS-friendly promise that is already rejected
            return scriptInstance.eval("(async () => { throw new Error('No active script instance for schedule'); })()");
        }

        Value promise = scriptInstance.eval("(() => {" +
                                          "  let res, rej;" +
                                          "  const p = new Promise((resolve, reject) => { res = resolve; rej = reject; });" +
                                          // Assign to global scope for the current context
                                          "  pendingTaskResolve = res;" +
                                          "  pendingTaskReject = rej;" +
                                          "  return p;" +
                                          "})()");

        // It's crucial to check if the promise itself is null due to an eval error
        if (promise == null || promise.isNull()) {
            System.err.println("MinestomBridge.schedule: Failed to create promise in script '" + scriptFileName + "'. Scheduling aborted.");
            return scriptInstance.eval("(async () => { throw new Error('Failed to initialize promise for schedule in script " + scriptFileName + "'); })()");
        }

        MinecraftServer.getSchedulerManager().scheduleTask(() -> {
            ScriptInstance currentSI = scriptingManager.getCurrentScriptInstance(); // Re-fetch in case it changed
            if (currentSI != null && currentSI == scriptInstance) { // Ensure the same script instance is active
                Value resolveFunc = currentSI.getGraalvmContext().getBindings("js").getMember("pendingTaskResolve");
                Value rejectFunc = currentSI.getGraalvmContext().getBindings("js").getMember("pendingTaskReject");

                try {
                    if (resolveFunc != null && resolveFunc.canExecute()) {
                        resolveFunc.execute();
                    } else {
                        System.err.println("MinestomBridge.schedule: pendingTaskResolve was null or not executable for script '" + scriptFileName + "'.");
                        if (rejectFunc != null && rejectFunc.canExecute()) {
                            rejectFunc.execute("Internal error: resolve function not found for scheduled task.");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error resolving/rejecting schedule promise in script '" + scriptFileName + "': " + e.getMessage());
                    if (rejectFunc != null && rejectFunc.canExecute()) {
                        try {
                            rejectFunc.execute(e.getMessage());
                        } catch (Exception innerEx) {
                            System.err.println("Error while attempting to reject schedule promise in script '" + scriptFileName + "': " + innerEx.getMessage());
                        }
                    }
                } finally {
                    currentSI.getGraalvmContext().getBindings("js").removeMember("pendingTaskResolve");
                    currentSI.getGraalvmContext().getBindings("js").removeMember("pendingTaskReject");
                }
            } else {
                System.err.println("MinestomBridge.schedule: Script instance changed or became null before task execution for script '" + scriptFileName + "'. Task not executed in JS.");
            }

            System.out.printf("Scheduled task executed after %d ticks in script '%s'.%n", delayInTicks, scriptFileName);
        }, TaskSchedule.tick((int) delayInTicks), TaskSchedule.stop()); // Cast to int is correct for TaskSchedule.tick

        return promise;
    }
}
