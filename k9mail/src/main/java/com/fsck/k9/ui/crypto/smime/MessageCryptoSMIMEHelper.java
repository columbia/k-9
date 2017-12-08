package com.fsck.k9.ui.crypto.smime;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.Part;
import com.fsck.k9.ui.crypto.MessageCryptoAnnotations;
import com.fsck.k9.ui.crypto.MessageCryptoCallback;
import com.fsck.k9.ui.crypto.MessageCryptoHelper;
import com.fsck.k9.ui.crypto.MessageCryptoHelperInterface;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Created by mauzel on 12/8/2017.
 */

public class MessageCryptoSMIMEHelper implements MessageCryptoHelperInterface {

    private final Context context;
    private final Object callbackLock = new Object();
    private final Deque<SMIMEPart> partsToProcess = new ArrayDeque<>();

    @Nullable
    private MessageCryptoCallback callback;

    private Message currentMessage;
    private MessageCryptoAnnotations queuedResult;
    private PendingIntent queuedPendingIntent;


    private MessageCryptoAnnotations messageAnnotations;
    private Intent currentCryptoResult;
    private Intent userInteractionResultIntent;
    private State state;
    private boolean isCancelled;
    private boolean processSignedOnly;

    public MessageCryptoSMIMEHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public boolean isConfiguredForOutdatedCryptoProvider() {
        return false;
    }

    @Override
    public void asyncStartOrResumeProcessingMessage(Message message, MessageCryptoCallback callback, Parcelable cachedDecryptionResult, boolean processSignedOnly) {

    }

    private static class SMIMEPart {
        public final SMIMEPartType type;
        public final Part part;

        SMIMEPart(SMIMEPartType type, Part part) {
            this.type = type;
            this.part = part;
        }
    }


    private enum SMIMEPartType {
        SMIME_SIGNED,
        SMIME_SIGNATURE,
        SMIME_PART
    }

    private enum State {
        START, ENCRYPTION, SIGNATURES, FINISHED
    }
}
