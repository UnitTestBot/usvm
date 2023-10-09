package org.usvm.samples;

public class TraceSample {
    public interface TraceSampleI {
        int method1(int arg);

        void codeBefore();

        void codeAfter();
    }

    void entryPoint() {
        TraceSampleI traceSample = mkTraceSample();
        traceSample.codeBefore();
        traceSample.method1(1);
        traceSample.codeAfter();
    }

    public TraceSampleI mkTraceSample() {
        return new TraceSampleImpl();
    }

    public static class TraceSampleImpl implements TraceSampleI {
        public int method1(int arg) {
            codeBefore();
            int result = arg + arg;
            if (result == 2) {
                throw new IllegalStateException();
            }
            codeAfter();
            return result;
        }

        public void codeBefore() {
            deepCode11();
        }

        public void codeAfter() {
            deepCode11();
        }

        int deepCode11() {
            return 17 + deepCode10() + 18;
        }

        int deepCode10() {
            return 17 + deepCode9() + 18;
        }

        int deepCode9() {
            return 17 + deepCode8() + 18;
        }

        int deepCode8() {
            return 17 + deepCode7() + 18;
        }

        int deepCode7() {
            return 17 + deepCode6() + 18;
        }

        int deepCode6() {
            return 17 + deepCode5() + 18;
        }

        int deepCode5() {
            return 17 + deepCode4() + 18;
        }

        int deepCode4() {
            return 17 + deepCode3() + 18;
        }

        int deepCode3() {
            return 17 + deepCode2() + 18;
        }

        int deepCode2() {
            return 17 + deepCode1() + 18;
        }

        int deepCode1() {
            return 17 + deepCode0() + 18;
        }

        int deepCode0() {
            Object x = new Object();
            if (x.hashCode() > 0) {
                return deepCode11();
            }
            return x.hashCode();
        }
    }
}
