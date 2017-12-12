package com.fsck.k9.e3;

import android.os.AsyncTask;
import android.util.Log;

import com.fsck.k9.Account;
import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.FetchProfile;
import com.fsck.k9.mail.FetchProfile.Item;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.Folder.FolderType;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Multipart;
import com.fsck.k9.mail.Store;
import com.fsck.k9.mail.e3.E3Constants;
import com.fsck.k9.mail.e3.E3Key;
import com.fsck.k9.mail.e3.PFXToE3KeyFunction;
import com.fsck.k9.mail.filter.Base64;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mail.internet.RawDataBody;
import com.fsck.k9.mail.store.imap.ImapStore;
import com.fsck.k9.mailstore.LocalMessage;
import com.fsck.k9.mailstore.LocalStore;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * Created on 12/12/2017.
 *
 * @author koh
 */

public class FetchE3KeyAsyncTask extends AsyncTask<Void, Void, Optional<E3Key>> {
    private final Account account;
    private final String e3Folder;
    private final Function<byte[], E3Key> pfxToE3KeyFunction;

    public FetchE3KeyAsyncTask(final Account account, final String e3Folder, final String
            e3KeyName, final String e3Password) {
        this.account = account;
        this.e3Folder = e3Folder;
        this.pfxToE3KeyFunction = new PFXToE3KeyFunction(e3KeyName, e3Password);
    }

    @Override
    protected Optional<E3Key> doInBackground(final Void... nothingness) {
        final boolean oldSearchSetting = account.allowRemoteSearch();

        try {
            final LocalStore local = account.getLocalStore();
            final Store plainRemoteStore = account.getRemoteStore();

            Preconditions.checkState(plainRemoteStore instanceof ImapStore, "Remote is not " +
                    "IMAP: " + plainRemoteStore.getClass().getSimpleName());

            final ImapStore remote = (ImapStore) plainRemoteStore;

            final Folder<LocalMessage> localE3Folder = local.getFolder(e3Folder);
            final Folder remoteE3Folder = remote.getFolder(e3Folder);

            account.setAllowRemoteSearch(true);

            if (!localE3Folder.exists() && !remoteE3Folder.exists()) {
                localE3Folder.create(FolderType.HOLDS_MESSAGES);
                Log.d(E3Constants.LOG_TAG, "Created backup folder locally: " + localE3Folder
                        .getName());
            }

            if (remoteE3Folder.exists()) {
                return getE3KeyFromMailbox(remoteE3Folder);
            }

            return Optional.absent();
        } catch (final MessagingException e) {
            throw new RuntimeException(e);
        } finally {
            account.setAllowRemoteSearch(oldSearchSetting);
        }
    }

    // Necessary because ImapMessage is package private, so we are using MimeMessage
    @SuppressWarnings("unchecked")
    private Optional<E3Key> getE3KeyFromMailbox(final Folder remote) throws
            MessagingException {
        // Search for emails with KEYWORD E3
        final List<MimeMessage> searchRes = (List<MimeMessage>) remote.search("E3", Collections.singleton(Flag.E3_KEY),
                Collections.<Flag>emptySet());
        final FetchProfile fp = new FetchProfile();
        fp.add(Item.BODY);

        // Populate the skeleton ImapMessage objects with the actual content
        remote.fetch(searchRes, fp, null);

        for (final MimeMessage mimeMsg : searchRes) {
            Log.d(E3Constants.LOG_TAG, "Got fetched result from remote E3 folder: " + mimeMsg.toString());

            final Body body = mimeMsg.getBody();

            if (body != null) {
                final Optional<byte[]> rawPfxOptional = processPotentialE3KeyEmail(body);

                if (!rawPfxOptional.isPresent()) {
                    continue;
                }

                Log.d(E3Constants.LOG_TAG, "Got raw E3 pfx with byte count: " + rawPfxOptional
                        .get().length);

                return Optional.fromNullable(pfxToE3KeyFunction.apply(rawPfxOptional.get()));
            }
        }

        return Optional.absent();
    }

    private Optional<byte[]> processPotentialE3KeyEmail(final Body body) throws MessagingException {
        if (body instanceof Multipart) {
            final Multipart multipart = (Multipart) body;

            for (final BodyPart bodyPart : multipart.getBodyParts()) {
                final Optional<byte[]> rawPfx = bodyPartProcessor(bodyPart);

                if (rawPfx.isPresent()) {
                    return rawPfx;
                }
            }

            return Optional.absent();
        } else {
            throw new IllegalArgumentException("No support for non-multipart E3 key emails");
        }
    }

    private Optional<byte[]> bodyPartProcessor(final BodyPart bodyPart) throws MessagingException {
        final Body body = bodyPart.getBody();

        if (body == null || !bodyPart.getContentType().contains("x-pkcs12")) {
            return Optional.absent();
        }

        if (!(body instanceof RawDataBody)) {
            return Optional.absent();
        }
        final RawDataBody rawDataBody = (RawDataBody) body;
        final String encoding = rawDataBody.getEncoding();
        final InputStream bodyStream = rawDataBody.getInputStream();

        try {
            final byte[] rawBody = ByteStreams.toByteArray(bodyStream);

            Preconditions.checkState(rawBody != null, "BodyPart's data was null");
            Preconditions.checkState(rawBody.length != 0, "BodyPart's length was 0");

            if (encoding.toLowerCase().equals("base64")) {
                return Optional.of(Base64.decodeBase64(rawBody));
            }

            return Optional.of(rawBody);
        } catch (final IOException e) {
            Closeables.closeQuietly(bodyStream);
        }

        return Optional.absent();
    }
}
