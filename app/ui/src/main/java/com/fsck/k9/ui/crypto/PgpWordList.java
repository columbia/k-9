package com.fsck.k9.ui.crypto;



import android.content.Context;

import com.fsck.k9.ui.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PgpWordList {

    private static List<String> EVEN_WORDS = new ArrayList<>();

    public PgpWordList(Context context) {
        EVEN_WORDS = Arrays.asList(context.getResources().getString(R.string.pgp_word_list_even).split(" "));
    }

    public List<String> getRandomWords(final int size) {
        if (size <= 0 || size > EVEN_WORDS.size()) {
            throw new IllegalArgumentException("invalid size value: " + size);
        }

        Collections.shuffle(EVEN_WORDS);

        return EVEN_WORDS.subList(0, size);
    }
}
