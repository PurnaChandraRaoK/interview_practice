// memento holds the state of an object so you can go back to it later. 

class TextEditor {
  private String text;
  public void setText(String text) {
    this.text = text;
  }

  public String getText() {
    return text;
  }

  // Creates a memento (snapshot) of the current state
  public Memento save() {
    return new Memento(text);
  }

  // Restores the state from the given memento
  public void restore(Memento memento) {
    this.text = memento.getText();
  }
}

class Memento {
  private final String text;
  public Memento(String text) {
    this.text = text;
  }

  public String getText() {
    return text;
  }
}

// Extended caretaker with redo support
class EditorHistory {
  private Stack<Memento> undoStack = new Stack<>();
  private Stack<Memento> redoStack = new Stack<>();

  // Save new state; clear redo stack when a new state is saved
  public void saveState(Memento memento) {
    undoStack.push(memento);
    redoStack.clear();
  }

  // Undo operation: push current state to redo stack and return last state from
  // undo stack
  public Memento undo(Memento currentState) {
    if (!undoStack.isEmpty()) {
      redoStack.push(currentState);
      return undoStack.pop();
    }
    return null;
  }

  // Redo operation: push current state to undo stack and return last state from
  // redo stack
  public Memento redo(Memento currentState) {
    if (!redoStack.isEmpty()) {
      undoStack.push(currentState);
      return redoStack.pop();
    }
    return null;
  }
}

public class MementoRedoDemo {
  public static void main(String[] args) {
    TextEditor editor = new TextEditor();
    EditorHistory history = new EditorHistory();
    // Initial state
    editor.setText("Hello");
    history.saveState(editor.save());
    // First change
    editor.setText("Hello, World!");
    history.saveState(editor.save());
    // Second change
    editor.setText("Hello, World! Welcome!");
    System.out.println("Current: " + editor.getText());
    // Undo the last change
    Memento previousState = history.undo(editor.save());
    if (previousState != null) {
      editor.restore(previousState);
      System.out.println("After undo: " + editor.getText());
    }
    // Redo the undone change
    Memento redoState = history.redo(editor.save());
    if (redoState != null) {
      editor.restore(redoState);
      System.out.println("After redo: " + editor.getText());
    }
  }
}