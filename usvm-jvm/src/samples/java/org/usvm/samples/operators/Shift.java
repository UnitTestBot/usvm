package org.usvm.samples.operators;

public class Shift {
    public int shlInt(int x, int shift) {
        int shifted = x << shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }

    public int shlByte(byte x, byte shift) {
        int shifted = x << shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }

    public int shlShort(short x, short shift) {
        int shifted = x << shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }

    public int shlLong(long x, long shift) {
        long shifted = x << shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }

    public int shlLongByInt(long x, int shift) {
        long shifted = x << shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }

    public int shlByteByInt(byte x, int shift) {
        int shifted = x << shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }

    public int shrInt(int x, int shift) {
        int shifted = x >> shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }

    public int shrByte(byte x, byte shift) {
        int shifted = x >> shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }

    public int shrShort(short x, short shift) {
        int shifted = x >> shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }

    public int shrLong(long x, long shift) {
        long shifted = x >> shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }

    public int shrLongByInt(long x, int shift) {
        long shifted = x >> shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }

    public int shrByteByInt(byte x, int shift) {
        int shifted = x >> shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }

    public int ushrInt(int x, int shift) {
        int shifted = x >>> shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }

    public int ushrByte(byte x, byte shift) {
        int shifted = x >>> shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }

    public int ushrShort(short x, short shift) {
        int shifted = x >>> shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }

    public int ushrLong(long x, long shift) {
        long shifted = x >>> shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }

    public int ushrLongByInt(long x, int shift) {
        long shifted = x >>> shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }

    public int ushrByteByInt(byte x, int shift) {
        int shifted = x >>> shift;
        if (shift == 0) {
            return -1;
        }
        if (shifted == x) {
            return 1;
        }
        return 0;
    }
}
