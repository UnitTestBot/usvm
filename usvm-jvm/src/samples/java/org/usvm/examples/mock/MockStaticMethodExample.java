package org.usvm.examples.mock;

import org.usvm.examples.mock.others.Random;

public class MockStaticMethodExample {
    public int useStaticMethod() {
        int value = Random.nextRandomInt();
        if (value > 50) {
            return 100;
        }

        return 0;
    }

    public void mockStaticMethodFromAlwaysMockClass() {
        System.out.println("example");
    }
}
