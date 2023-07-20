package org.usvm.instrumentation.collector.trace;

public class MockCollector {

    public static MockInfoArrayWrapper mocks = new MockInfoArrayWrapper();

    public static boolean inExecution;

    public static boolean isInExecution() {
        return inExecution;
    }

    public static Object getMockValue(long mockedId, Object mockedObject) {
        return mocks.find(mockedId, mockedObject).getMockValue();
    }
    public static boolean getBooleanMockValue(long mockedId, Object mockedObject) {
        return (boolean) mocks.find(mockedId, mockedObject).getMockValue();
    }
    public static byte getByteMockValue(long mockedId, Object mockedObject) {
        return (byte) mocks.find(mockedId, mockedObject).getMockValue();
    }
    public static short getShortMockValue(long mockedId, Object mockedObject) {
        return (short) mocks.find(mockedId, mockedObject).getMockValue();
    }
    public static char getCharMockValue(long mockedId, Object mockedObject) {
        return (char) mocks.find(mockedId, mockedObject).getMockValue();
    }
    public static int getIntMockValue(long mockedId, Object mockedObject) {
        return (int) mocks.find(mockedId, mockedObject).getMockValue();
    }
    public static float getFloatMockValue(long mockedId, Object mockedObject) {
        return (float) mocks.find(mockedId, mockedObject).getMockValue();
    }
    public static double getDoubleMockValue(long mockedId, Object mockedObject) {
        return (double) mocks.find(mockedId, mockedObject).getMockValue();
    }
    public static long getLongMockValue(long mockedId, Object mockedObject) {
        return (long) mocks.find(mockedId, mockedObject).getMockValue();
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
            arr = new MockInfo[10];
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
            arr[size] = null;
            if (size == 0) return;
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
            arr = new Object[10];
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
            Object[] newArr = new Object[newSize];
            for (int i = 0; i < arr.length; i++) {
                newArr[i] = arr[i];
            }
            arr = newArr;
        }
    }

}
