package com.fsck.k9.ui.crypto.smime;

import android.content.Context;
import android.content.Intent;

import com.fsck.k9.K9;
import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.internet.TextBody;
import com.fsck.k9.ui.crypto.MessageCryptoCallback;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.OutputStream;

import static com.fsck.k9.message.TestMessageConstructionUtils.bodypart;
import static com.fsck.k9.message.TestMessageConstructionUtils.messageFromBody;
import static com.fsck.k9.message.TestMessageConstructionUtils.multipart;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by mauzel on 12/11/2017.
 */
@SuppressWarnings("unchecked")
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 21)
public class MessageCryptoSMIMEHelperTest {
    private MessageCryptoSMIMEHelper messageCryptoHelper;
    private MessageCryptoCallback messageCryptoCallback;

    @Before
    public void setUp() throws Exception {
        messageCryptoHelper = new MessageCryptoSMIMEHelper(RuntimeEnvironment.application);
        messageCryptoCallback = mock(MessageCryptoCallback.class);
    }

    @Test
    public void testMultipartSMIME() throws Exception {
        Body encryptedBody = spy(new TextBody("encrypted data"));
        Message message = messageFromBody(
                bodypart("application/pkcs7-mime", encryptedBody)
        );
        message.setFrom(Address.parse("Test <test@example.org>")[0]);

        OutputStream outputStream = mock(OutputStream.class);

        messageCryptoHelper.asyncStartOrResumeProcessingMessage(message, messageCryptoCallback, null, false);

        verify(encryptedBody).writeTo(outputStream);
    }
}