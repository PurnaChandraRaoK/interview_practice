// Imagine you’re building a remote control system for a device like a TV.
//  Your TV remote needs to be able to perform a set of actions,
//  like turning the TV on and off, changing channels, and adjusting the volume.

// Problem

public class RemoteControl {
  private TV tv;
  public RemoteControl(TV tv) {
    this.tv = tv;
  }
  public void pressOnButton() {
    tv.turnOn();
  }
  public void pressOffButton() {
    tv.turnOff();
  }
  public void pressChannelButton(int channel) {
    tv.changeChannel(channel);
  }
  public void pressVolumeButton(int volume) {
    tv.adjustVolume(volume);
  }
  // New methods are added each time we need more actions
  public void pressOnChangeVolumeAndChannelButton(int volume, int channel) {
    tv.turnOn();
    tv.changeChannel(channel);
    tv.adjustVolume(volume);
  }
}

// As you see it is not Remote control class job 
// if the order of pressOnChangeVolumeAndChannelButton changes
// and it can become complex in no time

// Solution

public interface Command {
  void execute(); // Executes the command
}

public class TurnOnCommand implements Command {
  private TV tv;
  public TurnOnCommand(TV tv) {
    this.tv = tv;
  }
  @Override
  public void execute() {
    tv.turnOn();
  }
}
public class TurnOffCommand implements Command {
  private TV tv;
  public TurnOffCommand(TV tv) {
    this.tv = tv;
  }
  @Override
  public void execute() {
    tv.turnOff();
  }
}
public class ChangeChannelCommand implements Command {
  private TV tv;
  private int channel;
  public ChangeChannelCommand(TV tv, int channel) {
    this.tv = tv;
    this.channel = channel;
  }
  @Override
  public void execute() {
    tv.changeChannel(channel);
  }
}
public class AdjustVolumeCommand implements Command {
  private TV tv;
  private int volume;
  public AdjustVolumeCommand(TV tv, int volume) {
    this.tv = tv;
    this.volume = volume;
  }
  @Override
  public void execute() {
    tv.adjustVolume(volume);
  }
}

// In future we can provide one button in remote control to on tv,
//  set my fav channel, set brightness, set volume and it is not 
// remote control job to verify this or get code added in its class

public class RemoteControl {
  private Command onCommand;
  private Command offCommand;
  public void setOnCommand(Command onCommand) {
    this.onCommand = onCommand;
  }
  public void setOffCommand(Command offCommand) {
    this.offCommand = offCommand;
  }
  public void pressOnButton() {
    onCommand.execute();
  }
  public void pressOffButton() {
    offCommand.execute();
  }
}

public class TV {
  public void turnOn() {
    System.out.println("TV is ON");
  }
  public void turnOff() {
    System.out.println("TV is OFF");
  }
  public void changeChannel(int channel) {
    System.out.println("Channel changed to " + channel);
  }
  public void adjustVolume(int volume) {
    System.out.println("Volume set to " + volume);
  }
}

public class Main {
  public static void main(String[] args) {
    TV tv = new TV();
    // Create commands
    Command turnOn = new TurnOnCommand(tv);
    Command turnOff = new TurnOffCommand(tv);
    Command changeChannel = new ChangeChannelCommand(tv, 5);
    Command adjustVolume = new AdjustVolumeCommand(tv, 20);
    // Create remote control
    RemoteControl remote = new RemoteControl();
    remote.setOnCommand(turnOn);
    remote.setOffCommand(turnOff);
    remote.pressOnButton(); // Turn on the TV
    remote.pressOffButton(); // Turn off the TV
    // Execute other commands
    changeChannel.execute(); // Change the channel
    adjustVolume.execute(); // Adjust the volume
  }
}