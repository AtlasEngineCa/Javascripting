package ca.atlasengine.scripting.api;

import ca.atlasengine.scripting.MinestomBridge;
import ca.atlasengine.scripting.ScriptingManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.CommandExecutor;
import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.minecraft.ArgumentEntity;
import net.minestom.server.command.builder.arguments.number.ArgumentDouble;
import net.minestom.server.command.builder.arguments.number.ArgumentFloat;
import net.minestom.server.command.builder.arguments.number.ArgumentInteger;
import net.minestom.server.command.builder.arguments.number.ArgumentLong;

import net.minestom.server.entity.Player;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record CommandApi(ScriptingManager scriptingManager) {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandApi.class);

    public void register(Value commandDefinitionValue) {
        if (!commandDefinitionValue.hasMembers()) {
            LOGGER.error("Scripting: Command definition must be an object.");
            return;
        }

        String commandName = commandDefinitionValue.getMember("name").asString();
        if (commandName == null || commandName.isBlank()) {
            LOGGER.error("Scripting: Command name is required in definition.");
            return;
        }

        Value syntaxesValue = commandDefinitionValue.getMember("syntaxes");
        if (syntaxesValue == null || !syntaxesValue.hasArrayElements() || syntaxesValue.getArraySize() == 0) {
            LOGGER.error("Scripting: Command '{}' must have at least one syntax defined.", commandName);
            return;
        }

        DynamicScriptCommand command = new DynamicScriptCommand(commandName, scriptingManager, this.scriptingManager.getBridge());

        for (int i = 0; i < syntaxesValue.getArraySize(); i++) {
            Value syntaxValue = syntaxesValue.getArrayElement(i);
            Value jsHandler = syntaxValue.getMember("handler");

            if (jsHandler == null || !jsHandler.canExecute()) {
                LOGGER.error("Scripting: Syntax {} for command '{}' is missing a valid handler function.", i, commandName);
                continue;
            }

            List<Argument<?>> minestomArgs = new ArrayList<>();
            if (syntaxValue.hasMember("arguments") && syntaxValue.getMember("arguments").hasArrayElements()) {
                Value argumentsValue = syntaxValue.getMember("arguments");
                for (int j = 0; j < argumentsValue.getArraySize(); j++) {
                    Value argDefValue = argumentsValue.getArrayElement(j);
                    Argument<?> minestomArg = parseArgumentDefinition(argDefValue);
                    if (minestomArg != null) {
                        minestomArgs.add(minestomArg);
                    } else {
                        LOGGER.warn("Scripting: Could not parse argument definition for command '{}', syntax {}: {}. Skipping argument.", commandName, i, argDefValue);
                    }
                }
            }
            command.addScriptSyntax(jsHandler, minestomArgs.toArray(new Argument[0]));
        }

        CommandManager commandManager = MinecraftServer.getCommandManager();
        if (commandManager.getCommand(commandName) != null) {
            LOGGER.warn("Scripting: Command '{}' is already registered. It will be overwritten by the script.", commandName);
        }
        commandManager.register(command);
        scriptingManager.trackRegisteredCommand(commandName);
        LOGGER.info("Scripting: Registered dynamic command '{}' from script.", commandName);
    }

    private Argument<?> parseArgumentDefinition(Value argDefValue) {
        String name = argDefValue.getMember("name").asString();
        String type = argDefValue.getMember("type").asString().toLowerCase();

        switch (type) {
            case "string":
                return ArgumentType.String(name);
            case "word":
                return ArgumentType.Word(name);
            case "greedystring":
                return ArgumentType.StringArray(name);
            case "integer":
                ArgumentInteger intArg = ArgumentType.Integer(name);
                if (argDefValue.hasMember("min")) intArg.min(argDefValue.getMember("min").asInt());
                if (argDefValue.hasMember("max")) intArg.max(argDefValue.getMember("max").asInt());
                return intArg;
            case "float":
                ArgumentFloat floatArg = ArgumentType.Float(name);
                if (argDefValue.hasMember("min")) floatArg.min(argDefValue.getMember("min").asFloat());
                if (argDefValue.hasMember("max")) floatArg.max(argDefValue.getMember("max").asFloat());
                return floatArg;
            case "double":
                ArgumentDouble doubleArg = ArgumentType.Double(name);
                if (argDefValue.hasMember("min")) doubleArg.min(argDefValue.getMember("min").asDouble());
                if (argDefValue.hasMember("max")) doubleArg.max(argDefValue.getMember("max").asDouble());
                return doubleArg;
            case "long":
                ArgumentLong longArg = ArgumentType.Long(name);
                if (argDefValue.hasMember("min")) longArg.min(argDefValue.getMember("min").asLong());
                if (argDefValue.hasMember("max")) longArg.max(argDefValue.getMember("max").asLong());
                return longArg;
            case "boolean":
                return ArgumentType.Boolean(name);
            case "player":
                ArgumentEntity playerArg = ArgumentType.Entity(name).singleEntity(true).onlyPlayers(true);
                if (argDefValue.hasMember("singleOnly") && !argDefValue.getMember("singleOnly").asBoolean()) {
                    playerArg.singleEntity(false);
                }
                return playerArg;
            case "entity":
                ArgumentEntity entityArg = ArgumentType.Entity(name);
                boolean singleOnly = !argDefValue.hasMember("singleOnly") || argDefValue.getMember("singleOnly").asBoolean();
                entityArg.singleEntity(singleOnly);
                if (argDefValue.hasMember("playersOnly") && argDefValue.getMember("playersOnly").asBoolean()) {
                    entityArg.onlyPlayers(true);
                }
                return entityArg;
            case "uuid":
                return ArgumentType.UUID(name);
            case "command":
                return ArgumentType.Command(name);
            case "component":
                return ArgumentType.Component(name);
            case "itemstack":
                return ArgumentType.ItemStack(name);
            case "blockposition":
                return ArgumentType.RelativeBlockPosition(name);
            case "vec2":
                return ArgumentType.RelativeVec2(name);
            case "vec3":
                return ArgumentType.RelativeVec3(name);
            case "color":
                return ArgumentType.Color(name);
            case "time":
                return ArgumentType.Time(name);
            case "resourcelocation":
                return ArgumentType.ResourceLocation(name);
            case "enum":
                if (argDefValue.hasMember("enumValues") && argDefValue.getMember("enumValues").hasArrayElements()) {
                    Value enumValuesJs = argDefValue.getMember("enumValues");
                    String[] enumValues = new String[(int) enumValuesJs.getArraySize()];
                    for (int i = 0; i < enumValuesJs.getArraySize(); i++) {
                        enumValues[i] = enumValuesJs.getArrayElement(i).asString();
                    }
                    return ArgumentType.Word(name).from(enumValues);
                }
                LOGGER.warn("Scripting: Enum argument '{}' requires 'enumValues' array.", name);
                return null;
            default:
                LOGGER.warn("Scripting: Unsupported argument type '{}' for argument '{}'.", type, name);
                return null;
        }
    }
}

class DynamicScriptCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicScriptCommand.class);
    private final ScriptingManager scriptingManager;
    private final MinestomBridge bridge;

    public DynamicScriptCommand(String name, ScriptingManager scriptingManager, MinestomBridge bridge) {
        super(name);
        this.scriptingManager = scriptingManager;
        this.bridge = bridge;
    }

    public void addScriptSyntax(Value jsHandler, Argument<?>... args) {
        var commandExecutioner = new CommandExecutor() {
            @Override
            public void apply(@NotNull CommandSender sender, @NotNull CommandContext context) {
                executeJsHandler(sender, context, jsHandler);
            }
        };

        if (args == null || args.length == 0) {
            this.setDefaultExecutor(commandExecutioner);
        } else {
            this.addSyntax(commandExecutioner, args);
        }
    }

    private void executeJsHandler(CommandSender sender, CommandContext context, Value specificJsHandler) {
        if (scriptingManager.getCurrentScriptInstance() == null) {
            sender.sendMessage("Error: Scripting engine is not ready for command " + getName());
            return;
        }

        Map<String, Object> senderProxyMap = new HashMap<>();
        senderProxyMap.put("name", sender instanceof Player ? ((Player) sender).getUsername() : "CONSOLE");
        senderProxyMap.put("sendMessage", (ProxyExecutable) (Value... execArgs) -> {
            if (execArgs.length > 0 && execArgs[0].isString()) {
                sender.sendMessage(execArgs[0].asString());
            }
            return null;
        });
        senderProxyMap.put("isPlayer", (ProxyExecutable) (Value... execArgs) -> sender instanceof Player);
        if (sender instanceof Player) {
            senderProxyMap.put("uuid", ((Player) sender).getUuid().toString());
        } else {
            senderProxyMap.put("uuid", null);
        }
        ProxyObject senderProxy = ProxyObject.fromMap(senderProxyMap);

        Map<String, Object> contextProxyMap = new HashMap<>();
        contextProxyMap.put("get", (ProxyExecutable) (Value... execArgs) -> {
            if (execArgs.length > 0 && execArgs[0].isString()) {
                String argName = execArgs[0].asString();
                Object rawValue = context.get(argName);

                if (rawValue instanceof Player && bridge != null) {
                    Map<String, Object> playerProxyData = new HashMap<>();
                    Player p = (Player) rawValue;
                    playerProxyData.put("name", p.getUsername());
                    playerProxyData.put("uuid", p.getUuid().toString());
                    playerProxyData.put("sendMessage", (ProxyExecutable) (msgArgs) -> {
                        if (msgArgs.length > 0 && msgArgs[0].isString()) p.sendMessage(msgArgs[0].asString());
                        return null;
                    });
                    playerProxyData.put("setGameMode", (ProxyExecutable) (gmArgs) -> {
                         if (gmArgs.length > 0 && gmArgs[0].isString()) {
                            return bridge.setPlayerGamemode(p.getUuid().toString(), gmArgs[0].asString());
                        }
                        return false;
                    });
                    return ProxyObject.fromMap(playerProxyData);
                }
                // TODO: Proxy other complex types like Entity, ItemStack, List<Player/Entity>
                return rawValue;
            }
            return null;
        });
        ProxyObject contextProxy = ProxyObject.fromMap(contextProxyMap);

        try {
            specificJsHandler.execute(senderProxy, contextProxy);
            String stdout = scriptingManager.getCurrentScriptInstance().getStdout();
            if (stdout != null && !stdout.isEmpty()) {
                LOGGER.error("Script command '{}' STDOUT: {}", getName(), stdout.trim());
            }
            String stderr = scriptingManager.getCurrentScriptInstance().getStderr();
            if (stderr != null && !stderr.isEmpty()) {
                LOGGER.error("Script command '{}' STDERR: {}", getName(), stderr.trim());
                sender.sendMessage("Script Error (see console): " + stderr.lines().findFirst().orElse("Unknown error"));
            }
        } catch (Exception e) {
            LOGGER.error("Error executing JS command handler for '{}': {}", getName(), e.getMessage(), e);
            sender.sendMessage("Internal error executing command " + getName());
        }
    }
}