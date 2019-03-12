package edu.vanderbilt.isis.create_object;

public class CreateObjectTest {

  private int id;
  
  public CreateObjectTest(int id) {
    this.id = id;
  }

  public static void main(String[] args) {
    System.out.println("Beginning create object test");

    CreateObjectTest t = new CreateObjectTest(1);
    
    System.out.println("Ending create object test");
  }
    
}
