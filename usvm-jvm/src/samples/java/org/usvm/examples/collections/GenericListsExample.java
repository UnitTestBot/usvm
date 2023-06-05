package org.usvm.examples.collections;

import static org.usvm.api.mock.UMockKt.assume;

import java.util.ArrayList;
import java.util.List;

public class GenericListsExample<T extends Long> {
    List<Long> listOfListsOfT(List<List<T>> value) {
        List<Long> result = new ArrayList<>();

        for (List<T> numbers : value) {
            long acc = 0;
            for (T number : numbers) {
                acc += number.longValue();
            }

            result.add(acc);
        }

        assume(result.size() > 1 && result.size() < 4);
        assume(result.get(0) > 15 && result.get(1) > 20);

        return result;
    }

    List<? extends Comparable<?>> listOfComparable(List<? extends Comparable<?>> value) {
        assume(value != null && value.size() > 1 && value.get(0) != null);

        return value;
    }

    List<T> listOfT(List<T> value) {
        assume(value != null && value.size() >= 2 && value.get(0) != null);

        return value;
    }

    List<T[][]> listOfTArray(List<T[][]> value) {
        assume(value != null && value.size() >= 2 && value.get(0) != null && value.get(0).length >= 1);

        return value;
    }

    List<? extends T[][]> listOfExtendsTArray(List<? extends T[][]> value) {
        assume(value != null && value.size() >= 2 && value.get(0) != null && value.get(0).length >= 1);

        return value;
    }

    List<? extends int[]> listOfPrimitiveArrayInheritors(List<? extends int[]> value) {
        assume(value != null && value.size() >= 2 && value.get(0) != null && value.get(0).length >= 1);

        return value;
    }

    @SuppressWarnings("IfStatementWithIdenticalBranches")
    List<? extends Number> wildcard(List<? extends Number> list) {
        if (list == null) {
            return new ArrayList<Integer>();
        }

        if (list.size() == 1 && list.get(0) != null) {
            return list;
        }

        return list;
    }

    List<List<Integer>> listOfLists(List<List<Integer>> value) {
        assume(value != null && !value.isEmpty() && value.get(0) != null && !value.get(0).isEmpty());

        return value;
    }

    @SuppressWarnings("IfStatementWithIdenticalBranches")
    List<?> wildcardWithOnlyQuestionMark(List<?> list) {
        if (list == null) {
            return new ArrayList<Integer>();
        }

        if (list.size() == 1) {
            return list;
        }

        return list;
    }

    List<long[]> genericWithArrayOfPrimitives(List<long[]> value) {
        assume(value != null && value.size() >= 2 && value.get(0) != null && value.get(0).length >= 1 && value.get(0)[0] != 0);

        return value;
    }

    List<Long> genericWithObject(List<Long> value) {
        assume(value != null && value.size() >= 2 && value.get(0) != null);

        return value;
    }

    List<Long[][]> genericWithArrayOfArrays(List<Long[][]> value) {
        assume(value != null && value.size() >= 2 && value.get(0) != null && value.get(0).length >= 1 && value.get(0)[0] != null);

        return value;
    }

}
