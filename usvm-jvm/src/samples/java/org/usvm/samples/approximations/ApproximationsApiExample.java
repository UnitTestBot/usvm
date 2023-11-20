package org.usvm.samples.approximations;

public class ApproximationsApiExample {
    public static int symbolicList(TestList<Integer> list) {
        if (list.size() < 10) {
            return 0;
        }

        if (list.get(3) == 5) {
            return 1;
        }

        if (list.get(2) == 7) {
            return 2;
        }

        return 3;
    }

    public static int symbolicMap(TestMap<String, Integer> map) {
        if (map.size() < 10) {
            return 0;
        }

        if (!map.containsKey("abc")) {
            return 1;
        }

        int value = map.get("abc");
        if (value != 5) {
            return 2;
        }

        TestMap<String, Integer> other = new TestMap<>();
        other.put("abc", 7);
        other.put("xxx", 17);
        other.putAll(map);


        if (!map.containsKey("abc")) {
            // unreachable
            return 3;
        }

        value = map.get("abc");
        if (value != 5) {
            // unreachable
            return 4;
        }

        if (!other.containsKey("xxx")) {
            // unreachable
            return 5;
        }

        value = other.get("xxx");

        if (value == 17) {
            return 6;
        } else {
            return 7;
        }
    }
}
