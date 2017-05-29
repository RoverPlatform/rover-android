package io.rover;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;

import io.rover.model.Message;

/**
 * Created by Rover Labs Inc on 2017-01-30.
 */

public class MessageInteractionService extends IntentService {

    public enum Type {
        OPEN, DELETE
    }

    public enum Source {
        INBOX, NOTIFICATION
    }

    public MessageInteractionService() {
        super("MessageInteractionService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Type type = (Type) intent.getSerializableExtra("type");
        Source source = (Source) intent.getSerializableExtra("source");
        Message message = intent.getParcelableExtra("message");
        PendingIntent launchIntent = intent.getParcelableExtra("launch-intent");

        if (source == Source.NOTIFICATION) {
            if (type == Type.OPEN) {
                Rover.didOpenNotificationWithMessage(message);
            } else {
                Rover.didDeleteNotificationWithMessage(message);
            }
        } else {
            if (type != Type.DELETE) {
                Rover.didOpenMessage(message);
            }
        }

        if (launchIntent != null) {

            try {
                launchIntent.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }

        }
    }
}
