package com.fsck.k9.ui.crypto;

import android.os.Parcelable;

import com.fsck.k9.mail.Message;

/**
 * Created by mauzel on 12/8/2017.
 */

public interface MessageCryptoHelperInterface {

    public boolean isConfiguredForOutdatedCryptoProvider();

    public void asyncStartOrResumeProcessingMessage(Message message, MessageCryptoCallback callback,
                                                    Parcelable cachedDecryptionResult, boolean processSignedOnly);
}
