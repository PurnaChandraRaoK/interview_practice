// employee submits a leave request. Depending on how many days of leave are requested,
//  different people can approve it. For example, a short leave is handled by a Supervisor,
//   a moderate leave by a Manager, and a longer leave by a Director.

// Problem

public class LeaveRequestTraditional {
  public static void main(String[] args) {
    int leaveDays = 10; // Employee requests 10 days off
    if (leaveDays <= 3) {
      System.out.println("Supervisor approved the leave.");
    } else if (leaveDays <= 7) {
      System.out.println("Manager approved the leave.");
    } else if (leaveDays <= 14) {
      System.out.println("Director approved the leave.");
    } else {
      System.out.println("Leave request denied. Too many days!");
    }
  }
}

// Solution

abstract class Approver {
  protected Approver nextApprover;
  // Set the next handler in the chain
  public void setNextApprover(Approver nextApprover) {
    this.nextApprover = nextApprover;
  }
  // Abstract method to process the leave request
  public abstract void processLeaveRequest(int leaveDays);
}

class Supervisor extends Approver {
  @Override
  public void processLeaveRequest(int leaveDays) {
    if (leaveDays <= 3) {
      System.out.println("Supervisor approved the leave.");
    } else if (nextApprover != null) {
      nextApprover.processLeaveRequest(leaveDays);
    }
  }
}

class Manager extends Approver {
  @Override
  public void processLeaveRequest(int leaveDays) {
    if (leaveDays <= 7) {
      System.out.println("Manager approved the leave.");
    } else if (nextApprover != null) {
      nextApprover.processLeaveRequest(leaveDays);
    }
  }
}

class Director extends Approver {
  @Override
  public void processLeaveRequest(int leaveDays) {
    if (leaveDays <= 14) {
      System.out.println("Director approved the leave.");
    } else if (nextApprover != null) { // Pass on if not handled
      nextApprover.processLeaveRequest(leaveDays);
    } else {
      System.out.println("Leave request denied. Too many days!");
    }
  }
}

public class LeaveRequestChainDemo {
  public static void main(String[] args) {
    // Create handler instances
    Approver supervisor = new Supervisor();
    Approver manager = new Manager();
    Approver director = new Director();
    // Set up the chain: Supervisor -> Manager -> Director
    supervisor.setNextApprover(manager);
    manager.setNextApprover(director);
    // Process a leave request
    int leaveDays = 10;
    System.out.println("Employee requests " + leaveDays + " days of leave.");
    supervisor.processLeaveRequest(leaveDays);
  }
}