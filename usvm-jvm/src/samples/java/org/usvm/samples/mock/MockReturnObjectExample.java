package org.usvm.samples.mock;

import static org.usvm.api.mock.UMockKt.assume;
import org.usvm.samples.mock.others.Generator;
import org.usvm.samples.mock.others.Locator;

public class MockReturnObjectExample {
    @SuppressWarnings("unused")
    private Locator privateLocator;
    public Locator publicLocator;
    public Generator[] generators;

    public int calculate(int threshold) {
        int a = privateLocator.locate().generateInt();
        int b = publicLocator.locate().generateInt();
        if (threshold < a + b) {
            return threshold;
        }
        return a + b + 1;
    }

    public int calculateFromArray() {
        assume(generators.length == 3);
        int result = 0;
        for (int i = 0; i < 3; i++) {
            result += generators[i].generateInt();
        }

        if (result > 100) {
            return result;
        } else if (result < -100) {
            return result * 10;
        }

        return result;
    }
}