package org.usvm.instrumentation.collector.trace;

public class MockCollector {

    private static final int DEFAULT_MOCKINFO_ARRAY_CAPACITY = 10;
    public static MockInfoArrayWrapper mocks = new MockInfoArrayWrapper();
    public static boolean inExecution;

    public static boolean isInExecution() {
        return inExecution;
    }

    public static Object getMockValue(long mockedId, Object mockedObject) {
        return mocks.find(mockedId, mockedObject).getMockValue();
    }
    public static boolean getBooleanMockValue(long mockedId, Object mockedObject) {
        return (boolean) getMockValue(mockedId, mockedObject);
    }
    public static byte getByteMockValue(long mockedId, Object mockedObject) {
        return (byte) getMockValue(mockedId, mockedObject);
    }
    public static short getShortMockValue(long mockedId, Object mockedObject) {
        return (short) getMockValue(mockedId, mockedObject);
    }
    public static char getCharMockValue(long mockedId, Object mockedObject) {
        return (char) getMockValue(mockedId, mockedObject);
    }
    public static int getIntMockValue(long mockedId, Object mockedObject) {
        return (int) getMockValue(mockedId, mockedObject);
    }
    public static float getFloatMockValue(long mockedId, Object mockedObject) {
        return (float) getMockValue(mockedId, mockedObject);
    }
    public static double getDoubleMockValue(long mockedId, Object mockedObject) {
        return (double) getMockValue(mockedId, mockedObject);
    }
    public static long getLongMockValue(long mockedId, Object mockedObject) {
        return (long) getMockValue(mockedId, mockedObject);
    }


    public static boolean isMocked(long mockedId, Object mockedObject) {
        return mocks.find(mockedId, mockedObject) != null;
    }


    public static void addMock(MockInfo mock) {
        mocks.add(mock);
    }

    public static class MockInfo {

        public long mockedId;

        public Object mockedObject;
        public MockValueArrayWrapper mockValues;

        private int refNumber = 0;

        public MockInfo(long mockedId, Object mockedObject, MockValueArrayWrapper mockValues) {
            this.mockedId = mockedId;
            this.mockedObject = mockedObject;
            this.mockValues = mockValues;
        }

        public Object getMockValue() {
            return mockValues.arr[refNumber++];
        }

    }

    public static class MockInfoArrayWrapper {
        public MockInfo[] arr;
        public int size;

        MockInfoArrayWrapper() {
            arr = new MockInfo[DEFAULT_MOCKINFO_ARRAY_CAPACITY];
        }

        public MockInfo find(long mockId, Object mockedObject) {
            if (mockedObject == null) return findStaticMethodMock(mockId);
            for (int i = 0; i < size; i++) {
                if (arr[i].mockedId == mockId && arr[i].mockedObject == mockedObject) {
                    return arr[i];
                }
            }
            return null;
        }

        public MockInfo findStaticMethodMock(long mockId) {
            for (int i = 0; i < size; i++) {
                if (arr[i].mockedId == mockId) {
                    return arr[i];
                }
            }
            return null;
        }

        public void add(MockInfo el) {
            if (size >= arr.length) {
                resize();
            }
            arr[size] = el;
            size++;
        }

        public void removeLast() {
            if (size == 0) return;
            arr[size] = null;
            size--;
        }

        public void clear() {
            for (int i = 0; i < size; i++) {
                arr[i] = null;
            }
            size = 0;
        }

        private void resize() {
            int newSize = (arr.length * 3) / 2;
            MockInfo[] newArr = new MockInfo[newSize];
            for (int i = 0; i < arr.length; i++) {
                newArr[i] = arr[i];
            }
            arr = newArr;
        }
    }

    public static class MockValueArrayWrapper {
        public Object[] arr;
        public int size;

        public MockValueArrayWrapper() {
            arr = new Object[DEFAULT_MOCKINFO_ARRAY_CAPACITY];
        }

        public MockValueArrayWrapper(int size) {
            arr = new Object[size];
        }

        public void add(Object el) {
            if (size >= arr.length) {
                resize();
            }
            arr[size] = el;
            size++;
        }

        public void removeLast() {
            if (size == 0) return;
            arr[size] = 0;
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
            Object[] newArr = new Object[newSize];
            for (int i = 0; i < arr.length; i++) {
                newArr[i] = arr[i];
            }
            arr = newArr;
        }
    }

}
