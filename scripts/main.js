/// <reference path="./minestom-api.d.ts" />

import { greetPlayer, utilityVersion } from './utils.js';
import './customCommands.js';

console.log("Main JavaScript file loaded!");
console.log(`Utility version: ${utilityVersion}`);

minestom.on('playerJoin', function(player) {
    console.log("Player joined from JS! Name: " + player.name + ", UUID: " + player.uuid);
    const joinMessage = player.name + " has joined the server!";

    minestom.broadcastMessage(joinMessage);
    player.sendMessage("Welcome to the server, " + player.name + "!");

    const greeting = greetPlayer(player.name);
    player.sendMessage(greeting);

    player.setGameMode("creative");

    if (player.instance) {
        minestom.schedule(200).then(() => {
            const pos = player.getPosition();
            player.instance.setBlock(Math.floor(pos.x), Math.floor(pos.y) + 2, Math.floor(pos.z), "minecraft:gold_block");
            player.sendMessage("The gold block above your head was placed by a scheduled task!");
            console.log("Scheduled task executed for player: " + player.name);
        }).catch(error => {
            console.error("Error in scheduled task for player " + player.name + ":", error);
        });
    }
});

minestom.on('playerMove', (event) => {
    if (event.isOnGround) {
        event.player.instance.setBlock(
            event.position.x,
            event.position.y - 1,
            event.position.z,
            "minecraft:diamond_block"
        )
    }
})

minestom.on('playerBlockInteract', (event) => {
    console.log(`Player ${event.player.name} interacted with block at ${event.position.x}, ${event.position.y}, ${event.position.z} using ${event.hand}. Block ID: ${event.block.id}, Namespace ID: ${event.block.namespaceId}`);
    event.player.sendMessage(`You interacted with a block at ${event.position.x}, ${event.position.y}, ${event.position.z} using your ${event.hand}. Block ID: ${event.block.id}, Namespace ID: ${event.block.namespaceId}`);
});

minestom.on('playerLeave', function(player) {
    console.log("Player left from JS! Name: " + player.name + ", UUID: " + player.uuid);
    const leaveMessage = player.name + " has left the server.";

    minestom.broadcastMessage(leaveMessage);
});

console.log("main.js: Registered playerJoin listener.");
console.log("main.js: Registered playerLeave listener.");
