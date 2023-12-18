package org.usvm.instrumentation.collector.trace;

//DO NOT EDIT!
//USE TRACER
public class TraceCollector {
    public static LongHashSet trace = new LongHashSet();
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


    public static class LongHashSet {
        private static final int DEFAULT_CAPACITY = 1024;
        private static final double DEFAULT_LOAD_FACTOR = 0.75;

        private long[] keys;
        private int size;
        private int capacity;
        private final double loadFactor;
        private boolean containsNull;

        public LongHashSet() {
            this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR);
        }

        public LongHashSet(int initialCapacity, double loadFactor) {
            this.capacity = initialCapacity;
            this.loadFactor = loadFactor;
            this.size = 0;
            this.keys = new long[capacity];
            containsNull = false;
        }

        private int hash(long key) {
            int hash = (int) (key ^ (key >>> 32)) % capacity;
            return (hash + capacity) % capacity;
        }

        public void add(long key) {
            if (key == 0) {
                containsNull = true;
                return;
            }
            if (contains(key)) {
                return; // Avoid duplicates
            }

            int index = hash(key);

            while (keys[index] != 0) {
                if (index == capacity - 1) {
                    index = 0;
                    continue;
                }

                index++;
            }
            keys[index] = key;

            size++;

            // Check if rehashing is needed
            if ((double) size > loadFactor * capacity) {
                rehash();
            }
        }

        public LongArrayWrapper getAllValues() {
            LongArrayWrapper res = new LongArrayWrapper();
            int ind = 0;
            if (containsNull) {
                res.add(0);
            }
            for (long key: keys) {
                if (key != 0L) {
                    res.add(key);
                }
            }

            return res;
        }

        public boolean contains(long key) {
            if (key == 0) {
                return containsNull;
            }

            int index = hash(key);
            while (keys[index] != 0) {
                if (keys[index] == key) {
                    return true;
                }

                if (index == capacity - 1) {
                    index = 0;
                    continue;
                }

                index++;
            }
            return false;
        }

        private void rehash() {
            int newCapacity = capacity * 2;
            long[] newKeys = new long[newCapacity];

            for (long key : keys) {
                if (key == 0) continue;
                int newIndex = hash(key);
                if (newKeys[newIndex] == 0) {
                    newKeys[newIndex] = key;
                    continue;
                }
                while (newKeys[newIndex] != 0) {
                    if (newIndex == newCapacity - 1) {
                        newIndex = 0;
                        continue;
                    }

                    newIndex++;
                }
                newKeys[newIndex] = key;
            }

            keys = newKeys;
            capacity = newCapacity;
        }

        public int realSize() {
            return containsNull ? size + 1 : size;
        }

        public boolean isEmpty() {
            return realSize() == 0;
        }

        public void clear() {
            size = 0;
            capacity = DEFAULT_CAPACITY;
            keys = new long[capacity];
            containsNull = false;
        }
    }

}
