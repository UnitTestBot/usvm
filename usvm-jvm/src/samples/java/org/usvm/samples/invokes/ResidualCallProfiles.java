package org.usvm.samples.invokes;

public class ResidualCallProfiles {
    private static int marker;

    private static native int unknownInt();
    private static native byte unknownByte();
    private static native short unknownShort();
    private static native char unknownChar();
    private static native void unknownVoid();

    public static int successor() {
        unknownInt();

        return 17;
    }

    public static int voidSuccessor() {
        marker = 1;
        unknownVoid();
        marker = 17;

        return marker;
    }

    public static int independentResults() {
        int first = unknownInt();
        int second = unknownInt();

        return first == second ? 1 : 2;
    }

    public static int primitiveBounds() {
        int narrowByte = unknownByte();
        int narrowShort = unknownShort();
        int narrowChar = unknownChar();

        boolean byteInRange = narrowByte >= -128 && narrowByte <= 127;
        boolean shortInRange = narrowShort >= -32768 && narrowShort <= 32767;
        boolean charInRange = narrowChar >= 0 && narrowChar <= 65535;

        return byteInRange && shortInRange && charInRange ? 1 : 0;
    }

    public static int modeledNative() {
        int[] source = new int[]{17};
        int[] destination = new int[]{0};

        System.arraycopy(source, 0, destination, 0, 1);

        return destination[0];
    }

    private static int executableBody() {
        return 17;
    }

    public static int ordinaryBody() {
        return executableBody();
    }
}
