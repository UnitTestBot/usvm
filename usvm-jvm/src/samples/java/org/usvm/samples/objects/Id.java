package org.usvm.samples.objects;

@SuppressWarnings("RedundantIfStatement")
public class Id {
    public int id;
    boolean isOne() {
        if (id == 1) {
            return true;
        }
        return false;
    }
}
