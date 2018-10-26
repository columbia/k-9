package com.fsck.k9.message;


import java.util.List;

import com.fsck.k9.crypto.e3.E3Constants;
import com.fsck.k9.crypto.MessageCryptoStructureDetector;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.internet.MimeMessage;


public class ComposePgpEnableByDefaultDecider {
    public boolean shouldEncryptByDefault(Message localMessage) {
        return messageIsEncrypted(localMessage) && !messageIsE3Encrypted(localMessage);
    }

    private boolean messageIsEncrypted(Message localMessage) {
        List<Part> encryptedParts = MessageCryptoStructureDetector.findMultipartEncryptedParts(localMessage);
        return !encryptedParts.isEmpty();
    }

    private boolean messageIsE3Encrypted(Message localMessage) {
        return localMessage.isSet(Flag.E3)
                || (localMessage instanceof MimeMessage
                && localMessage.getHeader(E3Constants.MIME_E3_ENCRYPTED_HEADER).length > 0);
    }
}
