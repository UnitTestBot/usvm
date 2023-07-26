package org.usvm.samples.controlflow;

public class CustomEnum {
    private static final CustomEnum[] values = new CustomEnum[2];
    public static final CustomEnum VALUE1 = new CustomEnum("VALUE1");
    public static final CustomEnum VALUE2 = new CustomEnum("VALUE2");

    private static int counter = 0;
    private final String name;
    private final int ordinal;

    private CustomEnum(String name) {
        this.name = name;
        this.ordinal = counter;
        values[counter++] = this;
    }

    public static CustomEnum[] values() {
        return values.clone();
    }

    public static CustomEnum valueOf(String name) {
        for (CustomEnum customEnum : values) {
            if (customEnum.name.equals(name)) {
                return customEnum;
            }
        }
        throw new IllegalArgumentException("No enum constant with name " + name);
    }

    public String name() {
        return name;
    }

    public int ordinal() {
        return ordinal;
    }

    @Override
    public String toString() {
        return String.format("CustomEnum: {name: %s, ordinal: %d}", name, ordinal);
    }
}
