package io.rover;

import android.app.Activity;

/**
 * Created by Rover Labs Inc on 2016-04-19.
 */
public class RoverConfig {

    public static class Builder {

        private String mAppToken;
        private NotificationProvider mNotificationProvider;
        private Class mExperienceActivity;

        // Rover will attempt to use 1/10th of available memory for caching images
        private int mImageCacheSize = (int) (Runtime.getRuntime().maxMemory() / 1024) / 10;

        public Builder() {}

        public Builder setApplicationToken(String token) {
            mAppToken = token;
            return this;
        }

        public Builder setNotificationProvider(NotificationProvider provider) {
            mNotificationProvider = provider;
            return this;
        }

        public Builder setExperienceActivity(Class<? extends ExperienceActivity> klass) {
            mExperienceActivity = klass;
            return this;
        }

        public Builder setMaximumImageCacheSize(int kilobytes) {
            mImageCacheSize = kilobytes;
            return this;
        }

        public RoverConfig build() {

            return new RoverConfig(mAppToken, mNotificationProvider, mExperienceActivity, mImageCacheSize);
        }
    }

    String mAppToken;
    NotificationProvider mNotificationProvider;
    Class mExperienceActivity;
    int mImageCacheSize;

    private RoverConfig(String appToken, NotificationProvider notificationProvider, Class<? extends ExperienceActivity> klass, int imageCacheSize) {
        mAppToken = appToken;
        mNotificationProvider = notificationProvider;
        mExperienceActivity = klass;
        mImageCacheSize = imageCacheSize;
    }

    public int getImageCacheSize() {
        return mImageCacheSize;
    }
}
