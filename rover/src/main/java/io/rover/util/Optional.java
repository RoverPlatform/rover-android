package io.rover.util;

import android.support.annotation.Nullable;

/**
 * Created by chrisrecalis on 2016-10-13.
 */
public class Optional<T> {

    T mValue = null;
    boolean mValueSet = false;

    public void set(T value) {
        mValue = value;
        mValueSet = true;
    }

    public Boolean hasBeenSet() {
        return mValueSet;
    }

    public Object getOrElse(T defaultValue) {
        if (hasBeenSet()) {
            return mValue;
        } else {
            return defaultValue;
        }
    }

    public T get() {
        return mValue;
    }

    public void clear() {
        mValue = null;
        mValueSet = false;
    }

}
