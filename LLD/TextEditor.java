import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Simple Text Editor (Core logic) using Memento + Command for Undo/Redo.
 * - Document is the Originator (creates/restores snapshots)
 * - Snapshot (Memento) is immutable
 * - EditCommand is the Command; stores before/after snapshots
 * - History manages undo/redo stacks
 *
 * Includes a small CLI demo in main().
 */
public class TextEditorApp {

    /* ===================== DEMO CLI ===================== */

    public static void main(String[] args) throws Exception {
        TextEditor editor = new TextEditor(new FileDocumentRepository());

        System.out.println("Simple TextEditor CLI");
        System.out.println("Commands:");
        System.out.println("  new <name>");
        System.out.println("  open <path>");
        System.out.println("  save [path]");
        System.out.println("  close");
        System.out.println("  show");
        System.out.println("  cursor <pos>");
        System.out.println("  select <start> <end>");
        System.out.println("  insert <text...>");
        System.out.println("  copy | cut | paste");
        System.out.println("  undo | redo");
        System.out.println("  search <term>");
        System.out.println("  replace <old> <new> [all]");
        System.out.println("  help | exit");
        System.out.println();

        java.util.Scanner sc = new java.util.Scanner(System.in, StandardCharsets.UTF_8);
        while (true) {
            System.out.print("> ");
            if (!sc.hasNextLine()) break;
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            String cmd = firstToken(line).toLowerCase();
            String rest = restAfterFirstToken(line);

            try {
                switch (cmd) {
                    case "new" -> {
                        if (rest.isBlank()) throw new IllegalArgumentException("Usage: new <name>");
                        editor.newDocument(rest.trim());
                        System.out.println("Created doc: " + editor.status());
                    }
                    case "open" -> {
                        if (rest.isBlank()) throw new IllegalArgumentException("Usage: open <path>");
                        editor.open(Path.of(rest.trim()));
                        System.out.println("Opened: " + editor.status());
                    }
                    case "save" -> {
                        if (rest.isBlank()) editor.save(null);
                        else editor.save(Path.of(rest.trim()));
                        System.out.println("Saved: " + editor.status());
                    }
                    case "close" -> {
                        editor.close();
                        System.out.println("Closed.");
                    }
                    case "show" -> {
                        ensureOpen(editor);
                        System.out.println(editor.getText());
                        System.out.println("-- " + editor.status());
                    }
                    case "cursor" -> {
                        ensureOpen(editor);
                        int pos = Integer.parseInt(rest.trim());
                        editor.setCursor(pos);
                        System.out.println(editor.status());
                    }
                    case "select" -> {
                        ensureOpen(editor);
                        String[] parts = rest.trim().split("\\s+");
                        if (parts.length != 2) throw new IllegalArgumentException("Usage: select <start> <end>");
                        editor.select(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                        System.out.println(editor.status());
                    }
                    case "insert" -> {
                        ensureOpen(editor);
                        if (rest.isBlank()) throw new IllegalArgumentException("Usage: insert <text...>");
                        editor.insert(rest); // inserts raw rest-of-line
                        System.out.println(editor.status());
                    }
                    case "copy" -> {
                        ensureOpen(editor);
                        editor.copy();
                        System.out.println("Copied selection to clipboard.");
                    }
                    case "cut" -> {
                        ensureOpen(editor);
                        editor.cut();
                        System.out.println("Cut selection to clipboard.");
                        System.out.println(editor.status());
                    }
                    case "paste" -> {
                        ensureOpen(editor);
                        editor.paste();
                        System.out.println("Pasted.");
                        System.out.println(editor.status());
                    }
                    case "undo" -> {
                        ensureOpen(editor);
                        editor.undo();
                        System.out.println("Undone.");
                        System.out.println(editor.status());
                    }
                    case "redo" -> {
                        ensureOpen(editor);
                        editor.redo();
                        System.out.println("Redone.");
                        System.out.println(editor.status());
                    }
                    case "search" -> {
                        ensureOpen(editor);
                        if (rest.isBlank()) throw new IllegalArgumentException("Usage: search <term>");
                        List<Integer> hits = editor.search(rest.trim(), true);
                        System.out.println("Hits: " + hits);
                    }
                    case "replace" -> {
                        ensureOpen(editor);
                        String[] parts = rest.trim().split("\\s+");
                        if (parts.length < 2) throw new IllegalArgumentException("Usage: replace <old> <new> [all]");
                        String oldText = parts[0];
                        String newText = parts[1];
                        boolean all = parts.length >= 3 && parts[2].equalsIgnoreCase("all");
                        editor.replace(oldText, newText, all);
                        System.out.println("Replaced.");
                        System.out.println(editor.status());
                    }
                    case "help" -> System.out.println("Type: new/open/save/close/show/cursor/select/insert/copy/cut/paste/undo/redo/search/replace/exit");
                    case "exit" -> {
                        System.out.println("Bye.");
                        return;
                    }
                    default -> System.out.println("Unknown command. Type 'help'.");
                }
            } catch (Exception ex) {
                System.out.println("Error: " + ex.getMessage());
            }
        }
    }

    private static void ensureOpen(TextEditor editor) {
        if (!editor.isDocumentOpen()) throw new IllegalStateException("No document open. Use: new/open");
    }

    private static String firstToken(String line) {
        int idx = line.indexOf(' ');
        return idx < 0 ? line : line.substring(0, idx);
    }

    private static String restAfterFirstToken(String line) {
        int idx = line.indexOf(' ');
        return idx < 0 ? "" : line.substring(idx + 1);
    }

    /* ===================== FACADE ===================== */

    public static final class TextEditor {
        private final DocumentRepository repository;
        private final History history = new History();

        private Document document;      // current doc
        private String clipboard = "";  // simple clipboard

        public TextEditor(DocumentRepository repository) {
            this.repository = Objects.requireNonNull(repository);
        }

        public boolean isDocumentOpen() {
            return document != null;
        }

        public void newDocument(String name) {
            this.document = new Document(name, null, "");
            this.clipboard = "";
            this.history.clear();
        }

        public void open(Path path) throws IOException {
            String text = repository.load(path);
            this.document = new Document(path.getFileName().toString(), path, text);
            this.clipboard = "";
            this.history.clear();
        }

        public void save(Path path) throws IOException {
            ensureOpen();
            Path target = (path != null) ? path : document.path();
            if (target == null) throw new IllegalArgumentException("No path provided and document has no associated path.");
            repository.save(target, document.getText());
            document.setPath(target);
        }

        public void close() {
            this.document = null;
            this.clipboard = "";
            this.history.clear();
        }

        public String getText() {
            ensureOpen();
            return document.getText();
        }

        public void setCursor(int pos) {
            ensureOpen();
            document.setCursor(pos);
        }

        public void select(int start, int end) {
            ensureOpen();
            document.select(start, end);
        }

        public void insert(String text) {
            ensureOpen();
            execute(new InsertCommand(text));
        }

        public void copy() {
            ensureOpen();
            clipboard = document.getSelectedText();
        }

        public void cut() {
            ensureOpen();
            // update clipboard (not part of undo/redo to keep it simple)
            clipboard = document.getSelectedText();
            execute(new CutSelectionCommand());
        }

        public void paste() {
            ensureOpen();
            if (clipboard == null || clipboard.isEmpty()) return;
            execute(new PasteCommand(clipboard));
        }

        public void undo() {
            ensureOpen();
            history.undo(document);
        }

        public void redo() {
            ensureOpen();
            history.redo(document);
        }

        public List<Integer> search(String term, boolean caseSensitive) {
            ensureOpen();
            return document.findAll(term, caseSensitive);
        }

        public void replace(String oldText, String newText, boolean all) {
            ensureOpen();
            execute(new ReplaceCommand(oldText, newText, all));
        }

        public String status() {
            if (document == null) return "NO_DOCUMENT";
            return "Doc=" + document.name()
                    + (document.path() != null ? " (" + document.path() + ")" : "")
                    + " | len=" + document.length()
                    + " | cursor=" + document.cursor()
                    + " | sel=[" + document.selStart() + "," + document.selEnd() + "]"
                    + " | undo=" + history.undoSize()
                    + " | redo=" + history.redoSize();
        }

        private void execute(EditCommand cmd) {
            // capture snapshots before/after for robust correctness
            Document.Snapshot before = document.createSnapshot();
            cmd.apply(document);
            Document.Snapshot after = document.createSnapshot();

            if (!before.equals(after)) { // no-op edits won't pollute history
                history.push(new HistoryEntry(before, after));
            } else {
                history.clearRedoOnly(); // still clear redo on user action
            }
        }

        private void ensureOpen() {
            if (document == null) throw new IllegalStateException("No document open.");
        }
    }

    /* ===================== DOMAIN (Originator) ===================== */

    public static final class Document {
        private String name;
        private Path path; // optional

        private StringBuilder text;
        private int cursor;       // 0..len
        private int selStart;     // 0..len
        private int selEnd;       // 0..len, selStart <= selEnd

        public Document(String name, Path path, String initialText) {
            this.name = Objects.requireNonNullElse(name, "Untitled");
            this.path = path;
            this.text = new StringBuilder(Objects.requireNonNullElse(initialText, ""));
            this.cursor = 0;
            this.selStart = 0;
            this.selEnd = 0;
        }

        public String name() { return name; }
        public Path path() { return path; }
        public void setPath(Path p) { this.path = p; }

        public int length() { return text.length(); }
        public int cursor() { return cursor; }
        public int selStart() { return selStart; }
        public int selEnd() { return selEnd; }

        public String getText() { return text.toString(); }

        public void setCursor(int pos) {
            int p = clamp(pos, 0, length());
            cursor = p;
            clearSelection();
        }

        public void select(int start, int end) {
            int s = clamp(Math.min(start, end), 0, length());
            int e = clamp(Math.max(start, end), 0, length());
            selStart = s;
            selEnd = e;
            cursor = e; // common editor behavior
        }

        public String getSelectedText() {
            if (!hasSelection()) return "";
            return text.substring(selStart, selEnd);
        }

        public void clearSelection() {
            selStart = selEnd = cursor;
        }

        public boolean hasSelection() {
            return selStart != selEnd;
        }

        public void insertAtCursor(String s) {
            Objects.requireNonNull(s);
            if (hasSelection()) {
                deleteRange(selStart, selEnd);
                cursor = selStart;
                clearSelection();
            }
            text.insert(cursor, s);
            cursor += s.length();
            clearSelection();
        }

        public void deleteSelection() {
            if (!hasSelection()) return;
            int start = selStart;
            deleteRange(selStart, selEnd);
            cursor = start;
            clearSelection();
        }

        public void replaceFirst(String oldText, String newText, boolean caseSensitive) {
            Objects.requireNonNull(oldText);
            Objects.requireNonNull(newText);
            if (oldText.isEmpty()) return;

            String src = getText();
            int idx = indexOf(src, oldText, 0, caseSensitive);
            if (idx < 0) return;

            int end = idx + oldText.length();
            replaceRange(idx, end, newText);
            cursor = idx + newText.length();
            clearSelection();
        }

        public void replaceAll(String oldText, String newText, boolean caseSensitive) {
            Objects.requireNonNull(oldText);
            Objects.requireNonNull(newText);
            if (oldText.isEmpty()) return;

            String src = getText();
            String result;
            if (caseSensitive) {
                result = src.replace(oldText, newText);
            } else {
                // simple case-insensitive replaceAll
                String lower = src.toLowerCase();
                String target = oldText.toLowerCase();
                StringBuilder out = new StringBuilder(src.length());
                int i = 0;
                while (true) {
                    int hit = lower.indexOf(target, i);
                    if (hit < 0) {
                        out.append(src, i, src.length());
                        break;
                    }
                    out.append(src, i, hit).append(newText);
                    i = hit + oldText.length();
                }
                result = out.toString();
            }

            text = new StringBuilder(result);
            cursor = clamp(cursor, 0, length());
            clearSelection();
        }

        public List<Integer> findAll(String term, boolean caseSensitive) {
            Objects.requireNonNull(term);
            List<Integer> hits = new ArrayList<>();
            if (term.isEmpty()) return hits;

            String src = getText();
            int from = 0;
            while (true) {
                int idx = indexOf(src, term, from, caseSensitive);
                if (idx < 0) break;
                hits.add(idx);
                from = idx + term.length();
            }
            return hits;
        }

        /* ===== MEMENTO (Snapshot) ===== */

        public Snapshot createSnapshot() {
            return new Snapshot(getText(), cursor, selStart, selEnd);
        }

        public void restore(Snapshot snapshot) {
            Objects.requireNonNull(snapshot);
            this.text = new StringBuilder(snapshot.text());
            this.cursor = clamp(snapshot.cursor(), 0, length());
            int s = clamp(snapshot.selStart(), 0, length());
            int e = clamp(snapshot.selEnd(), 0, length());
            this.selStart = Math.min(s, e);
            this.selEnd = Math.max(s, e);
            this.cursor = clamp(this.cursor, 0, length());
        }

        public static final class Snapshot {
            private final String text;
            private final int cursor;
            private final int selStart;
            private final int selEnd;

            public Snapshot(String text, int cursor, int selStart, int selEnd) {
                this.text = Objects.requireNonNullElse(text, "");
                this.cursor = cursor;
                this.selStart = selStart;
                this.selEnd = selEnd;
            }

            public String text() { return text; }
            public int cursor() { return cursor; }
            public int selStart() { return selStart; }
            public int selEnd() { return selEnd; }

            @Override public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof Snapshot other)) return false;
                return cursor == other.cursor
                        && selStart == other.selStart
                        && selEnd == other.selEnd
                        && text.equals(other.text);
            }

