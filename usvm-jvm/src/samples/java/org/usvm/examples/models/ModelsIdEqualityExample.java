package org.usvm.examples.models;

import static org.usvm.api.mock.UMockKt.assume;
import org.usvm.examples.objects.SimpleDataClass;

import java.util.List;

public class ModelsIdEqualityExample {
    public ObjectWithRefFieldClass objectItself(ObjectWithRefFieldClass obj) {
        assume(obj != null);
        return obj;
    }

    public SimpleDataClass refField(ObjectWithRefFieldClass obj) {
        assume(obj != null && obj.refField != null);
        return obj.refField;
    }

    public int[] arrayField(ObjectWithRefFieldClass obj) {
        assume(obj != null && obj.arrayField != null);
        return obj.arrayField;
    }

    public int[] arrayItself(int[] array) {
        assume(array != null);
        return array;
    }

    public int[] subArray(int[][] array) {
        assume(array != null && array.length == 1 && array[0] != null);
        return array[0];
    }

    public Integer[] subRefArray(Integer[][] array) {
        assume(array != null && array.length == 1 && array[0] != null);
        return array[0];
    }

    public List<Integer> wrapperExample(List<Integer> list) {
        assume(list != null);
        return list;
    }

    public SimpleDataClass objectFromArray(SimpleDataClass[] array) {
        assume(array != null && array.length == 1 && array[0] != null);
        return array[0];
    }

    public SimpleDataClass staticSetter(SimpleDataClass obj) {
        assume(obj != null);
        SimpleDataClass.staticField = obj;
        return SimpleDataClass.staticField;
    }
}
