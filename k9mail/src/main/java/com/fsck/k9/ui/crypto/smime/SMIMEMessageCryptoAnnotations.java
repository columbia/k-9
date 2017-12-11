package com.fsck.k9.ui.crypto.smime;

import com.fsck.k9.mail.Part;
import com.fsck.k9.mailstore.SMIMECryptoResultAnnotation;

import java.util.HashMap;

/**
 * Created by mauzel on 12/11/2017.
 */

public class SMIMEMessageCryptoAnnotations {
    private HashMap<Part, SMIMECryptoResultAnnotation> annotations = new HashMap<>();

    public void put(Part part, SMIMECryptoResultAnnotation annotation) {
        annotations.put(part, annotation);
    }

    public SMIMECryptoResultAnnotation get(Part part) {
        return annotations.get(part);
    }

    public boolean has(Part part) {
        return annotations.containsKey(part);
    }

    public boolean isEmpty() {
        return annotations.isEmpty();
    }

    public Part findKeyForAnnotationWithReplacementPart(Part part) {
        for (HashMap.Entry<Part, SMIMECryptoResultAnnotation> entry : annotations.entrySet()) {
            if (part == entry.getValue().getReplacementData()) {
                return entry.getKey();
            }
        }
        return null;
    }
}
