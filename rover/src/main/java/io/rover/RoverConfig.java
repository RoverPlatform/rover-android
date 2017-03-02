package io.rover;

import android.app.Activity;
import android.os.Build;

/**
 * Created by ata_n on 2016-04-19.
 */
public class RoverConfig {

    public static class Builder {

        private String mAppToken;
        private NotificationProvider mNotificationProvider;
        private Class mExperienceActivity;

        public Builder() {}

        public Builder setApplicationToken(String token) {
            mAppToken = token;
            return this;
        }

        public Builder setNotificationProvider(NotificationProvider provider) {
            mNotificationProvider = provider;
            return this;
        }

        public Builder setExperienceActivity(Class<? extends Activity> klass) {
            mExperienceActivity = klass;
            return this;
        }

        public RoverConfig build() {
            return new RoverConfig(mAppToken, mNotificationProvider, mExperienceActivity);
        }
    }

    String mAppToken;
    NotificationProvider mNotificationProvider;
    Class mExperienceActivity;

    private RoverConfig(String appToken, NotificationProvider notificationProvider, Class<? extends Activity> klass) {
        mAppToken = appToken;
        mNotificationProvider = notificationProvider;
        mExperienceActivity = klass;
    }
}
