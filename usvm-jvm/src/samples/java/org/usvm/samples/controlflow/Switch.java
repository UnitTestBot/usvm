package org.usvm.samples.controlflow;

//import java.math.RoundingMode;

public class Switch {

    public int simpleSwitch(int x) {
        switch (x) {
            case 10:
                return 10;
            case 11: // fall-through
            case 12:
                return 12;
            case 13:
                return 13;
            default:
                return -1;
        }
    }

    @SuppressWarnings("ConstantConditions")
    public int simpleSwitchWithPrecondition(int x) {
        if (x == 10 || x == 11) {
            return 0;
        }
        switch (x) {
            case 10:
                return 10;
            case 11: // fall-through
            case 12:
                return 12;
            case 13:
                return 13;
            default:
                return -1;
        }
    }


    public int lookupSwitch(int x) {
        switch (x) {
            case 0:
                return 0;
            case 10: // fall-through
            case 20:
                return 20;
            case 30:
                return 30;
            default:
                return -1;
        }
    }

    public enum EnumExample {
        SUCCESS(10), ERROR(-10);

        final int x;

        EnumExample(int x) {
            this.x = x;
        }

        @Override
        public String toString() {
            return String.format("Enum: {name: %s, x: %d, ordinal: %d}", name(), x, ordinal());
        }
    }

    public int enumSwitch(EnumExample e) {
        switch (e) {
            case SUCCESS:
                return 1;
            case ERROR:
                return 2;
        }
        return -1; // Unreachable
    }

    public int enumCustomField(EnumExample e) {
        if (e == null) {
            return 42;
        }

        return e.x;
    }

    public String enumName(EnumExample e) {
        if (e == null) {
            return "";
        }

        if (e == EnumExample.SUCCESS) {
            return "S";
        }

        return e.name();
    }

    @SuppressWarnings("unused")
    public int unusedEnumParameter(EnumExample e) {
        if (e == null) {
            return 0;
        }

        return 42;
    }

    public String customEnumName(CustomEnum customEnum) {
        if (customEnum == null) {
            return "";
        }

        return customEnum.name();
    }

//    public int roundingModeSwitch(RoundingMode m) {
//        switch (m) {
//            case HALF_DOWN: // fall-through
//            case HALF_EVEN: // fall-through
//            case HALF_UP: // fall-through
//                return 1;
//            case DOWN:
//                return 2;
//            case CEILING:
//                return 3;
//        }
//        return -1;
//    }

    public int charToIntSwitch(char c) {
        switch (c) {
            case 'I': return 1;
            case 'V': return 5;
            case 'X': return 10;
            case 'L': return 50;
            case 'C': return 100;
            case 'D': return 500;
            case 'M': return 1000;
            default: throw new IllegalArgumentException();
        }
    }

    public int stringSwitch(String s) {
        switch (s) {
            case "ABC":
                return 1;
            case "DEF": // fall-through
            case "GHJ":
                return 2;
            default:
            return -1;
        }
    }
}

