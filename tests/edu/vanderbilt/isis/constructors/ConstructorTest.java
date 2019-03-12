package edu.vanderbilt.isis.constructors;

public class ConstructorTest {

    public int x;
    public double y;
    
    public ConstructorTest(int x, double y) {
	this.x = x;
	this.y = y;
    }
    
    public void test() {
	if (this.y == 3.56)
	    System.out.println("Symbolic!");
	else
	    System.out.println("Concrete!");
    }

    public static void main(String[] args) {
	System.out.println("Beginning constructor test");
	ConstructorTest c = new ConstructorTest(1, 2.0);
	c.test();
	System.out.println("Ending constructor test");
    }
    
}
