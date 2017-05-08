package io.rover.util;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Created by Rover Labs Inc. on 2017-05-08.
 */

public class DataUriTest {



    @Test
    public void missing_content_type() throws Exception {
        DataUri uri = new DataUri("data:,a");

        assertEquals("Content Type is empty", null, uri.getContentType());
    }

    @Test
    public void missing_content_encoding() throws Exception {

        DataUri uri = new DataUri("data:text/plain,a");

        assertEquals("Encoding Type is base64", null, uri.getEncodingType());
    }

    @Test
    public void missing_content_type_and_content_encoding() throws Exception {

        DataUri uri = new DataUri("data:,a");

        assertEquals("Content Type is empty", null, uri.getContentType());
        assertEquals("Encoding Type is base64", null, uri.getEncodingType());
    }

    @Test
    public void simple_text_data() throws Exception {

        String data = "Hello%2C%20World!";

        DataUri uri = new DataUri("data:," + data);

        assertEquals("Content Type is empty", null, uri.getContentType());
        assertEquals("Encoding Type is empty", null, uri.getEncodingType());
        assertEquals("Data is pulled out", uri.getData(), data);
    }

    @Test
    public void simple_base64_encoded_text_data() throws Exception {
        String data = "SGVsbG8sIFdvcmxkIQ%3D%3D";

        DataUri uri = new DataUri("data:text/plain;base64," + data);

        assertEquals("Content Type is text/plain", "text/plain", uri.getContentType());
        assertEquals("Encoding Type is base64", "base64", uri.getEncodingType());
        assertEquals("Data is pulled out", data, uri.getData());

    }

    @Test
    public void base64_encoded_image() throws Exception {
        String data = "iVBORw0KGgoAAAANSUhEUgAAAL8AAACbCAMAAAAECboUAAAAHnRFWHRTb2Z0d2FyZQBid2lwLWpzLm1ldGFmbG9vci5jb21Tnbi0AAAABlBMVEUAAAAAAAClZ7nPAAAAAnRSTlMA/1uRIrUAAACuSURBVHic7c5BCsAgDEVBvf+lpQThq922IMyDKsUQprXL672+55x3nvVe5XS+rZvO6ZxYzzl9GnL3+9++n5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn/97vyRJ+r0BAyY2ARTTGZMAAAAASUVORK5CYII=";

        DataUri uri = new DataUri("data:;base64," + data);

        assertEquals("Content Type is null", null, uri.getContentType());
        assertEquals("Encoding Type is base64", "base64", uri.getEncodingType());
        assertEquals("Data is pulled out", data, uri.getData());
    }

}
