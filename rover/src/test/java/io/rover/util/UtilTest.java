package io.rover.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by Rover Labs Inc. on 2017-03-27.
 */
public class UtilTest {

    @Test
    public void concatArrays_Strings() throws Exception {
        String[] array1 = new String[] { "a", "b" };
        String[] array2 = new String[] { "c", "d" };

        assertArrayEquals(Util.concatArrays(array1, array2), new String[] { "a", "b", "c", "d" });
    }

    @Test
    public void concatArrays_Ints() throws  Exception {
        Integer[] array1 = new Integer[] { 1, 2 };
        Integer[] array2 = new Integer[] { 3, 4 };

        assertArrayEquals(Util.concatArrays(array1, array2), new Integer[] { 1, 2, 3, 4 });
    }

    @Test
    public void concatArrays_Null_arrays() throws Exception {

        String[] array1 = new String[] { "a", "b" };

        assertArrayEquals(Util.concatArrays(array1, null), new String[] { "a", "b" });

        String[] array2 = new String[] { "a", "b" };

        assertArrayEquals(Util.concatArrays(array2, null), new String[] { "a", "b" });

        assertArrayEquals(Util.concatArrays(null, null), null);
    }

    @Test
    public void removeElementFromArray_containing_element() throws Exception {
        String[] array1 = new String[] { "a", "b" };

        assertArrayEquals(Util.removeElementFromArray(array1, "b"), new String[] { "a" });
    }

    @Test
    public void removeElementFromArray_not_containing_element() throws Exception {
        String[] array1 = new String[] { "a", "b" };

        assertArrayEquals(Util.removeElementFromArray(array1, "c"), new String[] { "a", "b" });
    }

    @Test
    public void countOccurrences_containing_element() throws Exception {
        String[] array1 = new String[] { "a", "a", "b" };

        assertEquals(Util.countOccurrences(array1, "a"), 2);
    }


    @Test
    public void countOccurrences_not_containing_element() throws Exception {
        String[] array1 = new String[] { "a", "b" };

        assertEquals(Util.countOccurrences(array1, "c"), 0);
    }

    @Test
    public void subtractArrays_string_arrays() throws Exception {
        String[] array1 = new String[] { "a", "b", "c" };
        String[] array2 = new String[] { "b", "c" };

        assertArrayEquals(Util.subtractArrays(array1, array2), new String[] { "a" });
    }

    @Test
    public void subtractArrays_null_arrays() throws Exception {
        String[] array1 = new String[] { "a", "b", "c" };

        assertArrayEquals(Util.subtractArrays(array1, null), new String[] { "a", "b", "c" });

        assertArrayEquals(Util.subtractArrays(null, array1), null);
    }

    @Test
    public void uniqueArray_string_arrays() throws Exception {
        String[] array1 = new String[] { "a", "a", "a" };

        assertArrayEquals(Util.uniqueArray(array1), new String[] { "a" });
    }

    @Test
    public void uniqueArray_string_arrays_already_unique() throws Exception {
        String[] array1 = new String[] { "a", "b", "c", "d" };

        assertArrayEquals(Util.uniqueArray(array1), new String[] { "a", "b", "c", "d" });
    }

    @Test
    public void uniqueArray_null_arrays() throws Exception {
        assertArrayEquals(Util.uniqueArray(null), null);
    }

}