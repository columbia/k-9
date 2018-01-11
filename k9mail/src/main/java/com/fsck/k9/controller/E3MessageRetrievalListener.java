package com.fsck.k9.controller;

import android.content.Context;
import android.util.Log;

import com.fsck.k9.Account;
import com.fsck.k9.controller.MessagingControllerCommands.PendingAppend;
import com.fsck.k9.controller.MessagingControllerCommands.PendingEmptyTrash;
import com.fsck.k9.controller.MessagingControllerCommands.PendingMoveOrCopy;
import com.fsck.k9.mail.FetchProfile;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessageRetrievalListener;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.e3.E3Constants;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mailstore.LocalFolder;
import com.fsck.k9.mailstore.LocalMessage;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import timber.log.Timber;

/**
 * Created on 12/14/2017.
 *
 * @author koh
 */

public class E3MessageRetrievalListener<T extends Message> implements MessageRetrievalListener<T> {
    private final Context context;
    private final MessagingController controller;
    private final Account account;
    private final String remoteFolder;
    private final LocalFolder localFolder;
    private final AtomicInteger progress;
    private final int unreadBeforeStart;
    private final AtomicInteger newMessages;
    private final int todo;
    private final MessagingControllerSupport controllerSupport;
    private final Function<MimeMessage, MimeMessage> encryptFunction;
    private final Predicate<Message> shouldEncryptPredicate;

    private final PendingCommandController pendingCommandController;

    /**
     * Use {@link Builder}.
     */
    private E3MessageRetrievalListener(final Context context, //
                                       final MessagingController controller, //
                                       final Account account, //
                                       final String remoteFolder, //
                                       final LocalFolder localFolder, //
                                       final AtomicInteger progress, //
                                       final int unreadBeforeStart, //
                                       final AtomicInteger newMessages, //
                                       final int todo, //
                                       final MessagingControllerSupport controllerSupport, //
                                       final Function<MimeMessage, MimeMessage> encryptFunction, //
                                       final Predicate<Message> shouldEncryptPredicate) {
        this.context = context;
        this.controller = controller;
        this.account = account;
        this.remoteFolder = remoteFolder;
        this.localFolder = localFolder;
        this.progress = progress;
        this.unreadBeforeStart = unreadBeforeStart;
        this.newMessages = newMessages;
        this.todo = todo;
        this.controllerSupport = controllerSupport;
        this.encryptFunction = encryptFunction;
        this.shouldEncryptPredicate = shouldEncryptPredicate;

        this.pendingCommandController = new PendingCommandController();
    }

    @Override
    public void messageFinished(final T message, int number, int ofTotal) {
        try {
            final LocalMessage localMessage = replaceEncrypted(account, localFolder, message);

            if (localMessage == null) {
                return;
            }

            // Increment the number of "new messages" if the newly downloaded message is
            // not marked as read.
            if (!localMessage.isSet(Flag.SEEN)) {
                newMessages.incrementAndGet();
            }

            Timber.v("About to notify listeners that we got a new small message %s:%s:%s",
                    account, remoteFolder, message.getUid());

            // Update the listener with what we've found
            for (MessagingListener l : controller.getListeners()) {
                l.synchronizeMailboxProgress(account, remoteFolder, progress.get(), todo);
                if (!localMessage.isSet(Flag.SEEN)) {
                    l.synchronizeMailboxNewMessage(account, remoteFolder, localMessage);
                }
            }
            // Send a notification of this message

            if (controllerSupport.shouldNotifyForMessage(account, localFolder, message)) {
                // Notify with the localMessage so that we don't have to recalculate the content preview.
                controller.getNotificationController().addNewMailNotification(account, localMessage, unreadBeforeStart);
            }

        } catch (MessagingException me) {
            Timber.e(me, "SYNC: fetch small messages");
        }
    }

    private Optional<Message> encryptMessage(final Message original) {
        Preconditions.checkArgument(original instanceof MimeMessage, "Can only " + "encrypt " +
                "MimeMessage, but got " + original);
        try {
            final MimeMessage encrypted = Preconditions.checkNotNull(encryptFunction.apply(
                    (MimeMessage) original));

            Log.d(E3Constants.LOG_TAG, "Encrypted message: " + encrypted.getMessageId());

            encrypted.setUid("");

            return Optional.of((Message) encrypted);
        } catch (final IllegalArgumentException e) {
            Log.d(E3Constants.LOG_TAG, String.format("Original message (%s) already " +
                    "encrypted or not MimeMessage", original.getUid()));
            return Optional.absent();
        }
    }

