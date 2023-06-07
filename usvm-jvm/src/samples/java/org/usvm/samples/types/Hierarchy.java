package org.usvm.samples.types;

import org.jetbrains.annotations.NotNull;

public class Hierarchy {
    public static class Base1 {
    }

    public static class Base2 {
    }

    public interface Interface1 {
    }

    public interface Interface2 {
    }

    public interface DerivedInterface12 extends Interface1, Interface2 {
    }

    public static class Derived1A extends Base1 implements Interface1 {

    }

    public static class Derived1B extends Base1 implements Interface2 {
    }

    public static class DerivedMulti extends Base2 implements DerivedInterface12 {
    }

    public static class UserComparable implements Interface2, Comparable<UserComparable> {
        @Override
        public int compareTo(@NotNull UserComparable o) {
            return 0;
        }
    }
}
