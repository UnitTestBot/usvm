package org.usvm.examples.inner;

import org.usvm.examples.algorithms.BinarySearch;
import org.usvm.examples.arrays.ArrayOfArrays;
import org.usvm.examples.controlflow.Cycles;
import org.usvm.examples.controlflow.Switch;
import org.usvm.examples.exceptions.ExceptionExamples;
import org.usvm.examples.invokes.InvokeExample;
import org.usvm.examples.recursion.Recursion;
import org.usvm.examples.strings.StringExamples;

public class InnerCalls {

    public int callLoopInsideLoop(int x) {
        Cycles cycles = new Cycles();
        return cycles.loopInsideLoop(x);
    }

    public int callLeftBinSearch(long[] array, long key) {
        BinarySearch binarySearch = new BinarySearch();
        return binarySearch.leftBinSearch(array, key);
    }

    public void callCreateNewThreeDimensionalArray(int length, int constValue) {
        ArrayOfArrays arrayOfArrays = new ArrayOfArrays();
        arrayOfArrays.createNewThreeDimensionalArray(length, constValue);
    }

    public int callInitExamples(int n) {
        ExceptionExamples exceptionExamples = new ExceptionExamples();
        return exceptionExamples.initAnArray(n);
    }

    public int callFactorial(int n) {
        Recursion r = new Recursion();
        return r.factorial(n);
    }

    public int callFib(int n) {
        Recursion r = new Recursion();
        return r.fib(n);
    }

    public int callSimpleInvoke(int f, int s) {
        InvokeExample invokeExample = new InvokeExample();
        return invokeExample.simpleFormula(f, s);
    }

    public int callStringExample(String s, String key) {
        StringExamples stringExamples = new StringExamples();
        return stringExamples.indexOf(s, key);
    }

    public void callSimpleSwitch(int x) {
        new Switch().simpleSwitch(x);
    }

    public void callLookup(int x) {
        new Switch().lookupSwitch(x);
    }

    public void doubleSimpleInvoke(int f, int s) {
        callSimpleInvoke(f, s);
    }

    public void doubleCallLookUp(int x) {
        callLookup(x);
    }

    public int doubleCallFactorial(int n) {
        int result = callFactorial(n);
        return result;
    }

    public void doubleVoidCallFactorial(int n) {
        callFactorial(n);
    }

    public int doubleCallLoopInsideLoop(int x) {
        int result = callLoopInsideLoop(x);
        return result;
    }
}
