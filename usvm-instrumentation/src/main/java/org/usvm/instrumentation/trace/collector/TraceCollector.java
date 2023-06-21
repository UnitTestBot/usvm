package org.usvm.instrumentation.trace.collector;

//DO NOT EDIT!
//USE TRACER
public class TraceCollector {
    public static LongArrayWrapper trace = new LongArrayWrapper();
    public static LongArrayWrapper statics = new LongArrayWrapper();

    public static void jcInstructionCovered(long jcInstructionId) {
        trace.add(jcInstructionId);
    }
    public static void jcStaticFieldAccessed(long jcStaticFieldAccessId) {
        statics.add(jcStaticFieldAccessId);
    }

    public static class LongArrayWrapper {
        public long[] arr;
        public int size;

        LongArrayWrapper() {
            arr = new long[10];
        }

        public void add(long el) {
            if (size >= arr.length) {
                resize();
            }
            arr[size] = el;
            size++;
        }

        public void removeLast() {
            arr[size] = 0;
            if (size == 0) return;
            size--;
        }

        public void clear() {
            for (int i = 0; i < size; i++) {
                arr[i] = 0;
            }
            size = 0;
        }

        private void resize() {
            int newSize = (arr.length * 3) / 2;
            long[] newArr = new long[newSize];
            for (int i = 0; i < arr.length; i++) {
                newArr[i] = arr[i];
            }
            arr = newArr;
        }
    }


}
