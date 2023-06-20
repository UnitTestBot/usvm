package org.usvm.samples.operators;

@SuppressWarnings("ConstantValue")
public class Math {
    int addIntToInt(int x, int y) {
        return x + y;
    }

    short addShortToShort(short x, short y) {
        return (short) (x + y);
    }


    long addLongToInt(int x, long y) {
        return x + y;
    }

    int addLongToLongReturnInt(long x, long y) {
        return (int) (x + y);
    }

    byte addIntToShortReturnByte(short x, int y) {
        return (byte) (x + y);
    }

    int multiplyInts(int x, int y) {
        return x * y;
    }


    int multiplyDoublesReturnInt(double x, double y) {
        return (int) (x * y);
    }

    short multiplyDoubleAndFloatReturnShort(double x, float y) {
        return (short) (x * y);
    }


    char divideIntsReturnChar(int x, int y) {
        return (char) (x / y);
    }

    long divideIntsReturnLong(int x, int y) {
        return x / y;
    }

    short findRemainderBytesReturnShort(byte x, byte y) {
        return (short) (x / y);
    }

    byte findRemainderBytes(byte x, byte y) {
        return (byte) (x / y);
    }

    int complex(int x, float y, short z) {
        return (int) (-x * y + (~z ^ x));
    }

    int bitwiseShift(short x, byte y) {
        return x << y;
    }


    int shortOverflow(short x, int y) {
        if (y > 10 || y <= 0) {
            return 0;
        }
        if (x + y < 0 && x > 0) {
            throw new IllegalStateException();
        }
        return x + y;
    }
}
