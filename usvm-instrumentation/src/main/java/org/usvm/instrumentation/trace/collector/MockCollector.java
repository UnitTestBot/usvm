package org.usvm.instrumentation.trace.collector;

public class MockCollector {

    public static MockInfoArrayWrapper mocks = new MockInfoArrayWrapper();

    public static Object getMockValue(long mockedId, Object mockedObject) {
        return mocks.find(mockedId, mockedObject).mockValue;
    }
    public static boolean getBooleanMockValue(long mockedId, Object mockedObject) {
        return (boolean) mocks.find(mockedId, mockedObject).mockValue;
    }
    public static byte getByteMockValue(long mockedId, Object mockedObject) {
        return (byte) mocks.find(mockedId, mockedObject).mockValue;
    }
    public static short getShortMockValue(long mockedId, Object mockedObject) {
        return (short) mocks.find(mockedId, mockedObject).mockValue;
    }
    public static char getCharMockValue(long mockedId, Object mockedObject) {
        return (char) mocks.find(mockedId, mockedObject).mockValue;
    }
    public static int getIntMockValue(long mockedId, Object mockedObject) {
        return (int) mocks.find(mockedId, mockedObject).mockValue;
    }
    public static float getFloatMockValue(long mockedId, Object mockedObject) {
        return (float) mocks.find(mockedId, mockedObject).mockValue;
    }
    public static double getDoubleMockValue(long mockedId, Object mockedObject) {
        return (double) mocks.find(mockedId, mockedObject).mockValue;
    }
    public static long getLongMockValue(long mockedId, Object mockedObject) {
        return (long) mocks.find(mockedId, mockedObject).mockValue;
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
        public Object mockValue;

        public MockInfo(long mockedId, Object mockedObject, Object mockValue) {
            this.mockedId = mockedId;
            this.mockedObject = mockedObject;
            this.mockValue = mockValue;
        }

    }

    public static class MockInfoArrayWrapper {
        public MockInfo[] arr;
        public int size;

        MockInfoArrayWrapper() {
            arr = new MockInfo[10];
        }

        public MockInfo find(long mockId, Object mockedObject) {
            for (int i = 0; i < size; i++) {
                if (arr[i].mockedId == mockId && arr[i].mockedObject == mockedObject) {
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

}
