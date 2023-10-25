package example.hierarchy;

// Abstract class for a laptop
abstract class Laptop implements Computer {
    protected String brand;
    protected double screenSize;

    public Laptop(String brand, double screenSize) {
        this.brand = brand;
        this.screenSize = screenSize;
    }

    public abstract void type();

    public void turnOn() {
        System.out.println("Turning on the " + brand + " laptop with a " + screenSize + " inch screen...");
    }

    public void turnOff() {
        System.out.println("Turning off the " + brand + " laptop with a " + screenSize + " inch screen...");
    }
}