package com.fsck.k9;

import com.google.common.base.MoreObjects;

import java.io.Serializable;

public class Identity implements Serializable {
    private static final long serialVersionUID = -1666669071480985760L;

    private String description;
    private String name;
    private String email;
    private String signature;
    private boolean signatureUse;
    private String replyTo;
    private String e3Password;
    private String e3KeyName;
    private String e3BackupFolder;

    public synchronized String getName() {
        return name;
    }

    public synchronized void setName(String name) {
        this.name = name;
    }

    public synchronized String getEmail() {
        return email;
    }

    public synchronized void setEmail(String email) {
        this.email = email;
    }

    public synchronized boolean getSignatureUse() {
        return signatureUse;
    }

    public synchronized void setSignatureUse(boolean signatureUse) {
        this.signatureUse = signatureUse;
    }

    public synchronized String getSignature() {
        return signature;
    }

    public synchronized void setSignature(String signature) {
        this.signature = signature;
    }

    public synchronized String getDescription() {
        return description;
    }

    public synchronized void setDescription(String description) {
        this.description = description;
    }

    public synchronized String getReplyTo() {
        return replyTo;
    }

    public synchronized void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public String getE3Password() {
        return e3Password;
    }

    public void setE3Password(String e3Password) {
        this.e3Password = e3Password;
    }

    public String getE3KeyName() {
        return e3KeyName;
    }

    public void setE3KeyName(String e3KeyName) {
        this.e3KeyName = e3KeyName;
    }

    public String getE3BackupFolder() {
        return e3BackupFolder;
    }

    public void setE3BackupFolder(String e3BackupFolder) {
        this.e3BackupFolder = e3BackupFolder;
    }

    @Override
    public synchronized String toString() {
        return MoreObjects.toStringHelper(this)
                .add("description", description)
                .add("name", name)
                .add("email", email)
                .add("signature", signature)
                .add("signatureUse", signatureUse)
                .add("replyTo", replyTo)
                .add("e3KeyName", e3KeyName)
                .add("e3BackupFolder", e3BackupFolder)
                .toString();
    }
}
