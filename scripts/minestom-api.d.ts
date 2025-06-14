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

// Add a JSDoc reference in your main.js to point to this file
// For example, at the top of main.js:
// /// <reference path=\"./minestom-api.d.ts\" />
