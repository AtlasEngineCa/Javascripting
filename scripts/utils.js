// scripts/utils.js
console.log("utils.js module loaded");

/**
 * A utility function to greet a player.
 * @param {string} playerName The name of the player.
 * @returns {string} A greeting message.
 */
export function greetPlayer(playerName) {
    return `Hello, ${playerName}! Welcome from the utils module!`;
}

export const utilityVersion = "1.0";

