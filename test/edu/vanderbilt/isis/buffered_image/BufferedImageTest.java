package edu.vanderbilt.isis.buffered_image;

import java.awt.image.BufferedImage;

public class BufferedImageTest {

  public static void makeImage() {
    BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);

    image.setRGB(0, 0, 17);
        
    int x00 = image.getRGB(0, 0) & 0xFF;
    int x01 = image.getRGB(0, 1);
    int x10 = image.getRGB(1, 0);
    int x11 = image.getRGB(1, 1);

    if (x00 == 17 && x01 == 1 && x10 == 2 && x11 == 3)
      System.out.println("Symbolic path!");
    else
      System.out.println("Concrete path!");
  }

  public static void main(String[] args) {
    System.out.println("Beginning BufferedImage test");
    makeImage();
    System.out.println("Ending BufferedImage test");
  }
}
