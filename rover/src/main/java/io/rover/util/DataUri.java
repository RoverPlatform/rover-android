package io.rover.util;

import java.net.MalformedURLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Rover Labs Inc. on 2017-05-08.
 */

public class DataUri {

    private static final Pattern dataUriPatter = Pattern.compile("^(data)\\:([\\w\\/\\+]*)(\\;(base64)){0,1}\\,(.*)");

    private String mContentType;
    private String mEncodingType;
    private String mData;

    public static boolean isDataUri(String uri) {
        if (uri == null) {
            return false;
        }

        Matcher matcher = dataUriPatter.matcher(uri);

        return matcher.matches();
    }


    public DataUri(String uri) throws MalformedURLException {
        Matcher matcher = dataUriPatter.matcher(uri);

        if (!matcher.matches()) {
            throw new MalformedURLException("Invalid data uri");
        }

        mContentType = emptyAsNull(matcher.group(2));
        mEncodingType = emptyAsNull(matcher.group(4));
        mData = emptyAsNull(matcher.group(5));

    }

    public String getContentType() {
        return mContentType;
    }

    public String getEncodingType() {
        return mEncodingType;
    }

    public String getData() {
        return mData;
    }

    /* Private */

    private String emptyAsNull(String string) {
        if (string !=  null && string.isEmpty()) {
            return null;
        }

        return string;
    }
}
