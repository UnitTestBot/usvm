package example;

import java.util.Objects;

public class B implements Comparable<B> {

    public int f;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        B b = (B) o;
        return f == b.f;
    }

    @Override
    public int hashCode() {
        return Objects.hash(f);
    }

    @Override
    public int compareTo(B b) {
        return Integer.compare(f, b.f);
    }
}