package org.usvm.examples.exceptions;

@SuppressWarnings("serial")
public class MyCheckedException extends Exception {
    private final int i;

    public MyCheckedException(int i) {
        this.i = i;
    }
}
