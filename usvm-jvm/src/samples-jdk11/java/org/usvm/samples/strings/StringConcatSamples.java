package org.usvm.samples.strings;

public class StringConcatSamples {
    public static class Bar {
        @Override
        public String toString() {
            return "Bar";
        }
    }

    public boolean stringConcatEq() {
        Bar bar = new Bar();
        int intValue = 17;
        boolean boolValue = false;
        String concatenated = "prefix_" + intValue + "_" + boolValue + "_" + bar + "_suffix";
        String expected = "prefix_17_false_Bar_suffix";
        return expected.equals(concatenated);
    }

    public boolean stringConcatStrangeEq() {
        int iv = 0;
        String expected = "\u0000" + 0 + "#" + 0 + "\u0001" + 0 + "!\u0002" + 0 + "@\u0012\t";
        String concatenated = "\u0000" + iv + "#" + iv + "\u0001" + iv + "!\u0002" + iv + "@\u0012\t";
        return expected.equals(concatenated);
    }
}
