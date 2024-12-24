package org.usvm.instrumentation.collector.trace;

//DO NOT EDIT!
//USE TRACER
public class TraceCollector {
    public static LongCollection trace = new LongHashSet();
    public static LongArrayWrapper statics = new LongArrayWrapper();

    public static void jcInstructionCovered(long jcInstructionId) {
        trace.add(jcInstructionId);
    }
    public static void jcStaticFieldAccessed(long jcStaticFieldAccessId) {
        statics.add(jcStaticFieldAccessId);
    }

    public interface LongCollection {
        void add(long key);

        long[] getAllValues();

        boolean contains(long key);

        int realSize();

        boolean isEmpty();

        void clear();
    }

    public static class LongArrayWrapper implements LongCollection {
        public long[] arr;
        public int size;

        public LongArrayWrapper() {
            arr = new long[10];
            size = 0;
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

        public long[] getAllValues() {
            long[] res = new long[size];
            for (int i = 0; i < size; i++) {
                res[i] = arr[i];
            }
            return res;
        }

        public boolean contains(long key) {
            for (int i = 0; i < size; i++) {
                if (arr[i] == key) {
                    return true;
                }
            }
            return false;
        }

        public int realSize() {
            return size;
        }

        public boolean isEmpty() {
            return size == 0;
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

    public static class LongHashSet implements LongCollection {
        private static final int DEFAULT_CAPACITY = 1024;
        private static final double DEFAULT_LOAD_FACTOR = 0.75;

        public long[] keys;
        public int size;
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
            return (int) ((key ^ (key >>> 32)) % capacity);
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

            if (keys[index] != 0) {
                while (keys[++index] != 0) {
                    if (index == capacity - 1) {
                        index = 0;
                    }
                }
            }
            keys[index] = key;

            size++;

            // Check if rehashing is needed
            if ((double) size / capacity > loadFactor) {
                rehash();
            }
        }

        public long[] getAllValues() {
            long[] res = new long[realSize()];
            int ind = 0;
            if (containsNull) {
                res[0] = 0;
                ind++;
            }
            for (long key: keys) {
                if (key != 0) {
                    res[ind++] = key;
                }
            }
            return res;
        }

        public boolean contains(long key) {
            int index = hash(key);
            long current = keys[index];
            if (current == key) return true;
            while (keys[++index] != 0) {
                if (keys[index] == key) return true;
                if (index == capacity - 1) {
                    index = 0;
                }
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
                while (newKeys[++newIndex] != 0) {
                    if (newIndex == newCapacity - 1) {
                        newIndex = 0;
                    }
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
