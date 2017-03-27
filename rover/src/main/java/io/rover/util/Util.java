package io.rover.util;

import java.util.Arrays;


/**
 * Created by Rover Labs Inc. on 2017-03-27.
 */

public class Util {

    public static <T> T[] concatArrays(T[] first, T[] second) {
        if (first == null)
            return second;

        if (second == null)
            return  first;

        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static <T> T[] removeElementFromArray(T[] array, T elementToRemove) {

        if (array == null)
            return null;

        int totalOccurrences = countOccurrences(array, elementToRemove);

        if (totalOccurrences == 0)
            return array;


        T[] result = Arrays.copyOf(array, array.length - totalOccurrences);

        int position = 0;

        for (T element : array) {
            if (!element.equals(elementToRemove)) {
                result[position] = element;
                position++;
            }
        }

        return result;
    }

    public static <T> int countOccurrences(T[] array, T elementToCount) {

        if (array == null)
            return 0;

        int count = 0;

        for (T element : array) {
            if (element.equals(elementToCount)) {
                count++;
            }
        }

        return count;
    }

    public static <T> T[] subtractArrays(T[] first, T[] second) {
        if (first == null)
            return second;

        if (second == null)
            return first;

        for (T elementToRemove : second) {
            first = removeElementFromArray(first, elementToRemove);
        }

        return first;
    }

    public static <T> T[] uniqueArray(T[] array) {

        if (array == null)
            return null;

        T[] sortedArray = Arrays.copyOf(array, array.length);

        Arrays.sort(sortedArray);

        int distinctCount = 0;

        T previous = null;

        for (T element : sortedArray) {
            if (previous == null || !previous.equals(element)) {
                distinctCount++;
            }

            previous = element;
        }

        T[] distinctArray = Arrays.copyOf(array, distinctCount);

        previous = null;
        int position = 0;

        for (T element : sortedArray) {
            if (previous == null || !previous.equals(element)) {
                distinctArray[position] = element;
                position++;
            }

            previous = element;
        }


        return distinctArray;
    }

}
