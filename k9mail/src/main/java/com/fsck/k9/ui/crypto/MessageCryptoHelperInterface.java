package com.fsck.k9.ui.crypto;

import com.fsck.k9.mail.Message;

/**
 * Created by mauzel on 12/8/2017.
 */

public interface MessageCryptoHelperInterface<T> {

    public boolean isConfiguredForOutdatedCryptoProvider();

    public void asyncStartOrResumeProcessingMessage(Message message, MessageCryptoCallback callback,
                                                    T cachedDecryptionResult, boolean processSignedOnly);
}
