package ca.atlasengine.scripting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class InMemoryFileSystem extends FileSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryFileSystem.class);
    private final Path actualScriptsDir;
    private final Map<Path, String> virtualFileContents;
    private final FileSystem defaultFileSystem = FileSystems.getDefault();
    private final FileSystemProvider defaultProvider = defaultFileSystem.provider();

    public InMemoryFileSystem(Path actualScriptsDir, Map<String, String> moduleContentOverrides) {
        this.actualScriptsDir = actualScriptsDir.toAbsolutePath().normalize();
        this.virtualFileContents = new HashMap<>();
        moduleContentOverrides.forEach((relativePathStr, content) -> {
            Path relativePath = Paths.get(relativePathStr).normalize();
            if (relativePath.isAbsolute()) {
                LOGGER.error("InMemoryFS: Module override path should be relative: {}", relativePathStr);
                if (relativePath.startsWith(this.actualScriptsDir)) {
                    relativePath = this.actualScriptsDir.relativize(relativePath);
                } else {
                    LOGGER.error("InMemoryFS: Cannot make {} relative. Override skipped.", relativePathStr);
                    return;
                }
            }
            this.virtualFileContents.put(relativePath, content);
            LOGGER.info("InMemoryFS: Registered override for module: {}", relativePath);
        });
    }

    private Path getOverrideKey(Path requestedPath) {
        try {
            Path absoluteRequestedPath = requestedPath.isAbsolute() ? requestedPath.normalize() : this.actualScriptsDir.resolve(requestedPath).normalize();
            if (absoluteRequestedPath.startsWith(this.actualScriptsDir)) {
                Path relativePath = this.actualScriptsDir.relativize(absoluteRequestedPath);
                return virtualFileContents.containsKey(relativePath) ? relativePath : null;
            }
        } catch (Exception e) {
            // Ignore, means it's not an overridable path
        }
        return null;
    }

    @Override
    public FileSystemProvider provider() {
        return defaultProvider;
    }

    @Override
    public void close() {
        // Do not close the defaultFileSystem
    }

    @Override
    public boolean isOpen() {
        return defaultFileSystem.isOpen();
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String getSeparator() {
        return defaultFileSystem.getSeparator();
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return defaultFileSystem.getRootDirectories();
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return defaultFileSystem.getFileStores();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return defaultFileSystem.supportedFileAttributeViews();
    }

    @Override
    public Path getPath(String first, String... more) {
        return defaultFileSystem.getPath(first, more);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        return defaultFileSystem.getPathMatcher(syntaxAndPattern);
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        return defaultFileSystem.getUserPrincipalLookupService();
    }

    @Override
    public WatchService newWatchService() throws IOException {
        return defaultFileSystem.newWatchService();
    }

    public void checkAccess(Path path, Set<? extends AccessMode> modes) throws IOException {
        Path overrideKey = getOverrideKey(path);
        if (overrideKey != null) {
            if (modes.contains(AccessMode.WRITE) || modes.contains(AccessMode.EXECUTE)) {
                throw new AccessDeniedException("Overridden module " + path + " is read-only.");
            }
            return;
        }
        AccessMode[] modeArray = modes.toArray(new AccessMode[0]);
        defaultProvider.checkAccess(path, modeArray);
    }

    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        Path overrideKey = getOverrideKey(path);
        if (overrideKey != null) {
            for (OpenOption option : options) {
                if (option == StandardOpenOption.WRITE || option == StandardOpenOption.APPEND ||
                    option == StandardOpenOption.CREATE || option == StandardOpenOption.CREATE_NEW ||
                    option == StandardOpenOption.DELETE_ON_CLOSE) {
                    throw new UnsupportedOperationException("Overridden module is read-only: " + path);
                }
            }
            String content = virtualFileContents.get(overrideKey);
            System.out.println("InMemoryFS: Serving overridden content for: " + path + " (key: " + overrideKey + ")");
            return new InMemoryFileSystem.SeekableInMemoryByteChannel(content.getBytes(StandardCharsets.UTF_8));
        }
        return defaultProvider.newByteChannel(path, options, attrs);
    }

    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        Path overrideKey = getOverrideKey(path);
        if (overrideKey != null) {
            Map<String, Object> attrsMap = new HashMap<>();
            FileTime now = FileTime.fromMillis(System.currentTimeMillis());
            String content = virtualFileContents.get(overrideKey);
            long size = content.getBytes(StandardCharsets.UTF_8).length;

            // Basic attributes. For "posix:*", "dos:*", etc., more specific handling would be needed.
            switch (attributes) {
                case "basic:isDirectory", "isDirectory" -> attrsMap.put("isDirectory", false);
                case "basic:isRegularFile", "isRegularFile" -> attrsMap.put("isRegularFile", true);
                case "basic:isSymbolicLink", "isSymbolicLink" -> attrsMap.put("isSymbolicLink", false);
                case "basic:isOther", "isOther" -> attrsMap.put("isOther", false);
                case "basic:size", "size" -> attrsMap.put("size", size);
                case "basic:lastModifiedTime", "lastModifiedTime" -> attrsMap.put("lastModifiedTime", now);
                case "basic:creationTime", "creationTime" -> attrsMap.put("creationTime", now);
                case "basic:lastAccessTime", "lastAccessTime" -> attrsMap.put("lastAccessTime", now);
                case "basic:fileKey", "fileKey" -> attrsMap.put("fileKey", null);
                default -> {
                    if (attributes.contains("isDirectory")) attrsMap.put("isDirectory", false);
                    if (attributes.contains("isRegularFile")) attrsMap.put("isRegularFile", true);
                    if (attributes.contains("isSymbolicLink")) attrsMap.put("isSymbolicLink", false);
                    if (attributes.contains("isOther")) attrsMap.put("isOther", false);
                    if (attributes.contains("size")) attrsMap.put("size", size);
                    if (attributes.contains("lastModifiedTime")) attrsMap.put("lastModifiedTime", now);
                    if (attributes.contains("creationTime")) attrsMap.put("creationTime", now);
                    if (attributes.contains("lastAccessTime")) attrsMap.put("lastAccessTime", now);
                    if (attributes.contains("fileKey")) attrsMap.put("fileKey", null);
                }
            }
            return attrsMap;
        }
        return defaultProvider.readAttributes(path, attributes, options);
    }

    // This toAbsolutePath is specific to how InMemoryFileSystem wants to present paths.
    // It's not an override from java.nio.file.FileSystem.
    public Path toAbsolutePath(Path path) {
        Path overrideKey = getOverrideKey(path);
        if (overrideKey != null) {
            return this.actualScriptsDir.resolve(overrideKey).normalize();
        }
        // If not overridden, the path might already be absolute or needs context.
        // The defaultProvider.toAbsolutePath might be more robust if path isn't already absolute.
        if (path.isAbsolute()) return path.normalize();
        // Corrected call: toAbsolutePath is a method of the Path object, not FileSystemProvider
        return path.toAbsolutePath().normalize();
    }

    public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
        Path overrideKey = getOverrideKey(path);
        if (overrideKey != null) {
            // For overridden files, their "real path" is their virtual absolute path.
            return this.actualScriptsDir.resolve(overrideKey).normalize();
        }
        return path.toRealPath(linkOptions);
    }

    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        defaultProvider.createDirectory(dir, attrs);
    }

    public void delete(Path path) throws IOException {
        defaultProvider.delete(path);
    }

    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        return defaultProvider.newDirectoryStream(dir, filter);
    }

    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        defaultProvider.copy(source, target, options);
    }

    public void move(Path source, Path target, CopyOption... options) throws IOException {
        defaultProvider.move(source, target, options);
    }

    public boolean isSameFile(Path path, Path path2, LinkOption... options) throws IOException {
        // LinkOptions are not used by provider's isSameFile.
        return defaultProvider.isSameFile(path, path2);
    }

    private static class SeekableInMemoryByteChannel implements SeekableByteChannel {
        private byte[] content;
        private int position = 0;
        private boolean open = true;

        public SeekableInMemoryByteChannel(byte[] data) {
            this.content = data;
        }
        @Override public int read(java.nio.ByteBuffer dst) throws IOException {
            if (!open) throw new java.nio.channels.ClosedChannelException();
            if (position >= content.length) return -1;
            int bytesToRead = Math.min(dst.remaining(), content.length - position);
            dst.put(content, position, bytesToRead);
            position += bytesToRead;
            return bytesToRead;
        }
        @Override public int write(java.nio.ByteBuffer src) { throw new NonWritableChannelException(); }
        @Override public long position() throws IOException { if (!open) throw new java.nio.channels.ClosedChannelException(); return position; }
        @Override public SeekableByteChannel position(long newPosition) throws IOException {
            if (!open) throw new java.nio.channels.ClosedChannelException();
            if (newPosition < 0 || newPosition > content.length) throw new IllegalArgumentException("Invalid position");
            this.position = (int) newPosition; return this;
        }
        @Override public long size() throws IOException { if (!open) throw new java.nio.channels.ClosedChannelException(); return content.length; }
        @Override public SeekableByteChannel truncate(long size) { throw new NonWritableChannelException(); }
        @Override public boolean isOpen() { return open; }
        @Override public void close() { open = false; }
    }
}
