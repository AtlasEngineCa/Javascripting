\
// Type definitions for the Minestom JavaScript API

/**
 * Represents the Minestom bridge object available in the JavaScript context.
 */
declare const minestom: {
    /**
     * Registers an event listener.
     * @param eventName The name of the event to listen for (e.g., 'playerJoin', 'playerLeave').
     * @param jsCallback The function to execute when the event occurs.
     *                   The callback will receive a player object as its first argument.
     */
    on: (eventName: 'playerJoin' | 'playerLeave', jsCallback: (player: Player) => void) => void;
    on: (eventName: 'playerBlockInteract', jsCallback: (event: PlayerBlockInteractEventDetails) => void) => void;

    /**
     * Broadcasts a message to all players on the server.
     * @param message The message to broadcast.
     */
    broadcastMessage: (message: string) => void;

    /**
     * Schedules a task to be executed after a specified delay in ticks.
     * @param delayInTicks The number of ticks to wait before executing the task.
     * @returns A Promise that resolves when the task is executed.
     */
    schedule: (delayInTicks: number) => Promise<void>;

    /**
     * Registers a new server command based on the provided definition.
     * @param definition The command definition object.
     */
    registerCommand: (definition: ScriptCommandDefinition) => void;
};

/**
 * Represents a player object.
 */
interface Player {
    /**
     * The name of the player.
     */
    name: string;

    /**
     * The UUID of the player.
     */
    uuid: string;

    /**
     * Sends a message to this player.
     * @param message The message to send.
     */
    sendMessage: (message: string) => void;

    /**
     * Gets the current position of the player.
     * @returns An object with x, y, and z coordinates.
     */
    getPosition: () => { x: number; y: number; z: number };

    /**
     * Provides access to the player's current instance (world/dimension).
     */
    instance: PlayerInstance;

    /**
     * Sets the gamemode for this player.
     * @param gameModeName The name of the gamemode (e.g., "SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR").
     *                     Case-insensitive, but it's good practice to use uppercase or lowercase consistently.
     * @returns True if the gamemode was set successfully, false otherwise (e.g., invalid gamemode).
     */
    setGameMode: (gameModeName: "SURVIVAL" | "CREATIVE" | "ADVENTURE" | "SPECTATOR" | "survival" | "creative" | "adventure" | "spectator") => boolean;
}

/**
 * Details for the playerBlockInteract event.
 */
interface PlayerBlockInteractEventDetails {
    player: Player;
    position: { x: number; y: number; z: number };
    block: { id: string; namespaceId: string; };
    hand: 'main_hand' | 'off_hand';
}

/**
 * Represents the instance (world/dimension) a player is in.
 */
interface PlayerInstance {
    /**
     * Sends a message to the player associated with this instance object.
     * @param message The message to send.
     */
    sendMessage: (message: string) => void;

    /**
     * Sets a block in the instance at the given coordinates.
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     * @param z The z-coordinate.
     * @param blockId The namespace ID of the block (e.g., "minecraft:stone").
     */
    setBlock: (x: number, y: number, z: number, blockId: string) => void;
}

/**
 * Represents the sender of a script-defined command.
 */
interface ScriptCommandSender {
    /**
     * The name of the command sender (e.g., player's username or "CONSOLE").
     */
    name: string;

    /**
     * Sends a message to the command sender.
     * @param message The message to send.
     */
    sendMessage: (message: string) => void;

    /**
     * Checks if the command sender is a player.
     * @returns True if the sender is a player, false otherwise (e.g., console).
     */
    isPlayer: () => boolean;

    /**
     * The UUID of the player, if the sender is a player. Otherwise, undefined.
     */
    uuid?: string; // Optional, only if isPlayer is true

    // Potentially add more player-specific methods here or encourage type assertion in JS
    // e.g., getPosition: () => { x: number; y: number; z: number }; (if isPlayer)
}

// New interfaces for command definition
export interface ScriptArgumentDefinition {
    name: string;
    type: "string" | "word" | "greedystring" |
          "integer" | "float" | "double" | "long" |
          "boolean" |
          "player" | "entity" | "uuid" |
          "command" | "component" | "itemstack" | "blockposition" |
          "vec2" | "vec3" | "color" | "time" | "resourcelocation" | "potion" |
          "enum";
    // Optional properties for specific types
    optional?: boolean; // Note: Minestom handles optionality via multiple syntaxes or argument defaults
    defaultValue?: any;
    enumValues?: string[]; // For type: "enum"
    singleOnly?: boolean;  // For type: "entity", "player". True by default for player.
    playersOnly?: boolean; // For type: "entity". False by default.
    min?: number;          // For numeric types
    max?: number;          // For numeric types
}

export interface ScriptCommandContext {
    /**
     * Gets the parsed value of an argument for the current command execution.
     * @param argumentName The name of the argument as defined in ScriptArgumentDefinition.
     * @returns The parsed argument value. Type depends on the argument definition.
     *          Complex types like Player or Entity will be proxied objects.
     */
    get: <T = any>(argumentName: string) => T;
}

export interface ScriptCommandSyntax {
    arguments?: ScriptArgumentDefinition[];
    handler: (sender: ScriptCommandSender, context: ScriptCommandContext) => void;
}

export interface ScriptCommandDefinition {
    name: string;
    description?: string; // For documentation or help commands
    syntaxes: ScriptCommandSyntax[];
}

// Add a JSDoc reference in your main.js to point to this file
// For example, at the top of main.js:
// /// <reference path=\"./minestom-api.d.ts\" />
