// Problem 
public class Character {
  private String name;
  private int health;
  private int attackPower;
  private int level;
  public Character(String name, int health, int attackPower, int level) {
    this.name = name;
    this.health = health;
    this.attackPower = attackPower;
    this.level = level;
  }
  public void showCharacterInfo() {
    System.out.println("Character [Name=" + name + ", Health=" + health
        + ", AttackPower=" + attackPower + ", Level=" + level + "]");
  }
}

public class CharacterFactory {
  // Creating a new character each time with similar attributes
  public Character createCharacterWithNewName(String name) {
    // Creating a new character with the same attributes, just changing the name
    return new Character(name, 100, 50, 1); // Default attributes for simplicity
  }

  public Character createCharacterWithNewLevel(int level) {
    // Creating a new character with the same attributes, just changing the
    // level
    return new Character(
        "DefaultName", 100, 50, level); // Default name and attributes
  }

  public Character createCharacterWithNewAttackPower(int attackPower) {
    // Creating a new character with the same attributes, just changing the
    // attack power
    return new Character(
        "DefaultName", 100, attackPower, 1); // Default name and level
  }
}

// Solution

public class Character implements Cloneable {
  private String name;
  private int health;
  private int attackPower;
  private int level;

  public Character(String name, int health, int attackPower, int level) {
    this.name = name;
    this.health = health;
    this.attackPower = attackPower;
    this.level = level;
  }

  @Override
  public Character clone() throws CloneNotSupportedException {
    return (Character) super.clone(); // Shallow copy of the character object
  }

  public void showCharacterInfo() {
    System.out.println("Character [Name=" + name + ", Health=" + health
        + ", AttackPower=" + attackPower + ", Level=" + level + "]");
  }

  @Override
  public Character clone() throws CloneNotSupportedException {
      return (Character) super.clone();  // Shallow copy of the character object
  }
}

public class CharacterFactory {
  private Character prototypeCharacter;
  // Constructor to create a prototype character (default character)
  public CharacterFactory() {
    prototypeCharacter =
        new Character("DefaultName", 100, 50, 1); // Default prototype character
  }

  // Create a character by cloning the prototype and changing only the required
  // attributes
  public Character createCharacterWithNewName(String name)
      throws CloneNotSupportedException {
    Character clonedCharacter = prototypeCharacter.clone();
    clonedCharacter = new Character(name, clonedCharacter.health,
        clonedCharacter.attackPower, clonedCharacter.level);
    return clonedCharacter;
  }

  public Character createCharacterWithNewLevel(int level)
      throws CloneNotSupportedException {
    Character clonedCharacter = prototypeCharacter.clone();
    clonedCharacter = new Character(clonedCharacter.name,
        clonedCharacter.health, clonedCharacter.attackPower, level);
    return clonedCharacter;
  }

  public Character createCharacterWithNewAttackPower(int attackPower)
      throws CloneNotSupportedException {
    Character clonedCharacter = prototypeCharacter.clone();‍ 
    clonedCharacter = new Character(clonedCharacter.name,
        clonedCharacter.health, attackPower, clonedCharacter.level);
    return clonedCharacter;
  }
}