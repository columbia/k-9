package com.fsck.k9.activity.setup;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

import com.fsck.k9.Account;
import com.fsck.k9.K9;
import com.fsck.k9.Preferences;
import com.fsck.k9.R;
import com.fsck.k9.activity.K9Activity;
import com.fsck.k9.e3.AppendE3KeyAsyncTask;
import com.fsck.k9.e3.FetchE3KeyAsyncTask;
import com.fsck.k9.helper.Utility;
import com.fsck.k9.mail.e3.E3AliasFunction;
import com.fsck.k9.mail.e3.E3Constants;
import com.fsck.k9.mail.e3.E3Key;
import com.fsck.k9.mail.e3.E3KeyStoreService;
import com.fsck.k9.mail.e3.E3Type;
import com.fsck.k9.mail.e3.E3Utils;
import com.fsck.k9.mail.e3.E3X509PKCS12Generator;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.Closeables;

import org.spongycastle.openssl.PEMWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.ProtectionParameter;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.util.concurrent.ExecutionException;

/**
 * The activity in account setup for configuring E3.
 * <p>
 * Created on 12/12/2017.
 *
 * @author koh
 */
public class AccountSetupE3 extends K9Activity implements OnClickListener, TextWatcher {
    static final String EXTRA_ACCOUNT = "account";
    static final String EXTRA_MAKE_DEFAULT = "makeDefault";
    static final String EXTRA_ACCOUNT_PASSWORD = "accountPassword";
    private final static String STATE_KEY_CHECKED_INCOMING = "com.fsck.k9.AccountSetupE3.checkedIncoming";

    private Account mAccount;
    private EditText mE3PasswordView;
    private EditText mE3PasswordConfirmView;
    private EditText mE3KeyName;
    private EditText mE3BackupFolder;
    private Button mNextButton;
    private CheckBox mShowPasswordCheckBox;
    private boolean mCheckedIncoming = false;
    private String accountPassword;
    private E3X509PKCS12Generator pkcs12Generator;
    private E3KeyStoreService keyStoreService;
    private E3Utils e3Utils;

    public static void actionStartE3Setup(final Context context, final Account account, final
    String password, final boolean makeDefault) {
        final Intent i = new Intent(context, AccountSetupE3.class);
        i.putExtra(EXTRA_ACCOUNT, account.getUuid());
        i.putExtra(EXTRA_MAKE_DEFAULT, makeDefault);
        i.putExtra(EXTRA_ACCOUNT_PASSWORD, password);
        context.startActivity(i);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_setup_e3);
        mE3PasswordView = (EditText) findViewById(R.id.e3_password);
        mE3PasswordConfirmView = (EditText) findViewById(R.id.e3_password_confirm);
        mE3KeyName = (EditText) findViewById(R.id.e3_key_name);
        mE3BackupFolder = (EditText) findViewById(R.id.e3_backup_folder);

        mNextButton = (Button) findViewById(R.id.next);
        mShowPasswordCheckBox = (CheckBox) findViewById(R.id.show_password);
        mNextButton.setOnClickListener(this);

        final String accountUuid = getIntent().getStringExtra(EXTRA_ACCOUNT);
        mAccount = Preferences.getPreferences(this).getAccount(accountUuid);

