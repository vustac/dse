package edu.vanderbilt.isis.threads.simple;

public class ThreadTest implements Runnable {

  private Holder holder;
  private int id;
  
  public ThreadTest(Holder holder, int id) {
    this.holder = holder;
    this.id = id;
  }

  public void setSymbolic() {
    int x = 0;
    this.holder.setValue(x);
  }
  
  public void run() {
    if (id == 1) {
      System.out.println("In thread 1!");
      setSymbolic();
    }
    else if (id == 2) {
      System.out.println("In thread 2!");
      try {
        Thread.sleep(2000); // make sure to wait
      } catch (InterruptedException e) {
        System.err.println("Interrupted while waiting in thread 2!");
      }
      int val = this.holder.getValue() + 1;
      if (val  == 17) {
        System.out.println("Symbolic path: all ok!");
      } else {
        System.out.println("Concrete path: all ok!");
      }
    } 
  }

  public static void main(String[] args) {
    Holder h = new Holder();
    ThreadTest test1 = new ThreadTest(h, 1);
    ThreadTest test2 = new ThreadTest(h, 2);
    
    Thread t1 = new Thread(test1);
    Thread t2 = new Thread(test2);

    t1.start();
    t2.start();

    try {
      t1.join();
      t2.join();
    } catch (InterruptedException e) {}
  }

  public static class Holder {
    private int symbInt = 0;

    public void setValue(int val) {
      this.symbInt = val;
    }

    public int getValue() {
      return this.symbInt;
    }
  }
    
}