    private LocalMessage replaceEncrypted(final Account account, final LocalFolder localFolder,
                                          final Message original) throws MessagingException {
        // Encrypt the message and use the encrypted form instead
        final boolean shouldEncrypt = account.isE3EncryptionEnabled() && shouldEncryptPredicate
                .apply(original);
        final Optional<Message> encryptedMessageOptional = shouldEncrypt ? encryptMessage
                (original) : Optional.<Message>absent();

        // Then we know it was encrypted and the UID was reset
        if (encryptedMessageOptional.isPresent()) {
            final Message message = encryptedMessageOptional.get();

            message.setFlag(Flag.E3, true);

            // Store the updated (now downloaded) message locally
            final LocalMessage localMessage = localFolder.storeSmallMessage(message, new Runnable
                    () {
                @Override
                public void run() {
                    progress.incrementAndGet();
                }
            });

            final FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            fp.add(FetchProfile.Item.BODY);
            localFolder.fetch(Collections.singletonList(localMessage), fp, null);

            final String trashFolder = account.getTrashFolderName();
            final boolean folderIsTrash = trashFolder.equals(localFolder.getName());
            final List<String> uidSingleton = Collections.singletonList(original.getUid());

            if (!folderIsTrash) {
                // First: Set \Deleted and \E3_DONE on the original message
                controller.queueSetFlag(account, localFolder.getName(), true,
                        Flag.DELETED, uidSingleton);
                controller.queueSetFlag(account, localFolder.getName(), true,
                        Flag.E3_DONE, uidSingleton);

                // Second: Move original to Gmail's trash folder
                final PendingMoveOrCopy moveCmd = PendingMoveOrCopy.create(localFolder.getName(),
                        account.getTrashFolderName(), false, uidSingleton);

                pendingCommandController.queuePendingCommand(account, moveCmd);
            }

            // Third: Append encrypted remotely
            final PendingAppend appendCmd = PendingAppend.create(localFolder.getName(), message.getUid());
            Log.d(E3Constants.LOG_TAG, "PendingAppend: " + appendCmd);

            pendingCommandController.queuePendingCommand(account, appendCmd);

            if (!folderIsTrash) {
                // Fourth: Queue empty trash (expunge) command
                final PendingEmptyTrash emptyTrashCmd = PendingEmptyTrash.create();

                pendingCommandController.queuePendingCommand(account, emptyTrashCmd);
            }

            // Final: Run all the queued commands
            pendingCommandController.processPendingCommandsSynchronous(account, Collections.<MessagingListener>emptySet());

            return localMessage;
        } else {
            // Store the original updated message locally, and return the LocalMessage
            return localFolder.storeSmallMessage(original, new Runnable() {
                @Override
                public void run() {
                    progress.incrementAndGet();
                }
            });
        }
    }

    @Override
    public void messageStarted(String uid, int number, int ofTotal) {
    }

    @Override
    public void messagesFinished(int total) {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        E3MessageRetrievalListener<?> that = (E3MessageRetrievalListener<?>) o;
        return unreadBeforeStart == that.unreadBeforeStart && todo == that.todo && Objects.equal
                (context, that.context) && Objects.equal(controller, that.controller) && Objects
                .equal(account, that.account) && Objects.equal(remoteFolder, that.remoteFolder)
                && Objects.equal(localFolder, that.localFolder) && Objects.equal(progress, that
                .progress) && Objects.equal(newMessages, that.newMessages) && Objects.equal
                (controllerSupport, that.controllerSupport) && Objects.equal(encryptFunction, that
                .encryptFunction) && Objects.equal(shouldEncryptPredicate, that
                .shouldEncryptPredicate);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(context, controller, account, remoteFolder, localFolder,
                progress, unreadBeforeStart, newMessages, todo, controllerSupport, encryptFunction,
                shouldEncryptPredicate);
    }

    public static class Builder<T extends Message> {
        private Context context;
        private MessagingController controller;
        private Account account;
        private String remoteFolder;
        private LocalFolder localFolder;
        private AtomicInteger progress;
        private int unreadBeforeStart;
        private AtomicInteger newMessages;
        private int todo;
        private MessagingControllerSupport controllerSupport;
        private Function<MimeMessage, MimeMessage> encryptFunction;
        private Predicate<Message> shouldEncryptPredicate;

