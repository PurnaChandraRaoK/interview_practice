import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-Memory File System (Unix-style).
 * Patterns:
 *  - Composite: Node -> DirectoryNode / FileNode
 *  - Factory: NodeFactory creates nodes
 *  - Strategy: ParentCreationPolicy (STRICT vs AUTO_CREATE)
 */
public class InMemoryFileSystem {

    // ===== Public API =====

    public interface FileSystem {
        void mkdir(String path);
        void createFile(String path, String content);
        String readFile(String path);
        void writeFile(String path, String content);
        void appendToFile(String path, String content);
        List<String> ls(String path);
        void delete(String path);                 // delete file OR empty dir
        void deleteRecursive(String path);        // recursive delete
        void move(String sourcePath, String destPath); // move/rename

        // ✅ NEW: Existence checks
        boolean exists(String path);
        boolean fileExists(String path);
        boolean dirExists(String path);
    }

    public enum ParentCreationMode { STRICT, AUTO_CREATE }

    public static FileSystem createDefault() {
        return new FileSystemImpl(new StrictParentCreationPolicy(), new DefaultNodeFactory());
    }

    public static FileSystem create(ParentCreationMode mode) {
        ParentCreationPolicy policy = (mode == ParentCreationMode.AUTO_CREATE)
                ? new AutoCreateParentCreationPolicy()
                : new StrictParentCreationPolicy();
        return new FileSystemImpl(policy, new DefaultNodeFactory());
    }

    // ===== Exceptions =====

    public static class FsException extends RuntimeException {
        public FsException(String message) { super(message); }
    }

    public static class InvalidPathException extends FsException {
        public InvalidPathException(String path) { super("Invalid path: " + path); }
    }

    public static class NotFoundException extends FsException {
        public NotFoundException(String path) { super("Path not found: " + path); }
    }

    public static class AlreadyExistsException extends FsException {
        public AlreadyExistsException(String path) { super("Path already exists: " + path); }
    }

    public static class NotADirectoryException extends FsException {
        public NotADirectoryException(String path) { super("Not a directory: " + path); }
    }

    public static class IsADirectoryException extends FsException {
        public IsADirectoryException(String path) { super("Is a directory: " + path); }
    }

    public static class DirectoryNotEmptyException extends FsException {
        public DirectoryNotEmptyException(String path) { super("Directory not empty: " + path); }
    }

    public static class CannotMoveIntoSubtreeException extends FsException {
        public CannotMoveIntoSubtreeException(String src, String dst) {
            super("Cannot move '" + src + "' into its own subtree '" + dst + "'");
        }
    }

    // ===== Composite =====

    private interface Node {
        String name();
        DirectoryNode parent();
        void setParent(DirectoryNode parent);
        boolean isDirectory();
    }

    private static abstract class AbstractNode implements Node {
        private final String name;
        private DirectoryNode parent;

        protected AbstractNode(String name, DirectoryNode parent) {
            this.name = Objects.requireNonNull(name, "name");
            this.parent = parent;
        }

        @Override public String name() { return name; }
        @Override public DirectoryNode parent() { return parent; }
        @Override public void setParent(DirectoryNode parent) { this.parent = parent; }

        public String absolutePath() {
            if (parent == null) return "/"; // root
            Deque<String> stack = new ArrayDeque<>();
            Node curr = this;
            while (curr != null && curr.parent() != null) {
                stack.push(curr.name());
                curr = curr.parent();
            }
            StringBuilder sb = new StringBuilder("/");
            while (!stack.isEmpty()) {
                sb.append(stack.pop());
                if (!stack.isEmpty()) sb.append("/");
            }
            return sb.toString();
        }
    }

    private static final class FileNode extends AbstractNode {
        private final StringBuilder content = new StringBuilder();

        private FileNode(String name, DirectoryNode parent, String initialContent) {
            super(name, parent);
            if (initialContent != null) content.append(initialContent);
        }

        @Override public boolean isDirectory() { return false; }

        public String read() { return content.toString(); }

        public void overwrite(String data) {
            content.setLength(0);
            if (data != null) content.append(data);
        }

        public void append(String data) {
            if (data != null) content.append(data);
        }
    }

    private static final class DirectoryNode extends AbstractNode {
        private final Map<String, Node> children = new HashMap<>();

