package org.usvm.samples.statics;

public class StaticsTypesExample {
    // In this method we check correct types in org.usvm.samples.statics.StaticsTypesExample.ClassWithArrayField.arr (not enums)
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void virtualInvokeOnInputFieldArrayReading(ClassWithArrayField c, int i) {
        EnumWithTwoValues.values();
        c.arr[i].foo();
    }

    public static class ClassWithArrayField {
        ClassWithVirtualInvoke[] arr;

        public ClassWithArrayField() {
            // Size as number of enum constants in EnumWithTwoValues
            arr = new ClassWithVirtualInvoke[2];
        }
    }

    public static class ClassWithVirtualInvoke {
        void foo() {}
    }


    public enum EnumWithTwoValues {
        A, B,
    }
}
