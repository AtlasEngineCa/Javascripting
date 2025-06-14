package ca.atlasengine.scripting;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;
import org.graalvm.polyglot.Value;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Main {

    private static ScriptingManager scriptingManager;

    public static void main(String[] args) {
        // Initialization
        MinecraftServer minecraftServer = MinecraftServer.init();
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        scriptingManager = new ScriptingManager(); // Initialize ScriptingManager

        // Create the instance
        InstanceContainer instanceContainer = instanceManager.createInstanceContainer();
        instanceContainer.setChunkSupplier(LightingChunk::new);

        // Set the ChunkGenerator
        instanceContainer.setGenerator(unit -> unit.modifier().fillHeight(0, 40, Block.GRASS_BLOCK));

        // Add an event callback to specify the spawning instance (and the spawn position)
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            final Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            player.setRespawnPoint(new Pos(0, 42, 0));
        });

        // Instantiate and register EventHooks
        EventHooks eventHooks = new EventHooks(scriptingManager);
        eventHooks.registerEventHandlers();

        // Load the main script once on startup
        // The PlayerSpawnEvent in EventHooks will handle firing playerJoin for each player
        scriptingManager.loadAndRunScript("main.js", null);

        // Register a command to execute JavaScript
        Command jsCommand = new Command("js");
        jsCommand.setDefaultExecutor((sender, context) -> {
            sender.sendMessage("Usage: /js <javascript code>");
        });

        var scriptArgument = ArgumentType.StringArray("scriptCode");
        jsCommand.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can execute JavaScript this way.");
                return;
            }

            String scriptToExecute = String.join(" ", context.get(scriptArgument));
            player.sendMessage("Executing ad-hoc JavaScript: " + scriptToExecute);

            // Ad-hoc execution should ideally use a temporary ScriptInstance or a shared one carefully.
            // For simplicity, this example will use a new temporary instance.
            // Note: Ad-hoc scripts won't have access to listeners registered by the main script via ScriptingManager.
            ScriptInstance tempScriptInstance = null;
            try {
                // Define the root path for script execution, e.g., "scripts" directory
                Path scriptsRootPath = Paths.get("scripts").toAbsolutePath();
                // For ad-hoc scripts, we might not have specific overrides, so pass an empty map.
                Map<String, String> adhocOverrides = Collections.emptyMap(); // Or new HashMap<>();

                // First, create the InMemoryFileSystem
                InMemoryFileSystem inMemoryFs = new InMemoryFileSystem(scriptsRootPath, adhocOverrides);
                // Then, create the GraalVmFileSystemAdapter using the InMemoryFileSystem and the root path
                GraalVmFileSystemAdapter fsAdapter = new GraalVmFileSystemAdapter(inMemoryFs, scriptsRootPath);

                // Pass a new bridge for ad-hoc, or decide if ad-hoc scripts can register global listeners.
                // For now, ad-hoc scripts are fully isolated and cannot use minestom.on() to register persistent listeners.
                tempScriptInstance = new ScriptInstance(new MinestomBridge(new ScriptingManager()), fsAdapter); // Temporary, isolated bridge
                Value result = tempScriptInstance.eval(scriptToExecute);
                String stdout = tempScriptInstance.getStdout();
                String stderr = tempScriptInstance.getStderr();
                if (result != null) player.sendMessage("Result: " + (result.isHostObject() ? result.toString() : result));
                if (!stdout.isEmpty()) player.sendMessage("Stdout: " + stdout);
                if (!stderr.isEmpty()) player.sendMessage("Stderr: " + stderr);
            } catch (Exception e) {
                player.sendMessage("Error during ad-hoc script execution: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (tempScriptInstance != null) tempScriptInstance.close();
            }
        }, scriptArgument);
        MinecraftServer.getCommandManager().register(jsCommand);

        // Register a command to run JavaScript files
        Command runJsFileCommand = new Command("runjsfile");
        runJsFileCommand.setDefaultExecutor((sender, context) -> {
            sender.sendMessage("Usage: /runjsfile <filename>");
            sender.sendMessage("Reloads and runs the specified JavaScript file from the 'scripts' directory with the given scope.");
        });

        var fileNameArgument = ArgumentType.String("filename");

        runJsFileCommand.addSyntax((sender, context) -> {
            String fileName = context.get(fileNameArgument);
            Player player = (sender instanceof Player) ? (Player) sender : null;

            // Pass the player if scope is PLAYER_LOCAL, otherwise null for GLOBAL or if sender is not player
            scriptingManager.loadAndRunScript(fileName, player); // Added scope, player for command feedback
        }, fileNameArgument);

        MinecraftServer.getCommandManager().register(runJsFileCommand);

        // Load a default global script on startup, e.g., "main.js"
        scriptingManager.loadAndRunScript("main.js", null);

        // Start the server on port 25565
        minecraftServer.start("0.0.0.0", 25565);
        System.out.println("Server started. Use /runjsfile <filename> to load/reload scripts.");

        // Add a shutdown hook to close the scripting manager
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server, closing ScriptingManager...");
            if (scriptingManager != null) {
                scriptingManager.close();
            }
        }));
    }
}
