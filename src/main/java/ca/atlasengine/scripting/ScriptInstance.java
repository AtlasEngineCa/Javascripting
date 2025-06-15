package ca.atlasengine.scripting;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.ResourceLimits;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.io.IOException;
import org.graalvm.polyglot.Source;

public class ScriptInstance {

    private final Context context;
    private final ByteArrayOutputStream stdoutBuffer;
    private final ByteArrayOutputStream stderrBuffer;
    private static final long MAX_STATEMENT_COUNT = 100000;

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
            contextBuilder.fileSystem(fileSystemAdapter);
            contextBuilder.allowIO(true);
        } else {
            contextBuilder.allowIO(true);
        }

        this.context = contextBuilder.build();

        // Expose the bridge to JavaScript under the global name "minestom"
        this.context.getBindings("js").putMember("minestom", bridge);
    }

    public Context getGraalvmContext() {
        return this.context;
    }

    public Value eval(String script) {
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

    public void evalModule(Path scriptPath) throws IOException {
        if (this.context == null) {
            throw new IllegalStateException("Context is not initialized or has been closed.");
        }
        try {
            Source source = Source.newBuilder("js", scriptPath.toUri().toURL())
                                  .mimeType("application/javascript+module")
                                  .build();
            this.context.eval(source);
        } catch (PolyglotException e) {
            System.err.println("Script module execution error (" + scriptPath + "): " + e.getMessage());
            if (e.isCancelled()) {
                System.err.println("Script execution was cancelled.");
            }
            if (e.isHostException()) {
                System.err.println("Host exception: " + e.asHostException().toString());
            }
        }
    }

    public String getStdout() {
        String output = stdoutBuffer.toString();
        stdoutBuffer.reset();
        return output;
    }

    public String getStderr() {
        String output = stderrBuffer.toString();
        stderrBuffer.reset();
        return output;
    }

    public void close() {
        if (this.context != null) {
            this.context.close();
        }
    }
}
