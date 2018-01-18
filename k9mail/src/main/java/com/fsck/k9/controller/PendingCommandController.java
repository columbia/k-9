package com.fsck.k9.controller;

import android.content.Context;

import com.fsck.k9.Account;
import com.fsck.k9.Account.Expunge;
import com.fsck.k9.K9;
import com.fsck.k9.controller.MessagingControllerCommands.PendingAppend;
import com.fsck.k9.controller.MessagingControllerCommands.PendingCommand;
import com.fsck.k9.controller.MessagingControllerCommands.PendingEmptyTrash;
import com.fsck.k9.controller.MessagingControllerCommands.PendingExpunge;
import com.fsck.k9.controller.MessagingControllerCommands.PendingMarkAllAsRead;
import com.fsck.k9.controller.MessagingControllerCommands.PendingMoveOrCopy;
import com.fsck.k9.controller.MessagingControllerCommands.PendingSetFlag;
import com.fsck.k9.mail.FetchProfile;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.Folder.FolderType;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Store;
import com.fsck.k9.mailstore.LocalFolder;
import com.fsck.k9.mailstore.LocalMessage;
import com.fsck.k9.mailstore.LocalStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

import static com.fsck.k9.mail.Flag.X_REMOTE_COPY_STARTED;

/**
 * {@link PendingCommand} controls.
 * <p>
 * Note: Some of the controller code still exists in {@link MessagingController} and should probably
 * be eventually ported here.
 * <p>
 * Created on 1/11/2018.
 *
 * @author koh
 */

public class PendingCommandController {
    private final MessagingController messagingController;

    PendingCommandController(final MessagingController messagingController) {
        this.messagingController = messagingController;
    }

    public PendingCommandController(final Context context) {
        messagingController = MessagingController.getInstance(context);
    }

    public void queuePendingCommand(final Account account, final PendingCommand command) {
        try {
            final LocalStore localStore = account.getLocalStore();
            localStore.addPendingCommand(command);
        } catch (Exception e) {
            throw new RuntimeException("Unable to enqueue pending command", e);
        }
    }

    public void queueSetFlag(final Account account, final String folderName,
                             final boolean newState, final Flag flag, final List<String> uids) {
        final PendingCommand command = PendingSetFlag.create(folderName, newState, flag, uids);
        queuePendingCommand(account, command);
    }

    public void queueMoveToTrash(final Account account, final String folderName, final List<String> uids, final Map<String, String> mappedUids) {
        final PendingMoveOrCopy moveCmd;
        if (mappedUids == null || mappedUids.isEmpty()) {
            moveCmd = PendingMoveOrCopy.create(folderName, account.getTrashFolderName(), false, uids);
        } else {
            moveCmd = PendingMoveOrCopy.create(folderName, account.getTrashFolderName(), false, mappedUids);
        }
        queuePendingCommand(account, moveCmd);
    }

    public void queueEmptyTrash(final Account account) {
        final PendingEmptyTrash emptyTrashCmd = PendingEmptyTrash.create();
        queuePendingCommand(account, emptyTrashCmd);
    }

    public void queueAppend(final Account account, final String folderName, final String uid) {
        final PendingAppend appendCmd = PendingAppend.create(folderName, uid);
        queuePendingCommand(account, appendCmd);
    }

    public void processPendingCommandsSynchronous(final Account account, final Set<MessagingListener> listeners) throws MessagingException {
        LocalStore localStore = account.getLocalStore();
        List<PendingCommand> commands = localStore.getPendingCommands();

        int progress = 0;
        int todo = commands.size();
        if (todo == 0) {
            return;
        }

        for (MessagingListener l : listeners) {
            l.pendingCommandsProcessing(account);
            l.synchronizeMailboxProgress(account, null, progress, todo);
        }

        PendingCommand processingCommand = null;
        try {
            for (PendingCommand command : commands) {
                processingCommand = command;
                Timber.d("Processing pending command '%s'", command);

                for (MessagingListener l : listeners) {
                    l.pendingCommandStarted(account, command.getCommandName());
                }
                /*
                 * We specifically do not catch any exceptions here. If a command fails it is
                 * most likely due to a server or IO error and it must be retried before any
                 * other command processes. This maintains the order of the commands.
                 */
                try {
                    command.execute(this, account, listeners);

                    localStore.removePendingCommand(command);

                    Timber.d("Done processing pending command '%s'", command);
                } catch (MessagingException me) {
                    if (me.isPermanentFailure()) {
                        Timber.e("Failure of command '%s' was permanent, removing command from queue", command);
                        localStore.removePendingCommand(processingCommand);
                    } else {
                        throw me;
                    }
                } finally {
                    progress++;
                    for (MessagingListener l : listeners) {
                        l.synchronizeMailboxProgress(account, null, progress, todo);
                        l.pendingCommandCompleted(account, command.getCommandName());
                    }
                }
            }
        } catch (MessagingException me) {
            Timber.e(me, "Could not process command '%s'", processingCommand);
            throw me;
        } finally {
            for (MessagingListener l : listeners) {
                l.pendingCommandsFinished(account);
            }
        }
    }