            @Override public int hashCode() {
                int h = text.hashCode();
                h = 31 * h + cursor;
                h = 31 * h + selStart;
                h = 31 * h + selEnd;
                return h;
            }
        }

        /* ===== Internals ===== */

        private void deleteRange(int start, int end) {
            int s = clamp(Math.min(start, end), 0, length());
            int e = clamp(Math.max(start, end), 0, length());
            if (s == e) return;
            text.delete(s, e);
        }

        private void replaceRange(int start, int end, String replacement) {
            int s = clamp(Math.min(start, end), 0, length());
            int e = clamp(Math.max(start, end), 0, length());
            text.replace(s, e, replacement);
        }

        private static int clamp(int v, int lo, int hi) {
            return Math.max(lo, Math.min(hi, v));
        }

        private static int indexOf(String src, String term, int from, boolean caseSensitive) {
            if (caseSensitive) return src.indexOf(term, from);
            return src.toLowerCase().indexOf(term.toLowerCase(), from);
        }
    }

    /* ===================== COMMANDS (Edits) ===================== */

    // Keep commands tiny: only "how to edit". Undo/redo is handled by HistoryEntry snapshots.
    public interface EditCommand {
        void apply(Document doc);
    }

    public static final class InsertCommand implements EditCommand {
        private final String text;
        public InsertCommand(String text) { this.text = Objects.requireNonNull(text); }
        @Override public void apply(Document doc) { doc.insertAtCursor(text); }
    }

    public static final class CutSelectionCommand implements EditCommand {
        @Override public void apply(Document doc) { doc.deleteSelection(); }
    }

    public static final class PasteCommand implements EditCommand {
        private final String clipboard;
        public PasteCommand(String clipboard) { this.clipboard = Objects.requireNonNullElse(clipboard, ""); }
        @Override public void apply(Document doc) { doc.insertAtCursor(clipboard); }
    }

    public static final class ReplaceCommand implements EditCommand {
        private final String oldText;
        private final String newText;
        private final boolean all;

        public ReplaceCommand(String oldText, String newText, boolean all) {
            this.oldText = Objects.requireNonNull(oldText);
            this.newText = Objects.requireNonNull(newText);
            this.all = all;
        }

        @Override public void apply(Document doc) {
            // Case-sensitive replace to keep it simple; can extend later
            if (all) doc.replaceAll(oldText, newText, true);
            else doc.replaceFirst(oldText, newText, true);
        }
    }

    /* ===================== UNDO/REDO HISTORY (Caretaker-ish) ===================== */

    public static final class History {
        private final Deque<HistoryEntry> undo = new ArrayDeque<>();
        private final Deque<HistoryEntry> redo = new ArrayDeque<>();

        public void push(HistoryEntry entry) {
            undo.push(entry);
            redo.clear();
        }

        public void undo(Document doc) {
            if (undo.isEmpty()) return;
            HistoryEntry e = undo.pop();
            doc.restore(e.before());
            redo.push(e);
        }

        public void redo(Document doc) {
            if (redo.isEmpty()) return;
            HistoryEntry e = redo.pop();
            doc.restore(e.after());
            undo.push(e);
        }

        public void clear() {
            undo.clear();
            redo.clear();
        }

        public void clearRedoOnly() {
            redo.clear();
        }

        public int undoSize() { return undo.size(); }
        public int redoSize() { return redo.size(); }
    }

    public static final class HistoryEntry {
        private final Document.Snapshot before;
        private final Document.Snapshot after;

        public HistoryEntry(Document.Snapshot before, Document.Snapshot after) {
            this.before = Objects.requireNonNull(before);
            this.after = Objects.requireNonNull(after);
        }

        public Document.Snapshot before() { return before; }
        public Document.Snapshot after() { return after; }
    }

    /* ===================== REPOSITORY (IO) ===================== */

    public interface DocumentRepository {
        String load(Path path) throws IOException;
        void save(Path path, String content) throws IOException;
    }

    public static final class FileDocumentRepository implements DocumentRepository {
        @Override
        public String load(Path path) throws IOException {
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
        }

        @Override
        public void save(Path path, String content) throws IOException {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, content, StandardCharsets.UTF_8);
        }
    }
}
