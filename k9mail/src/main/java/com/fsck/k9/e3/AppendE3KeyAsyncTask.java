package com.fsck.k9.e3;

import android.app.PendingIntent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.fsck.k9.Account;
import com.fsck.k9.K9;
import com.fsck.k9.R;
import com.fsck.k9.activity.misc.Attachment;
import com.fsck.k9.controller.MessagingControllerCommands.PendingAppend;
import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.e3.E3Constants;
import com.fsck.k9.mail.e3.E3Key;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mailstore.LocalMessage;
import com.fsck.k9.mailstore.LocalStore;
import com.fsck.k9.message.MessageBuilder;
import com.fsck.k9.message.MessageBuilder.Callback;
import com.fsck.k9.message.SimpleMessageBuilder;
import com.fsck.k9.message.SimpleMessageFormat;

import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * <p>
 * Created on 12/12/2017.
 *
 * @author koh
 */

public class AppendE3KeyAsyncTask extends AsyncTask<E3Key, Void, Void> {
    private final Resources res;
    private final Account account;
    private final String e3BackupFolder;
    private final File e3File;
    private final String mimeType;

    public static final String PFX_MIME_TYPE = "application/x-pkcs12";
    public static final String PEM_MIME_TYPE = "application/x-pem-file";
    public static final String X509_MIME_TYPE = "application/x-x509-ca-cert";

    public AppendE3KeyAsyncTask(final Resources res, final Account account, final String
            e3BackupFolder, final File e3File, final String mimeType) {
        this.res = res;
        this.account = account;
        this.e3BackupFolder = e3BackupFolder;
        this.e3File = e3File;
        this.mimeType = mimeType;
    }

    @Override
    protected Void doInBackground(final E3Key... e3Keys) {
        for (final E3Key e3Key : e3Keys) {
            final MessageBuilder builder = createE3BackupEmailBuilder(e3Key);

            builder.buildAsync(new Callback() {
                @Override
                public void onMessageBuildSuccess(final MimeMessage message, final boolean
                        isDraft) {
                    try {
                        final LocalStore local = account.getLocalStore();
                        final Folder<LocalMessage> e3Folder = local.getFolder(e3BackupFolder);

                        message.addHeader(E3Constants.E3_NAME_HEADER, e3Key.getKeyName());
                        message.addHeader(E3Constants.E3_DIGEST_HEADER, e3Key.getSHA256Digest());
                        message.setFlag(Flag.E3_KEY, true);

                        e3Folder.appendMessages(Collections.singletonList(message));

                        final PendingAppend command = PendingAppend.create(e3Folder.getName(), message.getUid());
                        local.addPendingCommand(command);

                        Log.d(E3Constants.LOG_TAG, "Appended E3 key");
                    } catch (final MessagingException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void onMessageBuildCancel() {

                }

                @Override
                public void onMessageBuildException(final MessagingException exception) {
                    Log.e(E3Constants.LOG_TAG, "Failed to build E3 key backup message", exception);
                }

                @Override
                public void onMessageBuildReturnPendingIntent(final PendingIntent pendingIntent,
                                                              final int requestCode) {

                }
            });
        }

        return null;
    }

    private MessageBuilder createE3BackupEmailBuilder(final E3Key e3Key) {
        final MessageBuilder builder = SimpleMessageBuilder.newInstance();
        final Address selfAddr = new Address(account.getEmail());
        final Attachment pfxAttachment = getE3KeyAsAttachment(mimeType);
        final List<Attachment> attachments = Collections.singletonList(pfxAttachment);

        builder.setSubject(res.getString(R.string.account_setup_e3_email_subject, e3Key
                .getKeyName())) //
                .setText(res.getString(R.string.account_setup_e3_email_text, e3Key.getKeyName(),
                        e3Key.getSHA256Digest())) //
                .setAttachments(attachments) //
                .setSentDate(new Date()) //
                .setHideTimeZone(K9.hideTimeZone()) //
                .setTo(Collections.singletonList(selfAddr)) //
                .setIdentity(account.getIdentity(0)) //
                .setMessageFormat(SimpleMessageFormat.HTML) //
                .setIsPgpInlineEnabled(false).setDraft(false);

        return builder;
    }

    private Attachment getE3KeyAsAttachment(final String mimeType) {
        final Uri pfxUri = Uri.fromFile(e3File);

        return Attachment.createAttachment(pfxUri, 1, mimeType) //
                .deriveWithMetadataLoaded(mimeType, e3File.getName(), e3File.length()) //
                .deriveWithLoadComplete(e3File.getAbsolutePath());
    }
}