        public Builder() {

        }

        public E3MessageRetrievalListener<T> build() {
            Preconditions.checkNotNull(context);
            Preconditions.checkNotNull(controller);
            Preconditions.checkNotNull(account);
            Preconditions.checkNotNull(remoteFolder);
            Preconditions.checkNotNull(localFolder);
            Preconditions.checkNotNull(progress);
            Preconditions.checkNotNull(unreadBeforeStart);
            Preconditions.checkNotNull(newMessages);
            Preconditions.checkNotNull(controllerSupport);
            Preconditions.checkNotNull(encryptFunction);
            shouldEncryptPredicate = shouldEncryptPredicate == null ? Predicates
                    .<Message>alwaysTrue() : shouldEncryptPredicate;
            return new E3MessageRetrievalListener<>(context, controller, account, remoteFolder,
                    localFolder, progress, unreadBeforeStart, newMessages, todo, controllerSupport,
                    encryptFunction, shouldEncryptPredicate);
        }

        public Context getContext() {
            return context;
        }

        public Builder setContext(Context context) {
            this.context = context;
            return this;
        }

        public MessagingController getController() {
            return controller;
        }

        public Builder setController(MessagingController controller) {
            this.controller = controller;
            return this;
        }

        public Account getAccount() {
            return account;
        }

        public Builder setAccount(Account account) {
            this.account = account;
            return this;
        }

        public String getRemoteFolder() {
            return remoteFolder;
        }

        public Builder setRemoteFolder(String remoteFolder) {
            this.remoteFolder = remoteFolder;
            return this;
        }

        public LocalFolder getLocalFolder() {
            return localFolder;
        }

        public Builder setLocalFolder(LocalFolder localFolder) {
            this.localFolder = localFolder;
            return this;
        }

        public AtomicInteger getProgress() {
            return progress;
        }

        public Builder setProgress(AtomicInteger progress) {
            this.progress = progress;
            return this;
        }

        public int getUnreadBeforeStart() {
            return unreadBeforeStart;
        }

        public Builder setUnreadBeforeStart(int unreadBeforeStart) {
            this.unreadBeforeStart = unreadBeforeStart;
            return this;
        }

        public AtomicInteger getNewMessages() {
            return newMessages;
        }

        public Builder setNewMessages(AtomicInteger newMessages) {
            this.newMessages = newMessages;
            return this;
        }

        public int getTodo() {
            return todo;
        }

        public Builder setTodo(int todo) {
            this.todo = todo;
            return this;
        }

        public MessagingControllerSupport getControllerSupport() {
            return controllerSupport;
        }

        public Builder setControllerSupport(MessagingControllerSupport controllerSupport) {
            this.controllerSupport = controllerSupport;
            return this;
        }

        public Function<MimeMessage, MimeMessage> getEncryptFunction() {
            return encryptFunction;
        }

        public Builder setEncryptFunction(Function<MimeMessage, MimeMessage> encryptFunction) {
            this.encryptFunction = encryptFunction;
            return this;
        }

        public Predicate<Message> getShouldEncryptPredicate() {
            return shouldEncryptPredicate;
        }

        public Builder setShouldEncryptPredicate(Predicate<Message> shouldEncryptPredicate) {
            this.shouldEncryptPredicate = shouldEncryptPredicate;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Builder<?> builder = (Builder<?>) o;
            return unreadBeforeStart == builder.unreadBeforeStart &&
                    todo == builder.todo &&
                    Objects.equal(context, builder.context) &&
                    Objects.equal(controller, builder.controller) &&
                    Objects.equal(account, builder.account) &&
                    Objects.equal(remoteFolder, builder.remoteFolder) &&
                    Objects.equal(localFolder, builder.localFolder) &&
                    Objects.equal(progress, builder.progress) &&
                    Objects.equal(newMessages, builder.newMessages) &&
                    Objects.equal(controllerSupport, builder.controllerSupport) &&
                    Objects.equal(encryptFunction, builder.encryptFunction) &&
                    Objects.equal(shouldEncryptPredicate, builder.shouldEncryptPredicate);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(context, controller, account, remoteFolder, localFolder, progress, unreadBeforeStart, newMessages, todo, controllerSupport, encryptFunction, shouldEncryptPredicate);
        }
    }
}