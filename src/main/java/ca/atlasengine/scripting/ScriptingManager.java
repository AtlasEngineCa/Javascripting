package ca.atlasengine.scripting;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.command.builder.Command;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
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
import java.util.Set;
import java.util.HashSet;

public class ScriptingManager {
    private ScriptInstance currentScriptInstance;
    private String currentScriptFileName;
    private final MinestomBridge bridge;
    private final Map<String, List<Value>> jsEventListeners = new HashMap<>();
    private final Path scriptsDir = Paths.get("scripts");
    private final Set<String> registeredScriptCommands = new HashSet<>();

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
        this.currentScriptFileName = scriptPath.getFileName().toString();
        String effectiveFileName = this.currentScriptFileName;

        if (!Files.exists(scriptPath)) {
            String message = "Error: Script file not found: " + scriptPath;
            if (commandSender != null) commandSender.sendMessage(message);
            else System.err.println(message);
            return;
        }

        try {
            if (currentScriptInstance != null) {
                currentScriptInstance.close();
                unregisterScriptCommands();
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
            }

            // Create the InMemoryFileSystem with overrides
            InMemoryFileSystem inMemoryFs = new InMemoryFileSystem(scriptsDir, moduleOverrides);

            currentScriptInstance = new ScriptInstance(this.bridge, new GraalVmFileSystemAdapter(inMemoryFs,Path.of("./")));
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

    public void trackRegisteredCommand(String commandName) {
        registeredScriptCommands.add(commandName);
    }

    private void unregisterScriptCommands() {
        CommandManager commandManager = MinecraftServer.getCommandManager();
        for (String commandName : registeredScriptCommands) {
            Command existingCommand = commandManager.getCommand(commandName);
            if (existingCommand != null) {
                commandManager.unregister(existingCommand); // Minestom unregisters by Command object
                System.out.println("ScriptingManager: Unregistered command '" + commandName + "' from script.");
            }
        }
        registeredScriptCommands.clear();
    }

    public void registerJsEventListener(String eventName, Value jsCallback) {
        jsEventListeners.computeIfAbsent(eventName, k -> new ArrayList<>()).add(jsCallback);
    }

    public void firePlayerJoinEvent(Player player) {
        Map<String, Object> playerData = createPlayerProxyData(player, true);
        triggerJsEvent("playerJoin", player, ProxyObject.fromMap(playerData));
    }

    public void firePlayerLeaveEvent(Player player) {
        Map<String, Object> playerData = createPlayerProxyData(player, false);
        triggerJsEvent("playerLeave", player, ProxyObject.fromMap(playerData));
    }

    public void firePlayerMoveEvent(Player player, Pos newPosition, boolean isOnGround) {
        Map<String, Object> eventData = new HashMap<>();

        // Add player data to the event
        eventData.put("player", ProxyObject.fromMap(createPlayerProxyData(player, true)));

        // New position
        Map<String, Object> positionData = new HashMap<>();
        positionData.put("x", newPosition.x());
        positionData.put("y", newPosition.y());
        positionData.put("z", newPosition.z());
        eventData.put("position", ProxyObject.fromMap(positionData));

        // Ground state
        eventData.put("isOnGround", isOnGround);

        triggerJsEvent("playerMove", player, ProxyObject.fromMap(eventData));
    }

    public void firePlayerBlockInteractEvent(Player player, BlockVec blockPosition, Block block, PlayerHand hand) {
        Map<String, Object> eventData = new HashMap<>();
        
        // Add player data to the event
        eventData.put("player", ProxyObject.fromMap(createPlayerProxyData(player, true)));

        // Block information
        Map<String, Object> blockData = new HashMap<>();
        blockData.put("id", block.name());
        blockData.put("namespaceId", block.key().asString());
        eventData.put("block", ProxyObject.fromMap(blockData));

        // Block position
        Map<String, Object> positionData = new HashMap<>();
        positionData.put("x", blockPosition.x());
        positionData.put("y", blockPosition.y());
        positionData.put("z", blockPosition.z());
        eventData.put("position", ProxyObject.fromMap(positionData));

        // Hand information
        eventData.put("hand", hand == PlayerHand.MAIN  ? "main_hand" : "off_hand");

        triggerJsEvent("playerBlockInteract", player, ProxyObject.fromMap(eventData));
    }

    private Map<String, Object> createPlayerProxyData(Player player, boolean includeInstance) {
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

        // Add setGameMode to the Player object proxy
        playerData.put("setGameMode", (ProxyExecutable) (Value... args) -> {
            if (args.length > 0 && args[0].isString()) {
                String gameModeName = args[0].asString();

                GameMode gameMode = switch (gameModeName.toLowerCase()) {
                    case "survival" -> GameMode.SURVIVAL;
                    case "creative" -> GameMode.CREATIVE;
                    case "adventure" -> GameMode.ADVENTURE;
                    case "spectator" -> GameMode.SPECTATOR;
                    default -> null;
                };

                if (gameMode == null) {
                    System.err.println("ScriptingManager: Invalid game mode '" + gameModeName + "'. Valid modes are: survival, creative, adventure, spectator.");
                    return false;
                }
                player.setGameMode(gameMode);
                return true;
            }
            System.err.println("ScriptingManager: Invalid arguments for player.setGameMode. Expected (gameModeName: string).");
            return false;
        });

        if (includeInstance && player.getInstance() != null) {
            playerData.put("instance", createInstanceProxyData(player.getInstance(), true));
        }

        return playerData;
    }

    private ProxyObject createInstanceProxyData(Instance instance, boolean allowModification) {
        Map<String, Object> instanceApi = new HashMap<>();
        if (instance != null) {
            instanceApi.put("uuid", instance.getUuid());

            instanceApi.put("getBlock", (ProxyExecutable) (Value... args) -> {
                if (args.length == 3 && args[0].isNumber() && args[1].isNumber() && args[2].isNumber()) {
                    int x = args[0].asInt();
                    int y = args[1].asInt();
                    int z = args[2].asInt();
                    Block block = instance.getBlock(x, y, z);
                    return block != null ? block.toString() : "minecraft:air";
                } else {
                    System.err.println("ScriptingManager: Invalid arguments for instance.getBlock. Expected (x, y, z).");
                    return "minecraft:air";
                }
            });

            instanceApi.put("setBlock", (ProxyExecutable) (Value... args) -> {
                if (allowModification) {
                    if (args.length == 4 &&
                        (args[0].isNumber()) &&
                        (args[1].isNumber()) &&
                        (args[2].isNumber()) &&
                        args[3].isString()) {

                        double xDouble = args[0].asDouble();
                        double yDouble = args[1].asDouble();
                        double zDouble = args[2].asDouble();

                        int x = (int) Math.floor(xDouble);
                        int y = (int) Math.floor(yDouble);
                        int z = (int) Math.floor(zDouble);

                        String blockId = args[3].asString();
                        Block block = Block.fromKey(blockId);
                        if (block != null) {
                            instance.setBlock(x, y, z, block);
                        } else {
                            System.err.println("ScriptingManager: Invalid blockId '" + blockId + "' for instance.setBlock.");
                        }
                    } else {
                        System.err.println("ScriptingManager: Invalid arguments for instance.setBlock. Expected (x: number, y: number, z: number, blockId: string).");
                    }
                } else {
                    System.err.println("ScriptingManager: instance.setBlock called when modification is not allowed for instance " + instance.getUuid() + " - operation ignored.");
                }
                return null;
            });

            instanceApi.put("getBlock", (ProxyExecutable) (Value... args) -> {
                if (args.length == 3 && args[0].isNumber() && args[1].isNumber() && args[2].isNumber()) {
                    int x = args[0].asInt();
                    int y = args[1].asInt();
                    int z = args[2].asInt();
                    Block block = instance.getBlock(x, y, z);
                    return block != null ? block.toString() : "minecraft:air";
                } else {
                    System.err.println("ScriptingManager: Invalid arguments for instance.getBlock. Expected (x, y, z).");
                    return "minecraft:air";
                }
            });

            instanceApi.put("sendMessage", (ProxyExecutable) (Value... args) -> {
                if (allowModification) {
                    if (args.length > 0 && args[0].isString()) {
                        String message = args[0].asString();
                        instance.getPlayers().forEach(p -> p.sendMessage(message));
                    }
                } else {
                    System.out.println("ScriptingManager: instance.sendMessage called when modification is not allowed for instance " + instance.getUuid() + " (message not sent).");
                }
                return null;
            });
        } else {
            System.err.println("ScriptingManager: createInstanceProxyData called with null instance.");
        }
        return ProxyObject.fromMap(instanceApi);
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

    public void close() {
        if (currentScriptInstance != null) {
            currentScriptInstance.close();
            currentScriptInstance = null;
        }
        unregisterScriptCommands();
        jsEventListeners.clear();
        System.out.println("ScriptingManager closed and listeners cleared.");
    }
}
