package com.fsck.k9.ui.crypto;

import com.fsck.k9.mail.Message;

/**
 * Created by mauzel on 12/8/2017.
 */

public interface MessageCryptoHelperInterface<T, S> {

    public boolean isConfiguredForOutdatedCryptoProvider();

    public void asyncStartOrResumeProcessingMessage(Message message, MessageCryptoCallback<T> callback,
                                                    S cachedDecryptionResult, boolean processSignedOnly);
}