        private DirectoryNode(String name, DirectoryNode parent) {
            super(name, parent);
        }

        @Override public boolean isDirectory() { return true; }

        public Node getChild(String name) { return children.get(name); }
        public Collection<Node> children() { return children.values(); }
        public boolean isEmpty() { return children.isEmpty(); }

        public void addChild(Node child) {
            Objects.requireNonNull(child, "child");
            if (children.containsKey(child.name())) {
                throw new AlreadyExistsException(((AbstractNode) child).absolutePath());
            }
            children.put(child.name(), child);
            child.setParent(this);
        }

        public Node removeChild(String name) { return children.remove(name); }

        public List<String> listNamesSorted() {
            List<String> names = new ArrayList<>(children.keySet());
            Collections.sort(names);
            return names;
        }
    }

    // ===== Factory =====

    private interface NodeFactory {
        DirectoryNode newDirectory(String name, DirectoryNode parent);
        FileNode newFile(String name, DirectoryNode parent, String content);
    }

    private static final class DefaultNodeFactory implements NodeFactory {
        @Override public DirectoryNode newDirectory(String name, DirectoryNode parent) {
            return new DirectoryNode(name, parent);
        }

        @Override public FileNode newFile(String name, DirectoryNode parent, String content) {
            return new FileNode(name, parent, content);
        }
    }

    // ===== Strategy =====

    private interface ParentCreationPolicy {
        DirectoryNode ensureParentExists(DirectoryNode root, List<String> parentSegments, NodeFactory factory);
    }

    private static final class StrictParentCreationPolicy implements ParentCreationPolicy {
        @Override
        public DirectoryNode ensureParentExists(DirectoryNode root, List<String> parentSegments, NodeFactory factory) {
            DirectoryNode curr = root;
            for (String seg : parentSegments) {
                Node next = curr.getChild(seg);
                if (next == null) throw new NotFoundException(joinAbsolute(parentSegments));
                if (!next.isDirectory()) throw new NotADirectoryException(pathOf(curr, seg));
                curr = (DirectoryNode) next;
            }
            return curr;
        }
    }

    private static final class AutoCreateParentCreationPolicy implements ParentCreationPolicy {
        @Override
        public DirectoryNode ensureParentExists(DirectoryNode root, List<String> parentSegments, NodeFactory factory) {
            DirectoryNode curr = root;
            for (String seg : parentSegments) {
                Node next = curr.getChild(seg);
                if (next == null) {
                    DirectoryNode created = factory.newDirectory(seg, curr);
                    curr.addChild(created);
                    curr = created;
                } else {
                    if (!next.isDirectory()) throw new NotADirectoryException(pathOf(curr, seg));
                    curr = (DirectoryNode) next;
                }
            }
            return curr;
        }
    }

    // ===== Implementation =====

    private static final class FileSystemImpl implements FileSystem {
        private final DirectoryNode root;
        private final ParentCreationPolicy parentPolicy;
        private final NodeFactory factory;
        private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);

        private FileSystemImpl(ParentCreationPolicy parentPolicy, NodeFactory factory) {
            this.root = new DirectoryNode("", null);
            this.parentPolicy = Objects.requireNonNull(parentPolicy, "parentPolicy");
            this.factory = Objects.requireNonNull(factory, "factory");
        }

        // ✅ NEW: Existence checks (no exceptions for "not found")
        @Override
        public boolean exists(String path) {
            rwLock.readLock().lock();
            try {
                return tryResolve(path) != null;
            } finally {
                rwLock.readLock().unlock();
            }
        }

        @Override
        public boolean fileExists(String path) {
            rwLock.readLock().lock();
            try {
                Node n = tryResolve(path);
                return n != null && !n.isDirectory();
            } finally {
                rwLock.readLock().unlock();
            }
        }

        @Override
        public boolean dirExists(String path) {
            rwLock.readLock().lock();
            try {
                Node n = tryResolve(path);
                return n != null && n.isDirectory();
            } finally {
                rwLock.readLock().unlock();
            }
        }

