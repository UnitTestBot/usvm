package org.usvm.examples.mock;

import org.usvm.examples.mock.others.FinalClass;

public class MockFinalClassExample {
    FinalClass intProvider;

    int useFinalClass() {
        int x = intProvider.provideInt();
        if (x == 1) {
            return 1;
        } else {
            return 2;
        }
    }

}
