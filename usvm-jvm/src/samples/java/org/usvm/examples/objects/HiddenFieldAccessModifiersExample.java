package org.usvm.examples.objects;

public class HiddenFieldAccessModifiersExample {
    public boolean checkSuperFieldEqualsOne(HiddenFieldAccessModifiersSucc b) {
        return b.getF() == 1;
    }
}