    void processPendingMoveOrCopy(PendingMoveOrCopy command, Account account, Set<MessagingListener> listeners) throws MessagingException {
        Folder remoteSrcFolder = null;
        Folder remoteDestFolder = null;
        LocalFolder localDestFolder;
        try {
            String srcFolder = command.srcFolder;
            String destFolder = command.destFolder;
            boolean isCopy = command.isCopy;

            Store remoteStore = account.getRemoteStore();
            remoteSrcFolder = remoteStore.getFolder(srcFolder);

            Store localStore = account.getLocalStore();
            localDestFolder = (LocalFolder) localStore.getFolder(destFolder);
            List<Message> messages = new ArrayList<>();

            Collection<String> uids = command.newUidMap != null ? command.newUidMap.keySet() : command.uids;
            for (String uid : uids) {
                if (!uid.startsWith(K9.LOCAL_UID_PREFIX)) {
                    messages.add(remoteSrcFolder.getMessage(uid));
                }
            }

            if (!remoteSrcFolder.exists()) {
                throw new MessagingException(
                        "processingPendingMoveOrCopy: remoteFolder " + srcFolder + " does not exist", true);
            }
            remoteSrcFolder.open(Folder.OPEN_MODE_RW);
            if (remoteSrcFolder.getMode() != Folder.OPEN_MODE_RW) {
                throw new MessagingException("processingPendingMoveOrCopy: could not open remoteSrcFolder "
                        + srcFolder + " read/write", true);
            }

            Timber.d("processingPendingMoveOrCopy: source folder = %s, %d messages, destination folder = %s, " +
                    "isCopy = %s", srcFolder, messages.size(), destFolder, isCopy);

            Map<String, String> remoteUidMap = null;

            if (!isCopy && destFolder.equals(account.getTrashFolderName())) {
                Timber.d("processingPendingMoveOrCopy doing special case for deleting message");

                String destFolderName = destFolder;
                if (K9.FOLDER_NONE.equals(destFolderName)) {
                    destFolderName = null;
                }
                remoteSrcFolder.delete(messages, destFolderName);
            } else {
                remoteDestFolder = remoteStore.getFolder(destFolder);

                if (isCopy) {
                    remoteUidMap = remoteSrcFolder.copyMessages(messages, remoteDestFolder);
                } else {
                    remoteUidMap = remoteSrcFolder.moveMessages(messages, remoteDestFolder);
                }
            }
            if (!isCopy && Expunge.EXPUNGE_IMMEDIATELY == account.getExpungePolicy()) {
                Timber.i("processingPendingMoveOrCopy expunging folder %s:%s", account.getDescription(), srcFolder);
                remoteSrcFolder.expunge();
            }

            /*
             * This next part is used to bring the local UIDs of the local destination folder
             * upto speed with the remote UIDs of remote destination folder.
             */
            if (command.newUidMap != null && remoteUidMap != null && !remoteUidMap.isEmpty()) {
                for (Map.Entry<String, String> entry : remoteUidMap.entrySet()) {
                    String remoteSrcUid = entry.getKey();
                    String newUid = entry.getValue();
                    String localDestUid = command.newUidMap.get(remoteSrcUid);
                    if (localDestUid == null) {
                        continue;
                    }

                    Message localDestMessage = localDestFolder.getMessage(localDestUid);
                    if (localDestMessage != null) {
                        localDestMessage.setUid(newUid);
                        localDestFolder.changeUid((LocalMessage) localDestMessage);
                        for (MessagingListener l : listeners) {
                            l.messageUidChanged(account, destFolder, localDestUid, newUid);
                        }
                    }
                }
            }
        } finally {
            MessagingControllerSupport.closeFolder(remoteSrcFolder);
            MessagingControllerSupport.closeFolder(remoteDestFolder);
        }
    }

