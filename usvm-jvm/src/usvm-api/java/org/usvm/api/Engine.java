package org.usvm.api;

public class Engine {
    public static void assume(boolean expr) {
        engineApiStubError();
    }

    public static <T> T makeSymbolic(Class<T> clazz) {
        engineApiStubError();
        return null;
    }

    public static boolean makeSymbolicBoolean() {
        engineApiStubError();
        return false;
    }

    public static byte makeSymbolicByte() {
        engineApiStubError();
        return 0;
    }

    public static char makeSymbolicChar() {
        engineApiStubError();
        return 0;
    }

    public static short makeSymbolicShort() {
        engineApiStubError();
        return 0;
    }

    public static int makeSymbolicInt() {
        engineApiStubError();
        return 0;
    }

    public static long makeSymbolicLong() {
        engineApiStubError();
        return 0;
    }

    public static float makeSymbolicFloat() {
        engineApiStubError();
        return 0;
    }

    public static double makeSymbolicDouble() {
        engineApiStubError();
        return 0;
    }

    public static <T> T[] makeSymbolicArray(Class<T> clazz, int size) {
        engineApiStubError();
        return null;
    }

    public static boolean[] makeSymbolicBooleanArray(int size) {
        engineApiStubError();
        return null;
    }

    public static byte[] makeSymbolicByteArray(int size) {
        engineApiStubError();
        return null;
    }

    public static char[] makeSymbolicCharArray(int size) {
        engineApiStubError();
        return null;
    }

    public static short[] makeSymbolicShortArray(int size) {
        engineApiStubError();
        return null;
    }

    public static int[] makeSymbolicIntArray(int size) {
        engineApiStubError();
        return null;
    }

    public static long[] makeSymbolicLongArray(int size) {
        engineApiStubError();
        return null;
    }

    public static float[] makeSymbolicFloatArray(int size) {
        engineApiStubError();
        return null;
    }

    public static double[] makeSymbolicDoubleArray(int size) {
        engineApiStubError();
        return null;
    }

    public static <T> SymbolicList<T> makeSymbolicList() {
        engineApiStubError();
        return null;
    }

    public static <K, V> SymbolicMap<K, V> makeSymbolicMap() {
        engineApiStubError();
        return null;
    }

    public static boolean typeEquals(Object a, Object b) {
        engineApiStubError();
        return false;
    }

    private static void engineApiStubError() {
        throw new IllegalStateException("Engine API method must not be invoked");
    }
}
