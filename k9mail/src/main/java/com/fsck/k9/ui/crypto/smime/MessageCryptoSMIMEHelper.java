package com.fsck.k9.ui.crypto.smime;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import com.fsck.k9.crypto.MessageCryptoStructureDetector;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.internet.MimeBodyPart;
import com.fsck.k9.mailstore.CryptoResultAnnotation;
import com.fsck.k9.mailstore.CryptoResultAnnotation.CryptoError;
import com.fsck.k9.mailstore.MessageHelper;
import com.fsck.k9.ui.crypto.MessageCryptoAnnotations;
import com.fsck.k9.ui.crypto.MessageCryptoCallback;
import com.fsck.k9.ui.crypto.MessageCryptoHelperInterface;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Loosely follows the same logic as {@link com.fsck.k9.ui.crypto.MessageCryptoHelper}.
 *
 * Created by mauzel on 12/8/2017.
 */

public class MessageCryptoSMIMEHelper implements MessageCryptoHelperInterface<Parcelable> {

    private final Context context;
    private final Object callbackLock = new Object();
    private final Deque<SMIMEPart> partsToProcess = new ArrayDeque<>();

    @Nullable
    private MessageCryptoCallback callback;

    private Message currentMessage;
    private MessageCryptoAnnotations queuedResult;
    private PendingIntent queuedPendingIntent;


    private MessageCryptoAnnotations messageAnnotations;
    private SMIMEPart currentSMIMEPart;
    private Intent currentCryptoResult;
    private Intent userInteractionResultIntent;
    private State state;
    private boolean isCancelled;
    private boolean processSignedOnly;

    private Parcelable cachedDecryptionResult;

    public MessageCryptoSMIMEHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * S/MIME implementation does not use a crypto provider, so it's never outdated.
     *
     * @return Boolean false because it's never outdated.
     */
    @Override
    public boolean isConfiguredForOutdatedCryptoProvider() {
        return false;
    }

    @Override
    public void asyncStartOrResumeProcessingMessage(Message message, MessageCryptoCallback callback, Parcelable cachedDecryptionResult, boolean processSignedOnly) {
        if (this.currentMessage != null) {
            // TODO: Reattach callback?
        }

        this.messageAnnotations = new MessageCryptoAnnotations();
        this.state = State.START;
        this.currentMessage = message;
        this.callback = callback;
        this.processSignedOnly = processSignedOnly;

        this.cachedDecryptionResult = cachedDecryptionResult;

        nextStep();
    }

    private void nextStep() {
        if (isCancelled) {
            return;
        }

        while (state != State.FINISHED && partsToProcess.isEmpty()) {
            findPartsForNextPass();
        }

        if (state == State.FINISHED) {
            //callbackReturnResult();
            return;
        }

        currentSMIMEPart = partsToProcess.peekFirst();
        decryptOrVerifyCurrentPart();
    }

    private void findPartsForNextPass() {
        switch (state) {
            case START: {
                state = State.ENCRYPTION;

                findPartsForMultipartSMIMEPass();
                return;
            }

            case ENCRYPTION: {
                state = State.FINISHED;

                //findPartsForMultipartSignaturePass();
                //findPartsForEncryptionPass();
                return;
            }

            default: {
                throw new IllegalStateException("unhandled state");
            }
        }
    }

    private void findPartsForMultipartSMIMEPass() {
        List<Part> encryptedParts = MessageCryptoStructureDetector.findMultipartSMIMEParts(currentMessage);
        for (Part part : encryptedParts) {
            if (!MessageHelper.isCompletePartAvailable(part)) {
                addErrorAnnotation(part, CryptoError.OPENPGP_ENCRYPTED_BUT_INCOMPLETE, MessageHelper.createEmptyPart());
                continue;
            }
            if (MessageCryptoStructureDetector.isMultipartEncryptedOpenPgpProtocol(part)) {
                SMIMEPart cryptoPart = new SMIMEPart(SMIMEPartType.SMIME_ENCRYPTED, part);
                partsToProcess.add(cryptoPart);
                continue;
            }
            addErrorAnnotation(part, CryptoError.ENCRYPTED_BUT_UNSUPPORTED, MessageHelper.createEmptyPart());
        }
    }

    // TODO: de-duplicate this (also in MessageCryptoHelper)
    private void addErrorAnnotation(Part part, CryptoError error, MimeBodyPart replacementPart) {
        CryptoResultAnnotation annotation = CryptoResultAnnotation.createErrorAnnotation(error, replacementPart);
        messageAnnotations.put(part, annotation);
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
        SMIME_ENCRYPTED
    }

    private enum State {
        START, ENCRYPTION, FINISHED
    }
}