    void processPendingEmptyTrash(Account account) throws MessagingException {
        Store remoteStore = account.getRemoteStore();

        Folder remoteFolder = remoteStore.getFolder(account.getTrashFolderName());
        try {
            if (remoteFolder.exists()) {
                remoteFolder.open(Folder.OPEN_MODE_RW);
                remoteFolder.setFlags(Collections.singleton(Flag.DELETED), true);
                if (Expunge.EXPUNGE_IMMEDIATELY == account.getExpungePolicy()) {
                    remoteFolder.expunge();
                }

                // When we empty trash, we need to actually synchronize the folder
                // or local deletes will never get cleaned up
                messagingController.synchronizeFolder(account, remoteFolder, true, 0, null);
                messagingController.compact(account, null);
            }
        } finally {
            MessagingControllerSupport.closeFolder(remoteFolder);
        }
    }

    /**
     * Processes a pending mark read or unread command.
     */
    void processPendingSetFlag(PendingSetFlag command, Account account) throws MessagingException {
        String folder = command.folder;

        boolean newState = command.newState;
        Flag flag = command.flag;

        Store remoteStore = account.getRemoteStore();
        Folder remoteFolder = remoteStore.getFolder(folder);
        if (!remoteFolder.exists() || !remoteFolder.isFlagSupported(flag)) {
            return;
        }

        try {
            remoteFolder.open(Folder.OPEN_MODE_RW);
            if (remoteFolder.getMode() != Folder.OPEN_MODE_RW) {
                return;
            }
            List<Message> messages = new ArrayList<>();
            for (String uid : command.uids) {
                if (!uid.startsWith(K9.LOCAL_UID_PREFIX)) {
                    messages.add(remoteFolder.getMessage(uid));
                }
            }

            if (messages.isEmpty()) {
                return;
            }
            remoteFolder.setFlags(messages, Collections.singleton(flag), newState);
        } finally {
            MessagingControllerSupport.closeFolder(remoteFolder);
        }
    }

