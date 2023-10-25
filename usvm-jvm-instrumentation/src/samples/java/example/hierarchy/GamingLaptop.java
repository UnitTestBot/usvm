package example.hierarchy;

// Concrete class for a gaming laptop
class GamingLaptop extends Laptop {
    private int graphicsCardMemory;

    public GamingLaptop(String brand, double screenSize, int graphicsCardMemory) {
        super(brand, screenSize);
        this.graphicsCardMemory = graphicsCardMemory;
    }

    public void type() {
        System.out.println("Typing on the " + brand + " gaming laptop with " + graphicsCardMemory + "GB of graphics card memory!");
    }
}
