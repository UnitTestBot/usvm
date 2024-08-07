package org.usvm.api;

import org.usvm.api.internal.SymbolicIdentityMapImpl;
import org.usvm.api.internal.SymbolicListImpl;
import org.usvm.api.internal.SymbolicMapImpl;

import java.lang.reflect.Array;

public class Engine {

    public static void assume(boolean expr) {
        assert expr;
    }

    @SuppressWarnings("unused")
    public static <T> T makeSymbolic(Class<T> clazz) {
        return null;
    }

    @SuppressWarnings("unused")
    public static <T> T makeNullableSymbolic(Class<T> clazz) {
        return null;
    }

    public static boolean makeSymbolicBoolean() {
        return false;
    }

    public static byte makeSymbolicByte() {
        return 0;
    }

    public static char makeSymbolicChar() {
        return 0;
    }

    public static short makeSymbolicShort() {
        return 0;
    }

    public static int makeSymbolicInt() {
        return 0;
    }

    public static long makeSymbolicLong() {
        return 0;
    }

    public static float makeSymbolicFloat() {
        return 0;
    }

    public static double makeSymbolicDouble() {
        return 0;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] makeSymbolicArray(Class<T> clazz, int size) {
        assert clazz.isArray();

        return (T[]) Array.newInstance(clazz, size);
    }

    public static boolean[] makeSymbolicBooleanArray(int size) {
        return new boolean[size];
    }

    public static byte[] makeSymbolicByteArray(int size) {
        return new byte[size];
    }

    public static char[] makeSymbolicCharArray(int size) {
        return new char[size];
    }

    public static short[] makeSymbolicShortArray(int size) {
        return new short[size];
    }

    public static int[] makeSymbolicIntArray(int size) {
        return new int[size];
    }

    public static long[] makeSymbolicLongArray(int size) {
        return new long[size];
    }

    public static float[] makeSymbolicFloatArray(int size) {
        return new float[size];
    }

    public static double[] makeSymbolicDoubleArray(int size) {
        return new double[size];
    }

    public static <T> SymbolicList<T> makeSymbolicList() {
        return new SymbolicListImpl<>();
    }

    public static <K, V> SymbolicMap<K, V> makeSymbolicMap() {
        return new SymbolicMapImpl<>();
    }

    public static <K, V> SymbolicIdentityMap<K, V> makeSymbolicIdentityMap() {
        return new SymbolicIdentityMapImpl<>();
    }

    public static boolean typeEquals(Object a, Object b) {
        return a.getClass() == b.getClass();
    }

    public static boolean typeIs(Object a, Class<?> type) {
        return a.getClass() == type;
    }
}
