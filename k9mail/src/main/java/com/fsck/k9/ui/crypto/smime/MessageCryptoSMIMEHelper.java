package com.fsck.k9.ui.crypto.smime;

import android.app.PendingIntent;
import android.content.Context;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import com.fsck.k9.crypto.MessageCryptoStructureDetector;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.e3.E3Utils;
import com.fsck.k9.mail.e3.smime.SMIMEDecryptFunction;
import com.fsck.k9.mail.e3.smime.SMIMEDecryptFunctionFactory;
import com.fsck.k9.mail.internet.MimeBodyPart;
import com.fsck.k9.mailstore.MessageHelper;
import com.fsck.k9.mailstore.MimePartStreamParser;
import com.fsck.k9.mailstore.SMIMECryptoResultAnnotation;
import com.fsck.k9.ui.crypto.MessageCryptoCallback;
import com.fsck.k9.ui.crypto.MessageCryptoHelperInterface;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import com.google.common.io.Closeables;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import timber.log.Timber;

/**
 * Loosely follows the same logic as {@link com.fsck.k9.ui.crypto.MessageCryptoHelper}.
 * <p>
 * Created by mauzel on 12/8/2017.
 */

public class MessageCryptoSMIMEHelper implements MessageCryptoHelperInterface<SMIMEMessageCryptoAnnotations, Parcelable> {

    private final Context context;
    private final Object callbackLock = new Object();
    private final Deque<SMIMEPart> partsToProcess = new ArrayDeque<>();

    @Nullable
    private MessageCryptoCallback<SMIMEMessageCryptoAnnotations> callback;

    private Message currentMessage;
    private SMIMEMessageCryptoAnnotations queuedResult;


    private SMIMEMessageCryptoAnnotations messageAnnotations;
    private SMIMEPart currentSMIMEPart;
    private State state;
    private boolean isCancelled;
    private boolean processSignedOnly;

    private Parcelable cachedDecryptionResult;

    private Function<Part, ByteSource> smimeDecrypt;

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
    public void asyncStartOrResumeProcessingMessage(Message message, MessageCryptoCallback<SMIMEMessageCryptoAnnotations> callback, Parcelable cachedDecryptionResult, boolean processSignedOnly) {
        if (this.currentMessage != null) {
            // TODO: Reattach callback?
        }

        this.messageAnnotations = new SMIMEMessageCryptoAnnotations();
        this.state = State.START;
        this.currentMessage = message;
        this.callback = callback;
        this.processSignedOnly = processSignedOnly;

        this.cachedDecryptionResult = cachedDecryptionResult;

        // TODO: Set an actual key entry
        this.smimeDecrypt = SMIMEDecryptFunctionFactory.get(new E3Utils(this.context), "", "");

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
            callbackReturnResult();
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

    /**
     * Note: Most SMIME emails will just have a single part.
     */
    private void findPartsForMultipartSMIMEPass() {
        List<Part> encryptedParts = MessageCryptoStructureDetector.findSMIMEParts(currentMessage);
        for (Part part : encryptedParts) {
            if (!MessageHelper.isCompletePartAvailable(part)) {
                addErrorAnnotation(part, SMIMECryptoResultAnnotation.CryptoError.SMIME_ENCRYPTED_BUT_INCOMPLETE, MessageHelper.createEmptyPart());
                continue;
            }
            if (MessageCryptoStructureDetector.isEnvelopedEncryptedSMIME(part)) {
                SMIMEPart cryptoPart = new SMIMEPart(SMIMEPartType.SMIME_ENCRYPTED, part);
                partsToProcess.add(cryptoPart);
                continue;
            }
            addErrorAnnotation(part, SMIMECryptoResultAnnotation.CryptoError.ENCRYPTED_BUT_UNSUPPORTED, MessageHelper.createEmptyPart());
        }
    }

    private void decryptOrVerifyCurrentPart() {
        try {
            switch (currentSMIMEPart.type) {
                case SMIME_ENCRYPTED: {
                    decryptSMIME();
                    return;
                }
                case SMIME_SIGNED: {
                    Timber.e("SMIME_SIGNED not yet implemented");
                    return;
                }
            }

            throw new IllegalStateException(("Unknown SMIME part type: " + currentSMIMEPart.type));
        } catch (MessagingException | IOException e) {
            Timber.e(e, "Exception when decrypting or verifying SMIME part");
        }
    }

    private void decryptSMIME() throws IOException, MessagingException {
        ByteSource decryptedBytes = Preconditions.checkNotNull(smimeDecrypt.apply(currentSMIMEPart.part));
        InputStream decryptedIn = null;
        try {
            decryptedIn = decryptedBytes.openStream();
            MimeBodyPart decryptedResult = MimePartStreamParser.parse(null, decryptedIn);
            SMIMECryptoResultAnnotation resultAnnotation = SMIMECryptoResultAnnotation.createSMIMEResultAnnotation(decryptedResult);

            // Now add result to things
            Part part = currentSMIMEPart.part;
            messageAnnotations.put(part, resultAnnotation);

            boolean currentPartIsFirstInQueue = partsToProcess.peekFirst() == currentSMIMEPart;

            if (!currentPartIsFirstInQueue) {
                throw new IllegalStateException(
                        "Trying to remove part from queue that is not the currently processed one!");
            }

            if (currentSMIMEPart != null) {
                partsToProcess.removeFirst();
                currentSMIMEPart = null;
            } else {
                Timber.e(new Throwable(), "Got to end of decryptSMIME() with no part in processing!");
            }

            nextStep();
        } finally {
            Closeables.closeQuietly(decryptedIn);
        }
    }

    private void callbackReturnResult() {
        synchronized (callbackLock) {
            partsToProcess.clear();

            queuedResult = messageAnnotations;
            messageAnnotations = null;

            deliverResult();
        }
    }

    // This method must only be called inside a synchronized(callbackLock) block!
    private void deliverResult() {
        if (isCancelled) {
            return;
        }

        if (callback == null) {
            Timber.d("Keeping crypto helper result in queue for later delivery");
            return;
        }

        if (queuedResult != null) {
            callback.onCryptoOperationsFinished(queuedResult);
        } else {
            throw new IllegalStateException("deliverResult() called with no result!");
        }
    }

    // TODO: de-duplicate this (also in MessageCryptoHelper)
    private void addErrorAnnotation(Part part, SMIMECryptoResultAnnotation.CryptoError error, MimeBodyPart replacementPart) {
        SMIMECryptoResultAnnotation annotation = SMIMECryptoResultAnnotation.createErrorAnnotation(error, replacementPart);
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
