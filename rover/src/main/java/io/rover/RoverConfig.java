package io.rover;

import android.os.Build;

/**
 * Created by ata_n on 2016-04-19.
 */
public class RoverConfig {

    public static class Builder {

        private String mAppToken;
        private NotificationProvider mNotificationProvider;

        public Builder() {}

        public Builder setApplicationToken(String token) {
            mAppToken = token;
            return this;
        }

        public Builder setNotificationProvider(NotificationProvider provider) {
            mNotificationProvider = provider;
            return this;
        }

        public RoverConfig build() {
            return new RoverConfig(mAppToken, mNotificationProvider);
        }
    }

    String mAppToken;
    NotificationProvider mNotificationProvider;

    private RoverConfig(String appToken, NotificationProvider notificationProvider) {
        mAppToken = appToken;
        mNotificationProvider = notificationProvider;
    }
}
