package org.usvm.samples.types;

import static org.usvm.api.mock.UMockKt.assume;

import java.util.List;

public class PathDependentGenericsExample {
    public int pathDependentGenerics(GenericParent element) {
        if (element instanceof ClassWithOneGeneric) {
            functionWithOneGeneric((ClassWithOneGeneric<?>) element);
            return 1;
        }

        if (element instanceof ClassWithTwoGenerics) {
            functionWithTwoGenerics((ClassWithTwoGenerics<?, ?>) element);
            return 2;
        }

        return 3;
    }

    @SuppressWarnings("unchecked")
    public int functionWithSeveralTypeConstraintsForTheSameObject(Object element) {
        if (element instanceof List<?>) {
            functionWithSeveralGenerics((List<? extends Number>) element, (List<?>) element);

            assume(!((List<?>) element).isEmpty());
            Object value = ((List<?>) element).get(0);
            assume(value != null);

            if (value instanceof Number) {
                return 1;
            } else {
                return 2; // unreachable
            }
        }

        return 3;
    }

    private <T, K extends Number> void functionWithSeveralGenerics(List<K> firstValue, List<T> anotherValue) {
    }

    private <T> void functionWithOneGeneric(ClassWithOneGeneric<T> value) {

    }

    private <T, K> void functionWithTwoGenerics(ClassWithTwoGenerics<T, K> value) {

    }
}

abstract class GenericParent {
}

class ClassWithOneGeneric<T> extends GenericParent {
}

class ClassWithTwoGenerics<T, A> extends GenericParent {
}
