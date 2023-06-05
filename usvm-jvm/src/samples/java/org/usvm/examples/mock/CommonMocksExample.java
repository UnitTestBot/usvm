package org.usvm.examples.mock;

import static org.usvm.api.mock.UMockKt.assume;
import org.usvm.examples.mock.others.Random;
import org.usvm.examples.mock.service.impl.ExampleClass;
import org.usvm.examples.objects.ObjectWithFinalStatic;
import org.usvm.examples.objects.RecursiveTypeClass;

public class CommonMocksExample {
    public Object mockInterfaceWithoutImplementors(InterfaceWithoutImplementors value) {
        return value.visit(this);
    }

    public int doNotMockHashCode(ExampleClass exampleClass) {
        return exampleClass.hashCode();
    }

    public boolean doNotMockEquals(ExampleClass fst, ExampleClass snd) {
        return fst.equals(snd);
    }

    public RecursiveTypeClass nextValue(RecursiveTypeClass node) {
        if (node.next == node) {
            return node;
        }

        node.next.value = node.value + 1;

        return node;
    }

    // We should not mock clinit section.
    public int clinitMockExample() {
        if (ObjectWithFinalStatic.keyValue == 0) {
            return ObjectWithFinalStatic.keyValue;
        } else {
            return -ObjectWithFinalStatic.keyValue;
        }
    }

    public int mocksForNullOfDifferentTypes(Integer intValue, Random random) {
        assume(intValue == null);
        assume(random == null);

        return 0;
    }
}
