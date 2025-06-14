/// <reference path="./minestom-api.d.ts" />

// Import functions/variables from other modules
import { greetPlayer, utilityVersion } from './utils.js';

console.log("Main JavaScript file loaded!");
console.log(`Utility version: ${utilityVersion}`);

minestom.on('playerJoin', function(player) {
    console.log("Player joined from JS! Name: " + player.name + ", UUID: " + player.uuid);
    const joinMessage = player.name + " has joined the server!";

    minestom.broadcastMessage(joinMessage);
    player.sendMessage("Welcome to the server, " + player.name + "!");

    // Use the imported function
    const greeting = greetPlayer(player.name);
    player.sendMessage(greeting);

    const pos = player.getPosition();

    if (player.instance && pos) {
        minestom.schedule(200).then(() => {
            player.instance.setBlock(Math.floor(pos.x), Math.floor(pos.y) + 2, Math.floor(pos.z), "minecraft:gold_block");
            player.sendMessage("The gold block above your head was placed by a scheduled task!");
            console.log("Scheduled task executed for player: " + player.name);
        }).catch(error => {
            console.error("Error in scheduled task for player " + player.name + ":", error);
        });
    }
});

minestom.on('playerLeave', function(player) {
    console.log("Player left from JS! Name: " + player.name + ", UUID: " + player.uuid);
    const leaveMessage = player.name + " has left the server.";

    minestom.broadcastMessage(leaveMessage);
});

// To make testFunction callable from an ad-hoc /js command, you'd need to expose it globally in a way
// that a new ScriptInstance could pick up, or use a more sophisticated script management system.
// For now, functions defined here are primarily for use by event handlers within this script context.

console.log("main.js: Registered playerJoin listener.");
console.log("main.js: Registered playerLeave listener."); // Added log for new listener
