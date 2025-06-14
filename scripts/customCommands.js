/// <reference path="./minestom-api.d.ts" />

console.log("Loading custom commands...");

minestom.registerCommand({
    name: "jscmd",
    aliases: ["javascriptcommand"],
    description: "An example command implemented in JavaScript.",
    syntaxes: [
        {
            // /jscmd  (no arguments)
            handler: (sender, context) => {
                sender.sendMessage("==== JS Command Help ====");
                sender.sendMessage("/jscmd info - Display your info");
                sender.sendMessage("/jscmd echo <message> - Echoes a message back");
                sender.sendMessage("/jscmd setlevel <player> <level> - Sets a player's level (concept)");
            }
        },
        {
            // /jscmd info
            arguments: [
                { name: "subcommand", type: "word", enumValues: ["info"] } // Literal argument
            ],
            handler: (sender, context) => {
                sender.sendMessage(`Your name: ${sender.name}`);
                if (sender.isPlayer()) {
                    sender.sendMessage(`Your UUID: ${sender.uuid}`);
                } else {
                    sender.sendMessage("You are the console.");
                }
            }
        },
        {
            // /jscmd echo <message>
            arguments: [
                { name: "message", type: "greedystring" }
            ],
            handler: (sender, context) => {
                const message = context.get("message");
                sender.sendMessage(`Echo: ${message}`);
            }
        },
        {
            arguments: [
                { name: "targetPlayer", type: "player" },
                { name: "level", type: "integer", min: 0, max: 100 }
            ],
            handler: (sender, context) => {
                const targetPlayer = context.get("targetPlayer");
                const level = context.get("level");

                if (targetPlayer) {
                    sender.sendMessage(`Attempting to set ${targetPlayer.name}'s level to ${level}.`);
                    targetPlayer.sendMessage(`${sender.name} tried to set your level to ${level}.`);
                    if (targetPlayer.setGameMode) {
                        targetPlayer.setGameMode("SURVIVAL");
                        sender.sendMessage(`${targetPlayer.name} was also set to survival mode for fun!`);
                    }
                } else {
                    sender.sendMessage("Player not found for setlevel.");
                }
            }
        }
    ]
});

console.log("Custom command /jscmd registered.");