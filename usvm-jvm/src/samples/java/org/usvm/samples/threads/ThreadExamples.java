package org.usvm.samples.threads;

import java.util.ArrayList;
import java.util.List;

public class ThreadExamples {
    public void explicitExceptionInStart() {
        new Thread(() -> {
            throw new IllegalStateException();
        }).start();
    }

    public int changingCollectionInThread() {
        List<Integer> values = new ArrayList<>();

        new Thread(() -> values.add(42)).start();

        return values.get(0);
    }

    @SuppressWarnings("unused")
    public int changingCollectionInThreadWithoutStart() {
        List<Integer> values = new ArrayList<>();

        final Thread thread = new Thread(() -> values.add(42));

        return values.get(0);
    }

    // In this method we check that java.lang.ThreadLocal.ThreadLocalMap#table contain values of correct type (not enum)
    public String getThreadLocalValue() {
        /*@SuppressWarnings("unused")
        AppContext appContext = AppContext.getAppContext();
        ThreadLocal<String> threadLocal = new ThreadLocal<>();
        threadLocal.set("42");

        return threadLocal.get();*/
        // Init enum
        //noinspection ResultOfMethodCallIgnored
        EnumWithSixteenValues.values();

        ThreadLocal<String> threadLocal = new ThreadLocal<>();

        return threadLocal.get();
    }


    // Contains 16 constants - the same as java.lang.ThreadLocal.ThreadLocalMap.INITIAL_CAPACITY
    public enum EnumWithSixteenValues {
        A,
        B,
        C,
        D,
        E,
        F,
        G,
        H,
        I,
        J,
        K,
        L,
        M,
        N,
        O,
        P,
    }
}
