package io.rover;

import android.os.Build;

/**
 * Created by ata_n on 2016-04-19.
 */
public class RoverConfig {

    public static class Builder {

        private String mAppToken;
        private String mProjectNum;

        public Builder() {}

        public Builder setApplicationToken(String token) {
            mAppToken = token;
            return this;
        }

        public Builder setProjectNumber(String number) {
            mProjectNum = number;
            return this;
        }

        public RoverConfig build() {
            return new RoverConfig(mAppToken, mProjectNum);
        }
    }

    String mAppToken;
    String mProjectNum;

    private RoverConfig(String appToken, String projectNum) {
        mAppToken = appToken;
        mProjectNum = projectNum;
    }
}
