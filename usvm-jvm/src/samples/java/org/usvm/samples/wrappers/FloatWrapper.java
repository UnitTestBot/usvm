package org.usvm.samples.wrappers;

public class FloatWrapper {
    Float primitiveToWrapper(float i) {
        Float a = i;
        if (a >= 0) {
            return a;
        } else {
            return -a;
        }
    }

    float wrapperToPrimitive(Float i) {
        float a = i;
        if (a >= 0) {
            return a;
        } else {
            return -a;
        }
    }
}
