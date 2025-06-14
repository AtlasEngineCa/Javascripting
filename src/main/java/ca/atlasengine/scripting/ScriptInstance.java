package ca.atlasengine.scripting;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.ResourceLimits;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.nio.file.Path; // Added import
import java.io.IOException; // Added import
import org.graalvm.polyglot.Source; // Added import
import ca.atlasengine.scripting.GraalVmFileSystemAdapter; // Import the adapter

public class ScriptInstance {

    private final Context context;
    private final ByteArrayOutputStream stdoutBuffer;
    private final ByteArrayOutputStream stderrBuffer;
    private static final long MAX_STATEMENT_COUNT = 100000; // Increased for event handling scripts
    private static final Duration MAX_EXECUTION_TIME = Duration.ofSeconds(10); // Increased for event handling scripts

    // Modified constructor to accept GraalVmFileSystemAdapter
    public ScriptInstance(MinestomBridge bridge, GraalVmFileSystemAdapter fileSystemAdapter) {
        this.stdoutBuffer = new ByteArrayOutputStream();
        this.stderrBuffer = new ByteArrayOutputStream();

        Context.Builder contextBuilder = Context.newBuilder("js")
                .allowHostAccess(HostAccess.EXPLICIT)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .allowCreateThread(false)
                .allowNativeAccess(false)
                .allowEnvironmentAccess(org.graalvm.polyglot.EnvironmentAccess.NONE)
                .out(this.stdoutBuffer)
                .err(this.stderrBuffer)
                .resourceLimits(ResourceLimits.newBuilder()
                        .statementLimit(MAX_STATEMENT_COUNT, null)
                        .build())
                .option("js.ecmascript-version", "2022");

        if (fileSystemAdapter != null) {
            contextBuilder.fileSystem(fileSystemAdapter); // Use the adapter
            contextBuilder.allowIO(true); // AllowIO is still needed for the custom FS to operate
        } else {
            contextBuilder.allowIO(true); // Fallback to default IO if no adapter
        }

        this.context = contextBuilder.build();

        // Expose the bridge to JavaScript under the global name "minestom"
        this.context.getBindings("js").putMember("minestom", bridge);
    }

    // Getter for the GraalVM context
    public Context getGraalvmContext() {
        return this.context;
    }

    public Value eval(String script) { // This is for dynamic string evaluation
        if (this.context == null) {
            throw new IllegalStateException("Context is not initialized or has been closed.");
        }
        try {
            return this.context.eval("js", script);
        } catch (PolyglotException e) {
            System.err.println("Script execution error: " + e.getMessage());
            if (e.isCancelled()) {
                System.err.println("Script execution was cancelled.");
            }
            if (e.isHostException()) {
                System.err.println("Host exception: " + e.asHostException().toString());
            }
            return null;
        }
    }

    // New method for evaluating a script file as an ES module
    public Value evalModule(Path scriptPath) throws IOException {
        if (this.context == null) {
            throw new IllegalStateException("Context is not initialized or has been closed.");
        }
        try {
            // Create a Source object from the script file path
            Source source = Source.newBuilder("js", scriptPath.toUri().toURL())
                                  .mimeType("application/javascript+module") // Specify that this is an ES module
                                  .build();
            return this.context.eval(source);
        } catch (PolyglotException e) {
            System.err.println("Script module execution error (" + scriptPath.toString() + "): " + e.getMessage());
            if (e.isCancelled()) {
                System.err.println("Script execution was cancelled.");
            }
            if (e.isHostException()) {
                System.err.println("Host exception: " + e.asHostException().toString());
            }
            // Consider using a logging framework instead of e.printStackTrace()
            // e.printStackTrace();
            return null; // Or rethrow as a custom exception
        }
    }

    public String getStdout() {
        String output = stdoutBuffer.toString();
        stdoutBuffer.reset(); // Clear the buffer after reading
        return output;
    }

    public String getStderr() {
        String output = stderrBuffer.toString();
        stderrBuffer.reset(); // Clear the buffer after reading
        return output;
    }

    public void close() {
        if (this.context != null) {
            this.context.close();
        }
    }
}
