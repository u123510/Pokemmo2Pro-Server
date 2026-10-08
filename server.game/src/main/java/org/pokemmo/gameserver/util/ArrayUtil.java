package org.pokemmo.gameserver.util;

public class ArrayUtil {
    public static short[] toShortPrimitive(Short[] array) {
        short[] primitiveArray = new short[array.length];
        for (int i = 0; i < array.length; i++) {
            primitiveArray[i] = array[i];
        }
        return primitiveArray;
    }
    public static Short[] toShortObject(short[] array) {
        Short[] objectArray = new Short[array.length];
        for (int i = 0; i < array.length; i++) {
            objectArray[i] = array[i];
        }
        return objectArray;
    }
    public static boolean[] toBooleanPrimitive(Boolean[] array) {
        boolean[] primitiveArray = new boolean[array.length];
        for (int i = 0; i < array.length; i++) {
            primitiveArray[i] = array[i];
        }
        return primitiveArray;
    }
    public static Boolean[] toBooleanObject(boolean[] array) {
        Boolean[] objectArray = new Boolean[array.length];
        for (int i = 0; i < array.length; i++) {
            objectArray[i] = array[i];
        }
        return objectArray;
    }
    public static int[] toIntPrimitive(Integer[] array) {
        int[] primitiveArray = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            primitiveArray[i] = array[i];
        }
        return primitiveArray;
    }
    public static Integer[] toIntObject(int[] array) {
        Integer[] objectArray = new Integer[array.length];
        for (int i = 0; i < array.length; i++) {
            objectArray[i] = array[i];
        }
        return objectArray;
    }
    public static long[] toLongPrimitive(Long[] array) {
        long[] primitiveArray = new long[array.length];
        for (int i = 0; i < array.length; i++) {
            primitiveArray[i] = array[i];
        }
        return primitiveArray;
    }
    public static Long[] toLongObject(long[] array) {
        Long[] objectArray = new Long[array.length];
        for (int i = 0; i < array.length; i++) {
            objectArray[i] = array[i];
        }
        return objectArray;
    }
}
