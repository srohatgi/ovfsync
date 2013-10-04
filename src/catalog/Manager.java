package catalog;

import java.io.File;

public class Manager {
  
  public Delta catalogDelta() {
    return new Delta();
  }
  
  public Run start(Delta d) {
    Run run = new Run();
    run.start();
    return run;
  }
  
  public Run status() {
    return Run.LoadActiveRun();
  }
  
  public void abort() {
    Run.LoadActiveRun().abort();
  }
  
  public static void main(String[] args) {
    // TODO Auto-generated method stub

  }
}

class Run {
  File runDir;
  File activeRunFile;

  public void start() {
    
  }
  
  public void abort() {
    
  }
  
  public static Run LoadActiveRun() {
    return new Run();
  }
}

class Delta {
  
}