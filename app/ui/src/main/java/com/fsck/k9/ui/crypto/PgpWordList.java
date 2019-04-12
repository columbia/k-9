package com.fsck.k9.ui.crypto;



import android.content.Context;

import com.fsck.k9.ui.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class PgpWordList {

    private static List<String> EVEN_WORDS = new ArrayList<>();
    private static List<String> ODD_WORDS = new ArrayList<>();
    private final Random random;

    public PgpWordList(Context context) {
        EVEN_WORDS = Arrays.asList(context.getResources().getString(R.string.pgp_word_list_even).split(" "));
        ODD_WORDS = Arrays.asList(context.getResources().getString(R.string.pgp_word_list_odd).split(" "));

        if (EVEN_WORDS.size() != ODD_WORDS.size()) {
            throw new IllegalStateException("Got invalid PGP word list sizes");
        }

        random = new Random();
    }

    public List<String> getRandomWords(final int size) {
        if (size <= 0 || size > EVEN_WORDS.size()) {
            throw new IllegalArgumentException("invalid size value: " + size);
        }

        final List<String> selectedWords = new ArrayList<>();

        int i = 0;
        while (i < size) {
            int j = random.nextInt(EVEN_WORDS.size());
            selectedWords.add(i % 2 == 0 ? EVEN_WORDS.get(j) : ODD_WORDS.get(j));
            ++i;
        }

        return selectedWords;
    }
}
