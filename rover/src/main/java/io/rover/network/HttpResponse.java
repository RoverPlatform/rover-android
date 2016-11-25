package io.rover.network;

import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by chrisrecalis on 2016-11-25.
 */

public class HttpResponse {

    private InputStreamReader mBody;
    private int mStatus = 0;

    public HttpResponse() {

    }

    public InputStreamReader getBody() {
        return mBody;
    }

    public int getStatus() {
        return mStatus;
    }

    public boolean isSuccessful() {
        return mStatus >= 200 && mStatus < 300;
    }

    public void setBody(InputStreamReader body) {
        this.mBody = body;
    }

    public void setStatus(int status) {
        this.mStatus = status;
    }

    public void release() {
        if (mBody != null) {
            try {
                mBody.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