    /**
     * Process a pending append message command. This command uploads a local message to the
     * server, first checking to be sure that the server message is not newer than
     * the local message. Once the local message is successfully processed it is deleted so
     * that the server message will be synchronized down without an additional copy being
     * created.
     * TODO update the local message UID instead of deleting it
     */
    void processPendingAppend(PendingAppend command, Account account, Set<MessagingListener> listeners) throws MessagingException {
        Folder remoteFolder = null;
        LocalFolder localFolder = null;
        try {

            String folder = command.folder;
            String uid = command.uid;

            LocalStore localStore = account.getLocalStore();
            localFolder = localStore.getFolder(folder);
            LocalMessage localMessage = localFolder.getMessage(uid);

            if (localMessage == null) {
                return;
            }

            Store remoteStore = account.getRemoteStore();
            remoteFolder = remoteStore.getFolder(folder);
            if (!remoteFolder.exists()) {
                if (!remoteFolder.create(FolderType.HOLDS_MESSAGES)) {
                    return;
                }
            }
            remoteFolder.open(Folder.OPEN_MODE_RW);
            if (remoteFolder.getMode() != Folder.OPEN_MODE_RW) {
                return;
            }

            Message remoteMessage = null;
            if (!localMessage.getUid().startsWith(K9.LOCAL_UID_PREFIX)) {
                remoteMessage = remoteFolder.getMessage(localMessage.getUid());
            }

            if (remoteMessage == null) {
                if (localMessage.isSet(Flag.X_REMOTE_COPY_STARTED)) {
                    Timber.w("Local message with uid %s has flag %s  already set, checking for remote message with " +
                            "same message id", localMessage.getUid(), X_REMOTE_COPY_STARTED);
                    String rUid = remoteFolder.getUidFromMessageId(localMessage);
                    if (rUid != null) {
                        Timber.w("Local message has flag %s already set, and there is a remote message with uid %s, " +
                                        "assuming message was already copied and aborting this copy",
                                X_REMOTE_COPY_STARTED, rUid);

                        String oldUid = localMessage.getUid();
                        localMessage.setUid(rUid);
                        localFolder.changeUid(localMessage);
                        for (MessagingListener l : listeners) {
                            l.messageUidChanged(account, folder, oldUid, localMessage.getUid());
                        }
                        return;
                    } else {
                        Timber.w("No remote message with message-id found, proceeding with append");
                    }
                }

                /*
                 * If the message does not exist remotely we just upload it and then
                 * update our local copy with the new uid.
                 */
                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.BODY);
                localFolder.fetch(Collections.singletonList(localMessage), fp, null);
                String oldUid = localMessage.getUid();
                localMessage.setFlag(Flag.X_REMOTE_COPY_STARTED, true);
                remoteFolder.appendMessages(Collections.singletonList(localMessage));

                localFolder.changeUid(localMessage);
                for (MessagingListener l : listeners) {
                    l.messageUidChanged(account, folder, oldUid, localMessage.getUid());
                }
            } else {
                /*
                 * If the remote message exists we need to determine which copy to keep.
                 */
                /*
                 * See if the remote message is newer than ours.
                 */
                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.ENVELOPE);
                remoteFolder.fetch(Collections.singletonList(remoteMessage), fp, null);
                Date localDate = localMessage.getInternalDate();
                Date remoteDate = remoteMessage.getInternalDate();
                if (remoteDate != null && remoteDate.compareTo(localDate) > 0) {
                    /*
                     * If the remote message is newer than ours we'll just
                     * delete ours and move on. A sync will get the server message
                     * if we need to be able to see it.
                     */
                    localMessage.destroy();
                } else {
                    /*
                     * Otherwise we'll upload our message and then delete the remote message.
                     */
                    fp = new FetchProfile();
                    fp.add(FetchProfile.Item.BODY);
                    localFolder.fetch(Collections.singletonList(localMessage), fp, null);
                    String oldUid = localMessage.getUid();

                    localMessage.setFlag(Flag.X_REMOTE_COPY_STARTED, true);

                    remoteFolder.appendMessages(Collections.singletonList(localMessage));
                    localFolder.changeUid(localMessage);
                    for (MessagingListener l : listeners) {
                        l.messageUidChanged(account, folder, oldUid, localMessage.getUid());
                    }
                    if (remoteDate != null) {
                        remoteMessage.setFlag(Flag.DELETED, true);
                        if (Expunge.EXPUNGE_IMMEDIATELY == account.getExpungePolicy()) {
                            remoteFolder.expunge();
                        }
                    }
                }
            }
        } finally {
            MessagingControllerSupport.closeFolder(remoteFolder);
            MessagingControllerSupport.closeFolder(localFolder);
        }
    }

    void processPendingMarkAllAsRead(PendingMarkAllAsRead command, Account account, Set<MessagingListener> listeners) throws MessagingException {
        String folder = command.folder;
        Folder remoteFolder = null;
        LocalFolder localFolder = null;
        try {
            Store localStore = account.getLocalStore();
            localFolder = (LocalFolder) localStore.getFolder(folder);
            localFolder.open(Folder.OPEN_MODE_RW);
            List<? extends Message> messages = localFolder.getMessages(null, false);
            for (Message message : messages) {
                if (!message.isSet(Flag.SEEN)) {
                    message.setFlag(Flag.SEEN, true);
                }
            }

            for (MessagingListener l : listeners) {
                l.folderStatusChanged(account, folder, 0);
            }

            Store remoteStore = account.getRemoteStore();
            remoteFolder = remoteStore.getFolder(folder);

            if (!remoteFolder.exists() || !remoteFolder.isFlagSupported(Flag.SEEN)) {
                return;
            }
            remoteFolder.open(Folder.OPEN_MODE_RW);
            if (remoteFolder.getMode() != Folder.OPEN_MODE_RW) {
                return;
            }

            remoteFolder.setFlags(Collections.singleton(Flag.SEEN), true);
            remoteFolder.close();
        } catch (UnsupportedOperationException uoe) {
            Timber.w(uoe, "Could not mark all server-side as read because store doesn't support operation");
        } finally {
            MessagingControllerSupport.closeFolder(localFolder);
            MessagingControllerSupport.closeFolder(remoteFolder);
        }
    }

    void processPendingExpunge(PendingExpunge command, Account account) throws MessagingException {
        String folder = command.folder;

        Timber.d("processPendingExpunge: folder = %s", folder);

        Store remoteStore = account.getRemoteStore();
        Folder remoteFolder = remoteStore.getFolder(folder);
        try {
            if (!remoteFolder.exists()) {
                return;
            }
            remoteFolder.open(Folder.OPEN_MODE_RW);
            if (remoteFolder.getMode() != Folder.OPEN_MODE_RW) {
                return;
            }
            remoteFolder.expunge();

            Timber.d("processPendingExpunge: complete for folder = %s", folder);
        } finally {
            MessagingControllerSupport.closeFolder(remoteFolder);
        }
    }
}