        accountPassword = getIntent().getStringExtra(EXTRA_ACCOUNT_PASSWORD);
        e3Utils = new E3Utils(getApplicationContext());
        keyStoreService = new E3KeyStoreService(e3Utils, accountUuid);
        pkcs12Generator = new E3X509PKCS12Generator();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        initializeViewListeners();
        validateFields();
    }

    private void initializeViewListeners() {
        mE3PasswordView.addTextChangedListener(this);
        mE3PasswordConfirmView.addTextChangedListener(this);
        mShowPasswordCheckBox.setOnCheckedChangeListener(new CompoundButton
                .OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                showPassword(isChecked);
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mAccount != null) {
            outState.putString(EXTRA_ACCOUNT, mAccount.getUuid());
        }

        outState.putBoolean(STATE_KEY_CHECKED_INCOMING, mCheckedIncoming);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        if (savedInstanceState.containsKey(EXTRA_ACCOUNT)) {
            String accountUuid = savedInstanceState.getString(EXTRA_ACCOUNT);
            mAccount = Preferences.getPreferences(this).getAccount(accountUuid);
        }

        mCheckedIncoming = savedInstanceState.getBoolean(STATE_KEY_CHECKED_INCOMING);

        showPassword(mShowPasswordCheckBox.isChecked());
    }

    private void showPassword(boolean show) {
        int cursorPosition = mE3PasswordView.getSelectionStart();
        int confirmCursorPosition = mE3PasswordConfirmView.getSelectionStart();
        if (show) {
            mE3PasswordView.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
            mE3PasswordConfirmView.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            mE3PasswordView.setInputType(InputType.TYPE_CLASS_TEXT | InputType
                    .TYPE_TEXT_VARIATION_PASSWORD);
            mE3PasswordConfirmView.setInputType(InputType.TYPE_CLASS_TEXT | InputType
                    .TYPE_TEXT_VARIATION_PASSWORD);
        }
        mE3PasswordView.setSelection(cursorPosition);
        mE3PasswordConfirmView.setSelection(confirmCursorPosition);
    }

    // TODO: (E3) Make the password v. e3 password comparison better
    private boolean e3PasswordSufficientlyDifferent(final String e3Password) {
        return !accountPassword.equals(e3Password);
    }

    private void validateFields() {
        final String e3Password = mE3PasswordView.getText().toString();
        final String e3PasswordConfirm = mE3PasswordConfirmView.getText().toString();

        boolean valid = Utility.requiredFieldValid(mE3PasswordView) && Utility.requiredFieldValid
                (mE3PasswordConfirmView) && e3Password.equals(e3PasswordConfirm) &&
                e3PasswordSufficientlyDifferent(e3Password);

        mNextButton.setEnabled(valid);
        //mManualSetupButton.setEnabled(valid);
        //Dim the next button's icon to 50% if the button is disabled.
        Utility.setCompoundDrawablesAlpha(mNextButton, mNextButton.isEnabled() ? 255 : 128);
    }

    private String determineE3KeyName() {
        if (!Strings.isNullOrEmpty(mE3KeyName.getText().toString())) {
            return mE3KeyName.getText().toString();
        } else {
            final Function<String, String> aliasFunction = new E3AliasFunction();
            return aliasFunction.apply(mAccount.getEmail());
        }
    }

    private void finishE3Setup() {
        final String e3KeyName = determineE3KeyName();
        final String e3Password = mE3PasswordView.getText().toString();
        final String e3BackupFolder = mE3BackupFolder.getText().toString();
        final ProtectionParameter protParam = new PasswordProtection(e3Password.toCharArray());

        mAccount.setE3Password(e3Password);
        mAccount.setE3KeyName(e3KeyName);
        mAccount.setE3BackupFolder(e3BackupFolder);

        if (mAccount.getE3Type() == E3Type.PASSIVE) {
            mAccount.setE3EncryptionEnabled(false);
        } else {
            mAccount.setE3EncryptionEnabled(true);
        }

        try {
            // If null, there is no existing key locally.
            if (keyStoreService.getEntry(e3KeyName, protParam) == null) {
                final Optional<E3Key> remoteKey = storeExistingRemoteE3Key(e3BackupFolder, e3KeyName, e3Password);
                final boolean standaloneWithNoRemoteKey = mAccount.getE3Type() == E3Type.STANDALONE && !remoteKey.isPresent();

                // If STANDALONE with no remote key, generate a new key to put remotely.
                // If PASSIVE, generate a key because we don't have one.
                if (mAccount.getE3Type() == E3Type.PASSIVE || standaloneWithNoRemoteKey) {
                    appendKeyRemotely(e3BackupFolder, e3Password);
                }
            }
        } catch (final NoSuchAlgorithmException | UnrecoverableEntryException | KeyStoreException e) {
            throw new RuntimeException("Something went wrong when querying the local key store", e);
        } catch (final IOException e) {
            throw new RuntimeException("Got IOException when creating file for temporary public key storage", e);
        }

        try {
            keyStoreService.store(e3Password);
        } catch (final IOException e) {
            throw new RuntimeException("IOException while trying to write the E3 keystore to disk", e);
        }

        final Optional<File> keyStoreFile = keyStoreService.getStoreFile();
        Preconditions.checkState(keyStoreFile.isPresent(), "KeyStore file is missing");
        mAccount.setE3KeyStorePath(keyStoreFile.get().toString());

        // Check incoming here.  Then check outgoing in onActivityResult()
        AccountSetupCheckSettings.actionCheckSettings(this, mAccount, AccountSetupCheckSettings
                .CheckDirection.INCOMING);
    }

    private void appendKeyRemotely(final String e3BackupFolder, final String e3Password) throws IOException {
        final E3Key keyToAppend = generateKeyInKeyStore(e3Password);
        final Optional<File> pfxFile = keyStoreService.getStoreFile();
        Preconditions.checkState(pfxFile.isPresent(), ".pfx file could not be found " +
                "even though we just generated it?");
        final Resources res = getApplicationContext().getResources();
        final AppendE3KeyAsyncTask putE3KeyAsyncTask;

        if (mAccount.getE3Type() == E3Type.PASSIVE) {
            final File tempPubFile = e3Utils.getTempFile(mAccount.getUuid(), "_pub_key.pem");
            final FileWriter pubFileWriter = new FileWriter(tempPubFile);
            PEMWriter pemWriter = null;
            try {
                pemWriter = new PEMWriter(pubFileWriter);
                pemWriter.writeObject(keyToAppend.getKeyPair().getPublic());
                pemWriter.flush();
            } finally {
                Closeables.close(pemWriter, true);
            }

            putE3KeyAsyncTask = new AppendE3KeyAsyncTask
                    (res, mAccount, e3BackupFolder, tempPubFile, AppendE3KeyAsyncTask.PEM_MIME_TYPE);
        } else {
            putE3KeyAsyncTask = new AppendE3KeyAsyncTask
                    (res, mAccount, e3BackupFolder, pfxFile.get(), AppendE3KeyAsyncTask.PFX_MIME_TYPE);
        }

        putE3KeyAsyncTask.execute(keyToAppend);

        Log.d(E3Constants.LOG_TAG, "Successfully generated and stored E3 key");
    }

    private Optional<E3Key> storeExistingRemoteE3Key(final String e3BackupFolder, final String e3KeyName, final String e3Password) {
        final FetchE3KeyAsyncTask e3KeyAsyncTask = new FetchE3KeyAsyncTask(mAccount,
                e3BackupFolder, e3KeyName, e3Password);
        final Optional<E3Key> e3KeyOptional;

        try {
            e3KeyAsyncTask.execute();
            e3KeyOptional = e3KeyAsyncTask.get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        if (e3KeyOptional.isPresent()) {
            putE3KeyInKeyStore(e3KeyOptional.get());
            Log.d(E3Constants.LOG_TAG, "Successfully retrieved and stored E3 key from " +
                    "user's IMAP account");
        }

        return e3KeyOptional;
    }

    private E3Key generateKeyInKeyStore(final String e3Password) {
        final E3Key generatedKey = doGenerateNewKey(e3Password);
        putE3KeyInKeyStore(generatedKey);

        try {
            // All key generation/retrieval is complete, so write to disk for safekeeping
            keyStoreService.store(e3Password);
        } catch (final IOException e) {
            throw new RuntimeException("Failed to store your E3 key store to disk");
        }

        return generatedKey;
    }

    private void putE3KeyInKeyStore(final E3Key e3Key) {
        // Put the priv key into the store
        try {
            keyStoreService.setKeyEntry(e3Key.getKeyName(), e3Key.getKeyPair().getPrivate(),
                    e3Key.getE3Password(), e3Key.getCertChain());
        } catch (final KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    private E3Key doGenerateNewKey(final String e3Password) {
        final E3Key e3Key;

        try {
            e3Key = pkcs12Generator.generateNewE3Key(mAccount.getEmail(), e3Password);
            Log.d(E3Constants.LOG_TAG, "Generated PKCS12 store with alias: " + e3Key.getKeyName());
        } catch (final Exception e) {
            throw new RuntimeException("Failed to create your E3 key.", e);
        }

        return e3Key;
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void afterTextChanged(Editable editable) {
        validateFields();
    }

    @Override
    public void onClick(final View v) {
        finishE3Setup();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (!mCheckedIncoming) {
                //We've successfully checked incoming.  Now check outgoing.
                mCheckedIncoming = true;
                AccountSetupCheckSettings.actionCheckSettings(this, mAccount,
                        AccountSetupCheckSettings.CheckDirection.OUTGOING);
            } else {
                //We've successfully checked outgoing as well.
                mAccount.setDescription(mAccount.getEmail());
                mAccount.save(Preferences.getPreferences(this));
                K9.setServicesEnabled(this);
                AccountSetupNames.actionSetNames(this, mAccount);
                finish();
            }
        }
    }
}
