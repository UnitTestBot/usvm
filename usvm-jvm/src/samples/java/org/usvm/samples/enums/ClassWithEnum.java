package org.usvm.samples.enums;

@SuppressWarnings({"UnnecessaryLocalVariable", "IfStatementWithIdenticalBranches"})
public class ClassWithEnum {
    public int useOrdinal(String s) {
        if (s != null) {
            final int ordinal = StatusEnum.READY.ordinal();
            return ordinal;
        } else {
            final int ordinal = StatusEnum.ERROR.ordinal();
            return ordinal;
        }
    }

    public int useGetter(String s) {
        if (s != null) {
            final int mutableInt = StatusEnum.READY.getMutableInt();
            return mutableInt;
        } else {
            final int mutableInt = StatusEnum.ERROR.getMutableInt();
            return mutableInt;
        }
    }

    public int useEnumInDifficultIf(String s) {
        if ("TRYIF".equalsIgnoreCase(s)) {
            final ManyConstantsEnum[] values = ManyConstantsEnum.values();
            return foo(values[0]);
        } else {
            final ManyConstantsEnum b = ManyConstantsEnum.B;
            return foo(b);
        }
    }

    private int foo(ManyConstantsEnum e) {
        if (e.equals(ManyConstantsEnum.A)) {
            return 1;
        } else {
            return 2;
        }
    }

    public int nullEnumAsParameter(StatusEnum statusEnum) {
        final int ordinal = statusEnum.ordinal();
        return ordinal;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public int nullField(StatusEnum statusEnum) {
        // catch NPE
        statusEnum.s.length();

        statusEnum.s = "-200";
        return statusEnum.s.length();
    }

    public int changeEnum(StatusEnum statusEnum) {
        if (statusEnum == StatusEnum.READY) {
            statusEnum = StatusEnum.ERROR;
        } else {
            statusEnum = StatusEnum.READY;
        }

        // ERROR -> READY -> 0
        // READY -> ERROR -> 1
        return statusEnum.ordinal();
    }

    public String checkName(String s) {
        final String name = StatusEnum.READY.name();
        if (s.equals(name)) {
            return StatusEnum.ERROR.name();
        }

        return StatusEnum.READY.name();
    }

    public int changeMutableField(StatusEnum statusEnum) {
        if (statusEnum == StatusEnum.READY) {
            StatusEnum.READY.mutableInt = 2;

            return StatusEnum.READY.mutableInt;
        }

        StatusEnum.ERROR.mutableInt = -2;
        return StatusEnum.ERROR.mutableInt;
    }

    @SuppressWarnings("unused")
    public boolean changingStaticWithEnumInit() {
        // run <init> and <clinit> sections
        final EnumWithStaticAffectingInit[] values = EnumWithStaticAffectingInit.values();

        return true;
    }

    public int virtualFunction(StatusEnum parameter) {
        int value = parameter.virtualFunction();
        if (value > 0) {
            return value;
        }

        return Math.abs(value);
    }

    @SuppressWarnings("LombokGetterMayBeUsed")
    public enum StatusEnum {
        READY(0, 10, "200") {
            @Override
            public int virtualFunction() {
                return 0;
            }
        },
        ERROR(-1, -10, null) {
            @Override
            int virtualFunction() {
                return 1;
            }
        };

        int mutableInt;
        final int code;
        String s;

        StatusEnum(int mutableInt, final int code, String s) {
            this.mutableInt = mutableInt;
            this.code = code;
            this.s = s;
        }

        public int getMutableInt() {
            return mutableInt;
        }

        public int getCode() {
            return code;
        }

        static StatusEnum fromCode(int code) {
            for (StatusEnum value : values()) {
                if (value.getCode() == code) {
                    return value;
                }
            }

            throw new IllegalArgumentException("No enum corresponding to given code");
        }

        static StatusEnum fromIsReady(boolean isReady) {
            return isReady ? READY : ERROR;
        }

        int publicGetCode() {
            return this == READY ? 10 : -10;
        }

        abstract int virtualFunction();
    }

    enum ManyConstantsEnum {
        A, B, C, D, E, F, G, H, I, J, K
    }

    static int x = 0;

    static class ClassWithStaticField {
        static int y = 0;

        static void increment() {
            y++;
        }
    }

    enum EnumWithStaticAffectingInit {
        A, B;

        EnumWithStaticAffectingInit() {
            ClassWithStaticField.y++;
            ClassWithStaticField.increment();
            invokeIncrement();
            invokeIncrementStatic();

            // changes after all init sections:
            // y = y + 4 * 2 = y + 8
        }

        static {
            x++;
            ClassWithStaticField.y++;
            ClassWithStaticField.increment();
            invokeIncrementStatic();

            // changes after clinit section:
            // y = y + 3
            // x = x + 1
        }

        void invokeIncrement() {
            ClassWithStaticField.increment();
        }

        static void invokeIncrementStatic() {
            ClassWithStaticField.increment();
        }
    }

    public int implementingInterfaceEnumInDifficultBranch(String s) {
        if ("SUCCESS".equalsIgnoreCase(s)) {
            //noinspection ConstantValue
            return EnumImplementingInterface.x + EnumImplementingInterface.A_INHERITOR.ordinal();
        } else {
            return EnumImplementingInterface.y + EnumImplementingInterface.B_INHERITOR.ordinal();
        }
    }

    interface AncestorInterface {
        int y = 1;
    }

    interface InterfaceWithField extends AncestorInterface {
        int x = 0;
    }

    enum EnumImplementingInterface implements InterfaceWithField {
        A_INHERITOR, B_INHERITOR, C_INHERITOR, D_INHERITOR,
        E_INHERITOR, F_INHERITOR, G_INHERITOR, H_INHERITOR,
        I_INHERITOR, J_INHERITOR, K_INHERITOR, L_INHERITOR,
        M_INHERITOR, N_INHERITOR, O_INHERITOR, P_INHERITOR,
    }

    boolean affectSystemStaticAndInitEnumFromItAndReturnField() {
        int prevStaticValue = ClassWithEnum.staticInt;
        staticInt++;

        return OuterStaticUsageEnum.A.y != prevStaticValue;
    }

    boolean affectSystemStaticAndInitEnumFromItAndGetItFromEnumFun() {
        int prevStaticValue = ClassWithEnum.staticInt;
        staticInt++;

        return OuterStaticUsageEnum.A.getOuterStatic() != prevStaticValue;
    }

    static int staticInt = 42;

    enum OuterStaticUsageEnum {
        A;

        final int y;

        OuterStaticUsageEnum() {
            y = staticInt;
        }

        int getOuterStatic() {
            return staticInt;
        }


        @Override
        public String toString() {
            return String.format("%s(y = %d)", name(), y);
        }
    }

    @SuppressWarnings("unused")
    int takeTwoEnumParameters(OuterStaticUsageEnum first, StatusEnum second, StatusEnum third) {
        // For this method we should analyze static initializers for classes of all arguments, even though the first argument is unused
        if (second == null) return 1;
        if (third == null) return 2;

        if (second != third) {
            return 0;
        } else {
            return -1;
        }
    }
}
