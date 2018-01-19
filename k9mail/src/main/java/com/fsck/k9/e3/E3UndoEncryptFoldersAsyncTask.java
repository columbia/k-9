package com.fsck.k9.e3;

import android.app.AlertDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.fsck.k9.Account;
import com.fsck.k9.R;
import com.fsck.k9.controller.MessagingListener;
import com.fsck.k9.controller.PendingCommandController;
import com.fsck.k9.crypto.MessageCryptoStructureDetector;
import com.fsck.k9.mail.FetchProfile;
import com.fsck.k9.mail.FetchProfile.Item;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.e3.E3Constants;
import com.fsck.k9.mail.e3.smime.SMIMEDetectorPredicate;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mailstore.LocalFolder;
import com.fsck.k9.mailstore.LocalMessage;
import com.fsck.k9.mailstore.LocalStore;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Created on 1/16/2018.
 *
 * @author koh
 */

public class E3UndoEncryptFoldersAsyncTask  extends AsyncTask<LocalFolder, Void, List<String>> {
    private static final int BATCH_SZ = 20;

    private final Account account;
    private final Function<Part, MimeMessage> decryptFunction;
    private final PendingCommandController pendingCommandController;
    private final Predicate<MimeMessage> isSMIMEPredicate;
    private final AlertDialog completedDialog;
    private final String alertStringFromCtx;

    public E3UndoEncryptFoldersAsyncTask(final Context context, final Account account, final Function<Part, MimeMessage> decryptFunction) {
        this.account = account;
        this.decryptFunction = decryptFunction;
        this.pendingCommandController = new PendingCommandController(context);
        this.isSMIMEPredicate = new SMIMEDetectorPredicate();
        this.completedDialog = new AlertDialog.Builder(context).create();
        this.alertStringFromCtx = context.getString(R.string.e3_undo_encrypt_completed);
    }

    @Override
    protected void onPreExecute() {
        // TODO: Add a completedDialog indicator of some sort
        //completedDialog = ProgressDialog.show()
    }

    @Override
    protected List<String> doInBackground(LocalFolder... folders) {
        for (final LocalFolder folder : folders) {
            try {
                return decryptFolder(folder);
            } catch (final MessagingException e) {
                Timber.e(e, "Failed to get list of message UIDs in given folder: " + folder);
            }
        }

        return Collections.emptyList();
    }

    @Override
    protected void onPostExecute(final List<String> decryptedMessages) {
        completedDialog.setMessage(String.format(alertStringFromCtx, decryptedMessages.size()));
        completedDialog.show();
    }

    private List<String> decryptFolder(final LocalFolder folder) throws MessagingException {
        final List<String> decryptedSubjects = new ArrayList<>();
        final List<String> uids = folder.getAllMessageUids();
        final List<List<String>> partitions = Lists.partition(uids, BATCH_SZ);

        final LocalStore localStore = account.getLocalStore();
        final Folder trashFolder = localStore.getFolder(account.getTrashFolderName());

        try {
            for (final List<String> partition : partitions) {
                final List<LocalMessage> batch = folder.getMessagesByUids(partition);
                final List<LocalMessage> filtered = ImmutableList.copyOf(Collections2.filter(batch, isSMIMEPredicate));

                for (final LocalMessage originalEncryptedMsg : filtered) {
                    final List<LocalMessage> singletonMsg = Collections.singletonList(originalEncryptedMsg);
                    // Fetch the message contents before we try to decrypt it
                    final FetchProfile fetchProfile = new FetchProfile();
                    fetchProfile.add(Item.ENVELOPE);
                    fetchProfile.add(Item.BODY);
                    folder.fetch(Collections.singletonList(originalEncryptedMsg), fetchProfile, null);

                    // Need to set here because the decryptFunction is unaware of LocalMessage
                    originalEncryptedMsg.setMimeType(MessageCryptoStructureDetector.SMIME_CONTENT_TYPE);

                    final List<String> uidSingleton = Collections.singletonList(originalEncryptedMsg.getUid());

                    final MimeMessage decryptedMessage = decryptFunction.apply(originalEncryptedMsg);

                    try {
                        Preconditions.checkNotNull(decryptedMessage, "Failed to decrypt originalEncryptedMsg: " + originalEncryptedMsg);
                        Log.d(E3Constants.LOG_TAG, String.format("Undo decryption for messageId=%s completed", originalEncryptedMsg.getMessageId()));
                    } catch (final Exception e) {
                        // This message might be bad, so let's skip it.
                        Log.e(E3Constants.LOG_TAG, "Failed to decrypt a message", e);
                        continue;
                    }

                    decryptedMessage.setFlag(Flag.E3, false);
                    decryptedMessage.setUid("");

                    // Store the decrypted message locally
                    final LocalMessage localMessageDecrypted = folder.storeSmallMessage(decryptedMessage, new Runnable
                            () {
                        @Override
                        public void run() {
                            // TODO: add completedDialog?
                            //completedDialog.incrementAndGet();
                        }
                    });

                    folder.fetch(Collections.singletonList(localMessageDecrypted), fetchProfile, null);

                    final Map<String, String> trashUidMap = folder.moveMessages(singletonMsg, trashFolder);

                    // First: Set \Deleted and \E3_DONE on the original message
                    pendingCommandController.queueSetFlag(account, folder.getName(), true,
                            Flag.DELETED, uidSingleton);

                    // Second: Move original to Gmail's trash folder
                    pendingCommandController.queueMoveToTrash(account, folder.getName(), uidSingleton, trashUidMap);

                    // Third: Append decrypted remotely
                    pendingCommandController.queueAppend(account, folder.getName(), localMessageDecrypted.getUid());

                    // Fourth: Queue empty trash (expunge) command
                    // Expunging is not really necessary since the email is encrypted. But if we don't, it messes up the threading in Gmail.
                    pendingCommandController.queueEmptyTrash(account);

                    // Final: Run all the queued commands
                    // Invoking it here instead of for the entire batch is inefficient. However,
                    // there seems to be some kind of bug in the ImapConnection handler so that
                    // if we queue multiple commands for multiple messages, it gets null responses.
                    pendingCommandController.processPendingCommandsSynchronous(account, Collections.<MessagingListener>emptySet());

                    decryptedSubjects.add(originalEncryptedMsg.getSubject());
                }
            }
        } finally {
            if (trashFolder != null) {
                trashFolder.close();
            }
        }

        return decryptedSubjects;
    }
}
