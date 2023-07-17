package org.usvm.samples.objects;

public class ObjectWithStatics {
    static Object refStaticField;

    static int primitiveStaticField;

    static {
        refStaticField = new Object();
        primitiveStaticField = 17;
    }

    private Object getRefFiled() {
        return refStaticField;
    }

    private int getPrimitiveFiled() {
        return primitiveStaticField;
    }

    int staticsAreEqual() {
        if (ObjectWithStatics.refStaticField != getRefFiled()) {
            return 1;
        }
        if (ObjectWithStatics.primitiveStaticField != getPrimitiveFiled()) {
            return 2;
        }
        return 0;
    }

    int mutateStatics() {
        int initial = getPrimitiveFiled();
        primitiveStaticField++;
        int mutated = getPrimitiveFiled();
        return mutated - initial;
    }

    int staticsInitialized() {
        if (ObjectWithStatics.primitiveStaticField != 17) {
            return 1;
        }
        return 0;
    }
}
