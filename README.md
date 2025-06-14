# Minestom Javascripting Project

This project enables JavaScript scripting for Minestom Minecraft servers, bridging Minestom's Java API with a JavaScript runtime. Write server-side game logic, custom commands, and event handlers in JavaScript.

## Key Features

*   **JavaScript Scripting:** Develop server logic using JavaScript.
*   **Minestom API Access:** Interact with the Minestom API (players, worlds, events, etc.) from JavaScript.
*   **Custom In-Game Commands:** Define new commands using JavaScript.
*   **In-Memory File Loading:** Dynamically load/update JavaScript files from memory via `InMemoryFileSystem`, overriding physical files for flexible script management.

## Project Structure Overview

*   `scripts/`: Contains your JavaScript files (`main.js`, `customCommands.js`, etc.) and TypeScript definitions (`minestom-api.d.ts`).
*   `src/main/java/`: Java source code for the JavaScript bridge (GraalVM) and Minestom API exposure.
    *   `ca/atlasengine/scripting/`: Core scripting engine and `InMemoryFileSystem`.
    *   `ca/atlasengine/scripting/api/`: Java classes exposing Minestom features to JavaScript.
*   `build.gradle.kts`: Gradle build file.

## Getting Started

(To be filled in with specific instructions on how to build and run the project)

1.  **Prerequisites:**
    *   Java Development Kit (JDK)
    *   Gradle
2.  **Build the project:**
    ```bash
    ./gradlew build
    ```
3.  **Run the server:**
    (Instructions on how to launch the server with the JavaScript scripts)
