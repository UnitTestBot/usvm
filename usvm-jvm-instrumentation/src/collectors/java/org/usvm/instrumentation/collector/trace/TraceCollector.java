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

        private static class Entry {
            long key;
            Entry next;

            Entry(long key, Entry next) {
                this.key = key;
                this.next = next;
            }
        }

        private Entry[] buckets;
        public int size;
        private int capacity;
        private final double loadFactor;

        public LongHashSet() {
            this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR);
        }

        public LongHashSet(int initialCapacity, double loadFactor) {
            this.capacity = initialCapacity;
            this.loadFactor = loadFactor;
            this.size = 0;
            this.buckets = new Entry[capacity];
        }

        private int hash(long key) {
            return (int) (key ^ (key >>> 32)) % capacity;
        }

        public void add(long key) {
            if (contains(key)) {
                return; // Avoid duplicates
            }

            int index = hash(key);
            Entry newEntry = new Entry(key, null);

            if (buckets[index] == null) {
                buckets[index] = newEntry;
            } else {
                Entry current = buckets[index];
                while (current.next != null) {
                    current = current.next;
                }
                current.next = newEntry;
            }

            size++;

            // Check if rehashing is needed
            if ((double) size / capacity > loadFactor) {
                rehash();
            }
        }

        public long[] getAllValues() {
            long[] values = new long[size];
            int index = 0;

            for (Entry bucket : buckets) {
                Entry current = bucket;
                while (current != null) {
                    values[index++] = current.key;
                    current = current.next;
                }
            }

            return values;
        }

        public boolean contains(long key) {
            int index = hash(key);
            Entry current = buckets[index];

            while (current != null) {
                if (current.key == key) {
                    return true;
                }
                current = current.next;
            }

            return false;
        }

        private void rehash() {
            int newCapacity = capacity * 2;
            Entry[] newBuckets = new Entry[newCapacity];

            for (Entry bucket : buckets) {
                Entry current = bucket;
                while (current != null) {
                    int newIndex = hash(current.key);

                    Entry newEntry = new Entry(current.key, null);

                    if (newBuckets[newIndex] == null) {
                        newBuckets[newIndex] = newEntry;
                    } else {
                        Entry newCurrent = newBuckets[newIndex];
                        while (newCurrent.next != null) {
                            newCurrent = newCurrent.next;
                        }
                        newCurrent.next = newEntry;
                    }

                    current = current.next;
                }
            }

            buckets = newBuckets;
            capacity = newCapacity;
        }

        public int size() {
            return size;
        }

        public boolean isEmpty() {
            return size == 0;
        }

        public void clear() {
            size = 0;
            capacity = DEFAULT_CAPACITY;
            buckets = new Entry[capacity];
        }
    }

}
