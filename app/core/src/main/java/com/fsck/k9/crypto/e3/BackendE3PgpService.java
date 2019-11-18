package com.fsck.k9.crypto.e3;

import android.content.Context;
import androidx.annotation.NonNull;

import com.fsck.k9.Account;
import com.fsck.k9.backend.api.EncryptSyncListener;
import com.fsck.k9.backend.api.SyncUpdatedListener;
import com.fsck.k9.controller.MessagingController;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mailstore.LocalFolder;
import com.fsck.k9.mailstore.LocalStore;

import org.openintents.openpgp.IOpenPgpService2;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;
import org.openintents.openpgp.util.OpenPgpServiceConnection.OnBound;

import timber.log.Timber;

/**
 * TODO: E3 refactor this and its use in ImapSync.
 */
public class BackendE3PgpService implements EncryptSyncListener<Message> {

    private final Context context;
    private final Account account;
    private final String cryptoProvider;
    private final Long keyId;

    public BackendE3PgpService(final Context context, final Account account, final String cryptoProvider, final Long keyId) {
        this.context = context;
        this.account = account;
        this.cryptoProvider = cryptoProvider;
        this.keyId = keyId;
    }

    @Override
    public void asyncEncryptSync(final Message message, @NonNull final SyncUpdatedListener listener) {
        final String[] accountEmail = new String[]{account.getIdentity(0).getEmail()};
        final OpenPgpServiceConnection serviceConnection = new OpenPgpServiceConnection(context, cryptoProvider, new OnBound() {
            @Override
            public void onBound(IOpenPgpService2 service) {
                final LocalFolder localFolder;
                try {
                    final LocalStore localStore = account.getLocalStore();
                    localFolder = localStore.getFolder(message.getFolder().getName());
                    localFolder.open(Folder.OPEN_MODE_RW);
                } catch (MessagingException e) {
                    throw new RuntimeException("Failed to open local folder!", e);
                }

                final OpenPgpApi openPgpApi = new OpenPgpApi(context, service);
                final SimpleE3PgpEncryptor encryptor = new SimpleE3PgpEncryptor(openPgpApi, keyId);

                // Thread needed to prevent this from running on the UI thread
                final Thread encryptThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String emailToken = null;
                            if (message.getHeaderNames().contains(E3Constants.MIME_STUDY_EMAIL_TOKEN)) {
                                emailToken = message.getHeader(E3Constants.MIME_STUDY_EMAIL_TOKEN)[0];
                            }
                            final MimeMessage encryptedMimeMessage = encryptor.encrypt((MimeMessage) message, accountEmail);
                            encryptedMimeMessage.setHeader(E3Constants.MIME_E3_ENCRYPTED_HEADER, accountEmail[0]);

                            MessagingController.getInstance(context).replaceExistingMessage(account, localFolder, message, encryptedMimeMessage, listener);

                            // Record that we encrypted this email for the email study
                            if (emailToken != null) {
                                final EmailStudyHelper helper = new EmailStudyHelper();
                                final String hostname = message.getHeader(E3Constants.MIME_STUDY_HOSTNAME)[0];

                                helper.apiGetRecordEncryptAsync(hostname, account.getEmail(), emailToken);
                            }
                        } catch (MessagingException e) {
                            throw new RuntimeException("Failed to encrypt on receipt!", e);
                        }
                    }
                });

                encryptThread.start();
            }

            @Override
            public void onError(Exception e) {
                Timber.e(e, "Got error while binding to OpenPGP service");
            }
        });

        serviceConnection.bindToService();
    }
}
