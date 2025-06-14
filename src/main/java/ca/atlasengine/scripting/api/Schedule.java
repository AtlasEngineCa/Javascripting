package ca.atlasengine.scripting.api;

import ca.atlasengine.scripting.ScriptInstance;
import ca.atlasengine.scripting.ScriptingManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.TaskSchedule;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class Schedule {

    private static final Logger LOGGER = LoggerFactory.getLogger(Schedule.class);
    private final ScriptingManager scriptingManager;

    public Schedule(ScriptingManager scriptingManager) {
        this.scriptingManager = scriptingManager;
    }

    private void resolveScheduledPromise(ScriptInstance contextForCallback, String taskScriptName, String uniqueId) {
        Value pendingTasksMap = null;
        Value callbacks = null;
        Value resolveFunc = null;
        Value rejectFunc = null;

        try {
            if (contextForCallback == null || contextForCallback.getGraalvmContext() == null) {
                LOGGER.error("ScheduleCommand.resolveScheduledPromise: ScriptInstance or its context is null for task ID {} (script: {}). Cannot proceed.", uniqueId, taskScriptName);
                return;
            }

            pendingTasksMap = contextForCallback.getGraalvmContext().getBindings("js").getMember("polyglotMinestomPendingTasks");

            if (pendingTasksMap == null || !pendingTasksMap.hasMembers() || !pendingTasksMap.hasMember(uniqueId)) {
                LOGGER.warn("ScheduleCommand.resolveScheduledPromise: pendingTasksMap is missing or task ID {} not found for script: {}. Task might have been cleared or already run.", uniqueId, taskScriptName);
                return;
            }
            callbacks = pendingTasksMap.getMember(uniqueId);
            if (callbacks == null || callbacks.isNull()) {
                 LOGGER.warn("ScheduleCommand.resolveScheduledPromise: Callbacks object for task ID {} is null for script: {}", uniqueId, taskScriptName);
                 return;
            }

            resolveFunc = callbacks.getMember("resolve");
            rejectFunc = callbacks.getMember("reject");

            if (resolveFunc != null && resolveFunc.canExecute()) {
                resolveFunc.execute();
            } else {
                LOGGER.warn("ScheduleCommand.resolveScheduledPromise: resolveFunc was null or not executable for task ID {} (script: {}).", uniqueId, taskScriptName);
                if (rejectFunc != null && rejectFunc.canExecute()) {
                    rejectFunc.execute("Internal error: resolve function not found or not executable for scheduled task " + uniqueId);
                }
            }
        } catch (Exception e) {
            LOGGER.error("ScheduleCommand.resolveScheduledPromise: Error during task execution or promise resolution for task ID {} (script: {}): {}", uniqueId, taskScriptName, e.getMessage(), e);
            if (rejectFunc != null && rejectFunc.canExecute()) {
                try {
                    rejectFunc.execute(e.getMessage());
                } catch (Exception innerEx) {
                    LOGGER.error("ScheduleCommand.resolveScheduledPromise: Error while attempting to reject promise for task ID {}: {}", uniqueId, innerEx.getMessage(), innerEx);
                }
            }
        } finally {
            if (pendingTasksMap != null && pendingTasksMap.hasMember(uniqueId)) {
                pendingTasksMap.removeMember(uniqueId);
            }
        }
    }

    public Value schedule(long delayInTicks) {
        ScriptInstance scriptInstance = scriptingManager.getCurrentScriptInstance();
        String scriptFileName = scriptingManager.getCurrentScriptFileName();

        if (scriptInstance == null) {
            LOGGER.warn("ScheduleCommand.schedule: Cannot schedule task, no active script instance for script: {}. Returning null.", scriptFileName);
            return null; // Cannot create a JS promise if scriptInstance is null
        }

        String uniqueId = "task_" + UUID.randomUUID().toString().replace("-", "");
        String jsEval = String.format(
            "(() => {" +
            "  globalThis.polyglotMinestomPendingTasks = globalThis.polyglotMinestomPendingTasks || {};" +
            "  let res, rej;" +
            "  const p = new Promise((resolve, reject) => { res = resolve; rej = reject; });" +
            "  globalThis.polyglotMinestomPendingTasks['%s'] = { resolve: res, reject: rej, scriptName: '%s' };" +
            "  return p;" +
            "})()", uniqueId, scriptFileName
        );

        Value promise;
        try {
            promise = scriptInstance.eval(jsEval);
        } catch (Exception e) {
            LOGGER.error("ScheduleCommand.schedule: Error evaluating JS for promise creation in script '{}': {}", scriptFileName, e.getMessage(), e);
            // Attempt to return a rejected promise from the script context
            try {
                return scriptInstance.eval("(async () => { throw new Error('Failed to create promise for schedule due to JS eval error.'); })()");
            } catch (Exception evalEx) {
                LOGGER.error("ScheduleCommand.schedule: Further error trying to eval a rejected promise: {}", evalEx.getMessage(), evalEx);
                return null; // Fallback if even that fails
            }
        }

        if (promise == null || promise.isNull()) {
            LOGGER.warn("ScheduleCommand.schedule: Failed to create promise object in script '{}'. Scheduling aborted.", scriptFileName);
             try {
                return scriptInstance.eval("(async () => { throw new Error('Failed to initialize promise object for schedule in script " + scriptFileName.replace("'", "\\'") + "'); })()");
            } catch (Exception evalEx) {
                LOGGER.error("ScheduleCommand.schedule: Further error trying to eval a rejected promise for null promise: {}", evalEx.getMessage(), evalEx);
                return null; // Fallback if even that fails
            }
        }

        if (delayInTicks <= 0) {
            this.resolveScheduledPromise(scriptInstance, scriptFileName, uniqueId);
        } else {
            MinecraftServer.getSchedulerManager().scheduleTask(() -> {
                this.resolveScheduledPromise(scriptInstance, scriptFileName, uniqueId);
            }, TaskSchedule.tick((int) delayInTicks), TaskSchedule.stop());
        }
        return promise;
    }
}

