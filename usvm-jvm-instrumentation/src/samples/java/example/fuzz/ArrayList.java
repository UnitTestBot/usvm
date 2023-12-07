package example.fuzz;

public class ArrayList {

    public static int example(java.util.ArrayList<Integer> arr) {
        if (arr.size() == 3) {
            return -1;
        }
        if (arr.size() == 2 && arr.get(0) == 0) {
            return 1;
        }
        return 0;
    }
}
