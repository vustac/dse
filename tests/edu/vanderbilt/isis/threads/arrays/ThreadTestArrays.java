package edu.vanderbilt.isis.threads.arrays;

public class ThreadTestArrays implements Runnable {

  private Holder holder;
  private int id;
  
  public ThreadTestArrays(Holder holder, int id) {
    this.holder = holder;
    this.id = id;
  }

  public void setSymbolic() {
    int x = 0;
    this.holder.setValue(0, x);
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
      int val = this.holder.getValue(0) + 1;
      if (val  == 17) {
	System.out.println("Symbolic path: all ok!");
      } else {
	System.out.println("Concrete path: all ok!");
      }
    } 
  }

  public static void main(String[] args) {
    Holder h = new Holder();
    ThreadTestArrays test1 = new ThreadTestArrays(h, 1);
    ThreadTestArrays test2 = new ThreadTestArrays(h, 2);
    
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
    private int[] symbArray = new int[10];

    public void setValue(int index, int val) {
      this.symbArray[index] = val;
    }

    public int getValue(int index) {
      return this.symbArray[index];
    }
  }
    
}
