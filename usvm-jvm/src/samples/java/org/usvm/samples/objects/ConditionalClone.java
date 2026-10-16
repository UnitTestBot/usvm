package org.usvm.samples.objects;

public class ConditionalClone {
    public int arrays(int index) {
        if (index < 0 || index > 1) return -1;

        int[][] choices = {new int[]{3}, new int[]{7, 8}};
        int[] source = choices[index];
        int[] copy = source.clone();
        if (copy == source) return -2;

        copy[0] = 11;
        return source[0] * 100 + copy.length;
    }

    public int nullableArrays(int index) {
        if (index < 0 || index > 1) return -1;

        int[][] choices = {null, new int[]{7}};
        return choices[index].clone()[0];
    }

    public int objects(int index) throws CloneNotSupportedException {
        if (index < 0 || index > 1) return -1;

        Object payload = new Object();
        Item[] choices = {new Item(3, payload), new Item(7, payload)};
        Item source = choices[index];
        Item copy = source.copy();
        if (copy == source || copy.payload != source.payload) return -2;

        copy.value = 11;
        return source.value;
    }

    public static class Item implements Cloneable {
        public int value;
        public Object payload;

        public Item(int value, Object payload) {
            this.value = value;
            this.payload = payload;
        }

        public Item copy() throws CloneNotSupportedException {
            return (Item) super.clone();
        }
    }
}