        @Override
        public void mkdir(String path) {
            rwLock.writeLock().lock();
            try {
                PathParts parts = PathUtil.parse(path);
                if (parts.isRoot()) return;

                DirectoryNode parent = parentPolicy.ensureParentExists(root, parts.parentSegments(), factory);
                String name = parts.leafName();

                Node existing = parent.getChild(name);
                if (existing != null) {
                    if (existing.isDirectory()) return;
                    throw new AlreadyExistsException(path);
                }

                parent.addChild(factory.newDirectory(name, parent));
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        @Override
        public void createFile(String path, String content) {
            rwLock.writeLock().lock();
            try {
                PathParts parts = PathUtil.parse(path);
                if (parts.isRoot()) throw new InvalidPathException(path);

                DirectoryNode parent = parentPolicy.ensureParentExists(root, parts.parentSegments(), factory);
                String name = parts.leafName();

                Node existing = parent.getChild(name);
                if (existing != null) throw new AlreadyExistsException(path);

                parent.addChild(factory.newFile(name, parent, content));
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        @Override
        public String readFile(String path) {
            rwLock.readLock().lock();
            try {
                Node node = resolve(path);
                if (node.isDirectory()) throw new IsADirectoryException(path);
                return ((FileNode) node).read();
            } finally {
                rwLock.readLock().unlock();
            }
        }

        @Override
        public void writeFile(String path, String content) {
            rwLock.writeLock().lock();
            try {
                Node node = resolve(path);
                if (node.isDirectory()) throw new IsADirectoryException(path);
                ((FileNode) node).overwrite(content);
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        @Override
        public void appendToFile(String path, String content) {
            rwLock.writeLock().lock();
            try {
                Node node = resolve(path);
                if (node.isDirectory()) throw new IsADirectoryException(path);
                ((FileNode) node).append(content);
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        @Override
        public List<String> ls(String path) {
            rwLock.readLock().lock();
            try {
                Node node = resolve(path);
                if (!node.isDirectory()) return Collections.singletonList(node.name());
                return ((DirectoryNode) node).listNamesSorted();
            } finally {
                rwLock.readLock().unlock();
            }
        }

        @Override
        public void delete(String path) {
            rwLock.writeLock().lock();
            try {
                if ("/".equals(PathUtil.normalize(path))) throw new InvalidPathException(path);

                PathParts parts = PathUtil.parse(path);
                DirectoryNode parent = (DirectoryNode) resolve(joinAbsolute(parts.parentSegments()));
                Node target = parent.getChild(parts.leafName());
                if (target == null) throw new NotFoundException(path);

                if (target.isDirectory() && !((DirectoryNode) target).isEmpty()) {
                    throw new DirectoryNotEmptyException(path);
                }
                parent.removeChild(parts.leafName());
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        @Override
        public void deleteRecursive(String path) {
            rwLock.writeLock().lock();
            try {
                String norm = PathUtil.normalize(path);
                if ("/".equals(norm)) throw new InvalidPathException(path);

                PathParts parts = PathUtil.parse(norm);
                DirectoryNode parent = (DirectoryNode) resolve(joinAbsolute(parts.parentSegments()));
                Node target = parent.getChild(parts.leafName());
                if (target == null) throw new NotFoundException(path);

                parent.removeChild(parts.leafName());
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        @Override
        public void move(String sourcePath, String destPath) {
            rwLock.writeLock().lock();
            try {
                String src = PathUtil.normalize(sourcePath);
                String dst = PathUtil.normalize(destPath);

                if ("/".equals(src) || "/".equals(dst)) throw new InvalidPathException("src/dst");
                Node srcNode = resolve(src);

                PathParts srcParts = PathUtil.parse(src);
                DirectoryNode srcParent = (DirectoryNode) resolve(joinAbsolute(srcParts.parentSegments()));

                PathParts dstParts = PathUtil.parse(dst);
                DirectoryNode dstParent = parentPolicy.ensureParentExists(root, dstParts.parentSegments(), factory);
                String dstName = dstParts.leafName();

                if (srcNode.isDirectory()) {
                    Node check = dstParent;
                    while (check != null && check.parent() != null) {
                        if (check == srcNode) throw new CannotMoveIntoSubtreeException(src, dst);
                        check = check.parent();
                    }
                }

                if (dstParent.getChild(dstName) != null) throw new AlreadyExistsException(dst);

                srcParent.removeChild(srcNode.name());
                Node renamed = renameNode(srcNode, dstName, dstParent);
                dstParent.addChild(renamed);

            } finally {
                rwLock.writeLock().unlock();
            }
        }

        // ---- Internal helpers ----

        /**
         * Strict resolver: throws if invalid/missing.
         */
        private Node resolve(String path) {
            Node node = tryResolve(path);
            if (node == null) throw new NotFoundException(PathUtil.normalize(path));
            return node;
        }

        /**
         * Safe resolver: returns null when not found.
         * Still throws for invalid path format (because "exists" on invalid path should not silently pass).
         */
        private Node tryResolve(String path) {
            String norm = PathUtil.normalize(path);
            if ("/".equals(norm)) return root;

            PathParts parts = PathUtil.parse(norm);
            DirectoryNode curr = root;

            for (int i = 0; i < parts.segments().size(); i++) {
                String seg = parts.segments().get(i);
                Node next = curr.getChild(seg);
                if (next == null) return null;

                boolean isLeaf = (i == parts.segments().size() - 1);
                if (isLeaf) return next;

                if (!next.isDirectory()) throw new NotADirectoryException(pathOf(curr, seg));
                curr = (DirectoryNode) next;
            }
            return curr;
        }

        private Node renameNode(Node node, String newName, DirectoryNode newParent) {
            if (node.isDirectory()) {
                DirectoryNode oldDir = (DirectoryNode) node;
                DirectoryNode newDir = factory.newDirectory(newName, newParent);
                for (Node child : oldDir.children()) newDir.addChild(child);
                return newDir;
            } else {
                FileNode oldFile = (FileNode) node;
                return factory.newFile(newName, newParent, oldFile.read());
            }
        }
    }

    // ===== Path utilities =====

    private static final class PathUtil {
        static String normalize(String path) {
            if (path == null) throw new InvalidPathException("null");
            String p = path.trim();
            if (p.isEmpty()) throw new InvalidPathException(path);
            if (!p.startsWith("/")) throw new InvalidPathException(path);
            if (p.length() > 1 && p.endsWith("/")) throw new InvalidPathException(path);
            if (p.contains("//")) throw new InvalidPathException(path);
            return p;
        }

        static PathParts parse(String path) {
            String norm = normalize(path);
            if ("/".equals(norm)) return PathParts.root();

            String[] split = norm.substring(1).split("/");
            List<String> segs = new ArrayList<>(split.length);
            for (String s : split) {
                if (s == null || s.isEmpty()) throw new InvalidPathException(path);
                segs.add(s);
            }
            return new PathParts(segs);
        }
    }

    private static final class PathParts {
        private final List<String> segments;

        private PathParts(List<String> segments) { this.segments = segments; }

        static PathParts root() { return new PathParts(Collections.emptyList()); }

        boolean isRoot() { return segments.isEmpty(); }

        List<String> segments() { return segments; }

        String leafName() {
            if (segments.isEmpty()) return "";
            return segments.get(segments.size() - 1);
        }

        List<String> parentSegments() {
            if (segments.size() <= 1) return Collections.emptyList();
            return segments.subList(0, segments.size() - 1);
        }
    }

    private static String joinAbsolute(List<String> segments) {
        if (segments == null || segments.isEmpty()) return "/";
        StringBuilder sb = new StringBuilder("/");
        for (int i = 0; i < segments.size(); i++) {
            sb.append(segments.get(i));
            if (i < segments.size() - 1) sb.append("/");
        }
        return sb.toString();
    }

    private static String pathOf(DirectoryNode parent, String childName) {
        String base = ((AbstractNode) parent).absolutePath();
        if ("/".equals(base)) return "/" + childName;
        return base + "/" + childName;
    }

    // ===== Example usage =====
    public static void main(String[] args) {
        FileSystem fs = InMemoryFileSystem.create(ParentCreationMode.STRICT);

        System.out.println(fs.exists("/"));        // true
        System.out.println(fs.dirExists("/a"));    // false

        fs.mkdir("/a");
        fs.createFile("/a/x.txt", "hi");

        System.out.println(fs.exists("/a"));       // true
        System.out.println(fs.dirExists("/a"));    // true
        System.out.println(fs.fileExists("/a"));   // false
        System.out.println(fs.fileExists("/a/x.txt")); // true
    }
}
