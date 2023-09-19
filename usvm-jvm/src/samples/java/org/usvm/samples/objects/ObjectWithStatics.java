package org.usvm.samples.objects;

public class ObjectWithStatics {
    static Object refStaticField;

    static int mutablePrimitiveStaticField;
    final static int finalPrimitiveStaticField;

    static {
        refStaticField = new Object();
        mutablePrimitiveStaticField = 17;
        finalPrimitiveStaticField = 42;
    }

    private Object getRefFiled() {
        return refStaticField;
    }

    private int getPrimitiveFiled() {
        return mutablePrimitiveStaticField;
    }

    int staticsAreEqual() {
        if (ObjectWithStatics.refStaticField != getRefFiled()) {
            return 1;
        }
        if (ObjectWithStatics.mutablePrimitiveStaticField != getPrimitiveFiled()) {
            return 2;
        }
        return 0;
    }

    int mutateStatics() {
        int initial = getPrimitiveFiled();
        mutablePrimitiveStaticField++;
        int mutated = getPrimitiveFiled();
        return mutated - initial;
    }

    int useMutablePrimitiveStaticField() {
        if (ObjectWithStatics.mutablePrimitiveStaticField != 17) {
            // The static field primitiveStaticField is mutable primitive, so this stmt is reachable
            return 1;
        }

        return 0;
    }

    int useFinalPrimitiveStaticField() {
        if (ObjectWithStatics.finalPrimitiveStaticField != 42) {
            // We consider final primitive static fields as immutable, so this stmt is unreachable
            return 1;
        }

        return 0;
    }
}
