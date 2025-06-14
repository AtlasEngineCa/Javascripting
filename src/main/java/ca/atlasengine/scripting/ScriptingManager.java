package ca.atlasengine.scripting;

import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.coordinate.Pos;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScriptingManager {
    private ScriptInstance currentScriptInstance;
    private String currentScriptFileName; // Added field to store current script's file name
    private final MinestomBridge bridge;
    private final Map<String, List<Value>> jsEventListeners = new HashMap<>();
    private final Path scriptsDir = Paths.get("scripts"); // Define scripts directory path

    public ScriptingManager() {
        this.bridge = new MinestomBridge(this);
    }

    public ScriptInstance getCurrentScriptInstance() {
        return this.currentScriptInstance;
    }

    public String getCurrentScriptFileName() {
        return this.currentScriptFileName;
    }

    public MinestomBridge getBridge() {
        return this.bridge;
    }

    public synchronized void loadAndRunScript(String fileName, Player commandSender) {
        // Path scriptDir = Paths.get("scripts"); // Moved to class field
        if (!Files.exists(scriptsDir)) {
            try {
                Files.createDirectories(scriptsDir);
            } catch (IOException e) {
                String message = "Error: Could not create 'scripts' directory: " + e.getMessage();
                 if (commandSender != null) commandSender.sendMessage(message);
                 else System.err.println(message);
                e.printStackTrace();
                return;
            }
        }

        Path scriptPath = scriptsDir.resolve(fileName.endsWith(".js") ? fileName : fileName + ".js");
        this.currentScriptFileName = scriptPath.getFileName().toString(); // Store the script file name
        String effectiveFileName = this.currentScriptFileName; // Use the stored name

        if (!Files.exists(scriptPath)) {
            String message = "Error: Script file not found: " + scriptPath;
            if (commandSender != null) commandSender.sendMessage(message);
            else System.err.println(message);
            return;
        }

        try {
            if (currentScriptInstance != null) {
                currentScriptInstance.close();
            }
            jsEventListeners.clear();

            Map<String, String> moduleOverrides = new HashMap<>();
            try {
                Path utilsJsPath = scriptsDir.resolve("utils.js");
                if (Files.exists(utilsJsPath)) {
                    String utilsJsContent = Files.readString(utilsJsPath);
                    moduleOverrides.put("utils.js", utilsJsContent);
                    System.out.println("ScriptingManager: Loaded 'utils.js' for override.");
                } else {
                    System.err.println("ScriptingManager: 'utils.js' not found for override at " + utilsJsPath);
                }
            } catch (IOException e) {
                System.err.println("ScriptingManager: Error reading utils.js for override: " + e.getMessage());
                // Optionally, send this error to commandSender or log more formally
            }

            // Create the InMemoryFileSystem with overrides
            InMemoryFileSystem inMemoryFs = new InMemoryFileSystem(scriptsDir, moduleOverrides);

            currentScriptInstance = new ScriptInstance(this.bridge, new GraalVmFileSystemAdapter(inMemoryFs,Path.of("./")));
            // currentScriptInstance.evalModule(scriptPath); // Old way: evaluate the main script as a module
            // With custom FileSystem, GraalVM handles module loading via the FS for the main script too.
            // We still need to tell it which file to load as the entry point.
            currentScriptInstance.evalModule(scriptPath);

            String initialStdout = currentScriptInstance.getStdout();
            String initialStderr = currentScriptInstance.getStderr();
            String loadMessage = "Script loaded and executed: " + effectiveFileName;

            if (!initialStdout.isEmpty()) {
                System.out.println("Script stdout (" + effectiveFileName + "):\\n" + initialStdout);
            }
            if (!initialStderr.isEmpty()) {
                System.err.println("Script stderr (" + effectiveFileName + "):\\n" + initialStderr);
            }

            if (commandSender != null) {
                if (!initialStderr.isEmpty()) {
                    commandSender.sendMessage("Script stderr:\\n" + initialStderr);
                }
                commandSender.sendMessage(loadMessage);
            } else {
                System.out.println(loadMessage);
            }

        } catch (IOException e) {
            String message = "Error reading script file '" + effectiveFileName + "': " + e.getMessage();
            if (commandSender != null) commandSender.sendMessage(message);
            else System.err.println(message);
            e.printStackTrace();
        } catch (Exception e) {
            String message = "Error executing script '" + effectiveFileName + "': " + e.getMessage();
            if (commandSender != null) commandSender.sendMessage(message);
            else System.err.println(message);
            e.printStackTrace();
        }
    }

    public void registerJsEventListener(String eventName, Value jsCallback) {
        jsEventListeners.computeIfAbsent(eventName, k -> new ArrayList<>()).add(jsCallback);
    }

    public void firePlayerJoinEvent(Player player) {
        Map<String, Object> playerData = new HashMap<>();
        playerData.put("name", player.getUsername());
        playerData.put("uuid", player.getUuid().toString());
        playerData.put("sendMessage", (ProxyExecutable) (Value... args) -> {
            if (args.length > 0 && args[0].isString()) {
                String message = args[0].asString();
                player.sendMessage(message);
            }
            return null;
        });
        playerData.put("getPosition", (ProxyExecutable) (Value... args) -> {
            Pos position = player.getPosition();
            Map<String, Object> posMap = new HashMap<>();
            posMap.put("x", position.x());
            posMap.put("y", position.y());
            posMap.put("z", position.z());
            return ProxyObject.fromMap(posMap);
        });

        Map<String, Object> instanceApi = new HashMap<>();
        instanceApi.put("sendMessage", (ProxyExecutable) (Value... args) -> {
            if (args.length > 0 && args[0].isString()) {
                String message = args[0].asString();
                player.sendMessage(message);
            }
            return null;
        });
        instanceApi.put("setBlock", (ProxyExecutable) (Value... args) -> {
            if (args.length == 4 && args[0].isNumber() && args[1].isNumber() && args[2].isNumber() && args[3].isString()) {
                int x = args[0].asInt();
                int y = args[1].asInt();
                int z = args[2].asInt();
                String blockId = args[3].asString();
                Instance playerInstance = player.getInstance();
                if (playerInstance != null) {
                    Block block = Block.fromKey(blockId);
                    if (block != null) {
                        playerInstance.setBlock(x, y, z, block);
                    } else {
                        System.err.println("ScriptingManager: Invalid blockId '" + blockId + "' for setBlock.");
                    }
                } else {
                    System.err.println("ScriptingManager: Player is not in an instance for setBlock.");
                }
            } else {
                 System.err.println("ScriptingManager: Invalid arguments for instance.setBlock. Expected (x, y, z, blockId).");
            }
            return null;
        });
        playerData.put("instance", ProxyObject.fromMap(instanceApi));

        triggerJsEvent("playerJoin", player, ProxyObject.fromMap(playerData));
    }

    public void firePlayerLeaveEvent(Player player) {
        Map<String, Object> playerData = new HashMap<>();
        playerData.put("name", player.getUsername());
        playerData.put("uuid", player.getUuid().toString());
        playerData.put("sendMessage", (ProxyExecutable) (Value... args) -> {
            if (args.length > 0 && args[0].isString()) {
                String message = args[0].asString();
                player.sendMessage(message);
            }
            return null;
        });
        playerData.put("getPosition", (ProxyExecutable) (Value... args) -> {
            Pos position = player.getPosition();
            Map<String, Object> posMap = new HashMap<>();
            posMap.put("x", position.x());
            posMap.put("y", position.y());
            posMap.put("z", position.z());
            return ProxyObject.fromMap(posMap);
        });

        Map<String, Object> instanceApi = new HashMap<>();
        instanceApi.put("sendMessage", (ProxyExecutable) (Value... args) -> {
            if (args.length > 0 && args[0].isString()) {
                String message = args[0].asString();
                System.out.println("ScriptingManager: instance.sendMessage called on playerLeave for " + player.getUsername() + ": " + message + " (message not sent to player)");
            }
            return null;
        });
        instanceApi.put("setBlock", (ProxyExecutable) (Value... args) -> {
            System.err.println("ScriptingManager: instance.setBlock called on playerLeave event for " + player.getUsername() + " - operation ignored.");
            return null;
        });
        playerData.put("instance", ProxyObject.fromMap(instanceApi));

        triggerJsEvent("playerLeave", player, ProxyObject.fromMap(playerData));
    }

    private void triggerJsEvent(String eventName, Player targetOutputPlayer, Object... args) {
        List<Value> listeners = jsEventListeners.get(eventName);
        if (listeners != null && !listeners.isEmpty() && currentScriptInstance != null) {
            for (Value listener : new ArrayList<>(listeners)) {
                if (listener != null && listener.canExecute()) {
                    try {
                        listener.execute(args);

                        String eventStdout = currentScriptInstance.getStdout();
                        String eventStderr = currentScriptInstance.getStderr();
                        String scriptContextName = (this.currentScriptFileName != null) ? this.currentScriptFileName : "active script";

                        if (!eventStdout.isEmpty()) {
                            System.out.println("Event stdout (" + eventName + " in " + scriptContextName + "):\\n" + eventStdout);
                        }
                        if (!eventStderr.isEmpty()) {
                            System.err.println("Event stderr (" + eventName + " in " + scriptContextName + "):\\n" + eventStderr);
                        }

                        if (targetOutputPlayer != null) {
                            if (!eventStderr.isEmpty()) {
                                targetOutputPlayer.sendMessage("Event (" + eventName + ") stderr:\\n" + eventStderr);
                            }
                        }

                    } catch (Exception e) {
                        String errorMsg = "Error executing JS event listener for '" + eventName + "': " + e.getMessage();
                        if (targetOutputPlayer != null) targetOutputPlayer.sendMessage(errorMsg);
                        else System.err.println(errorMsg);
                        e.printStackTrace();
                    }
                } else {
                    String errorMsg = "Cannot execute JS listener for event: " + eventName + ". Listener: " + listener;
                     if (targetOutputPlayer != null) targetOutputPlayer.sendMessage(errorMsg);
                     else System.err.println(errorMsg);
                }
            }
        }
    }

    // Example broadcast method that could be called from MinestomBridge
    // public void broadcastToServer(String message) {
    //    MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(p -> p.sendMessage(message));
    // }

    public void close() {
        if (currentScriptInstance != null) {
            currentScriptInstance.close();
            currentScriptInstance = null;
        }
        jsEventListeners.clear();
        System.out.println("ScriptingManager closed and listeners cleared.");
    }
}
