package ca.atlasengine.scripting;

import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import java.util.Set;

public class GraalVmFileSystemAdapter implements FileSystem {

    private final InMemoryFileSystem delegate;
    private final Path currentWorkingDirectory;

    public GraalVmFileSystemAdapter(InMemoryFileSystem delegate, Path currentWorkingDirectory) {
        this.delegate = delegate;
        this.currentWorkingDirectory = currentWorkingDirectory.toAbsolutePath().normalize();
    }

    private Path toNioPath(URI uri) {
        // GraalVM often passes URIs like `file:///path/to/file` or relative ones like `module.js`
        // We need to resolve them against our script directory context if they are not absolute file URIs.
        if (uri.isAbsolute() && "file".equals(uri.getScheme())) {
            return Paths.get(uri);
        } else {
            // If it's not an absolute file URI, assume it's relative to the CWD for modules.
            // The InMemoryFileSystem is already aware of the actualScriptsDir for overrides.
            return currentWorkingDirectory.resolve(uri.getPath()).normalize();
        }
    }

    @Override
    public Path parsePath(URI uri) {
        return toNioPath(uri); // Delegate uses NIO Path
    }

    @Override
    public Path parsePath(String pathString) {
        // This method is less commonly used by GraalJS for module loading than the URI one.
        // We'll resolve it against the CWD.
        return currentWorkingDirectory.resolve(pathString).normalize();
    }

    @Override
    public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
        delegate.checkAccess(path, modes, linkOptions);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        delegate.createDirectory(dir, attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
        delegate.delete(path);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return delegate.newByteChannel(path, options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        return delegate.newDirectoryStream(dir, filter);
    }

    @Override
    public Path toAbsolutePath(Path path) {
        return delegate.toAbsolutePath(path);
    }

    @Override
    public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
        return delegate.toRealPath(path, linkOptions);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return delegate.readAttributes(path, attributes, options);
    }

    // Methods not directly available in java.nio.file.FileSystem or requiring specific handling for GraalVM
    @Override
    public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
        // Our InMemoryFileSystem is rooted at actualScriptsDir, CWD for module resolution is handled at URI parsing.
        // This GraalVM CWD might be different. For simplicity, we can make this a no-op or throw UnsupportedOperationException
        // if we strictly control module resolution paths via InMemoryFileSystem's base path.
        // For now, let's make it a no-op as our InMemoryFileSystem is already anchored.
        // System.out.println("GraalVmFileSystemAdapter: setCurrentWorkingDirectory called with: " + currentWorkingDirectory + " (ignored)");
    }

    @Override
    public Path getTempDirectory() {
        // Delegate to the default system's temp directory concept if needed, or throw.
        // For module loading, this is usually not critical.
        String tempDirPath = System.getProperty("java.io.tmpdir");
        if (tempDirPath == null) {
            try {
                throw new IOException("Temporary directory not found (java.io.tmpdir not set)");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return Paths.get(tempDirPath);
    }

    @Override
    public boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
        return delegate.isSameFile(path1, path2, options);
    }

    @Override
    public void createLink(Path link, Path existing) throws IOException {
        // Delegate or throw UnsupportedOperationException if not supported by InMemoryFileSystem
        throw new UnsupportedOperationException("createLink not supported by this file system");
    }

    @Override
    public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
        // Delegate or throw UnsupportedOperationException if not supported by InMemoryFileSystem
        throw new UnsupportedOperationException("createSymbolicLink not supported by this file system");
    }

    @Override
    public Path readSymbolicLink(Path link) throws IOException {
        // Delegate or throw UnsupportedOperationException if not supported by InMemoryFileSystem
        throw new UnsupportedOperationException("readSymbolicLink not supported by this file system");
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        delegate.copy(source, target, options);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        delegate.move(source, target, options);
    }

    @Override
    public String getSeparator() {
        return delegate.getSeparator();
    }

    @Override
    public String getMimeType(Path path) {
        // Basic implementation, can be expanded
        if (path.toString().endsWith(".js") || path.toString().endsWith(".mjs")) {
            return "application/javascript+module";
        }
        return null; // Let GraalVM or the underlying system decide
    }

    @Override
    public java.nio.charset.Charset getEncoding(Path path) { // Changed return type to Charset
        return StandardCharsets.UTF_8; // Return Charset object directly
    }
}
