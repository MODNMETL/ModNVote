package com.modnmetl.modnvote.service;

import java.security.SecureRandom;

/**
 * Generates the human-usable private ballot proof phrase shown to a voter once
 * at submission time.
 *
 * <p>This is the single source of truth for proof-phrase <em>generation</em>
 * semantics. It was extracted verbatim from {@link BallotService} so the
 * single-contest (YES_NO / RANKED_SINGLE_WINNER) path and the linked-offices
 * submission path produce phrases the same way and can never drift apart — the
 * same reasoning the codebase already applies to {@code BallotHashingService}.
 *
 * <p>Semantics (unchanged): four words drawn uniformly at random from a fixed
 * curated word list, joined by {@code '-'}. Only one-way verifier material is
 * persisted; anyone who later learns the phrase can reveal that ballot's
 * contents, so callers must warn the voter not to share it.
 */
public final class BallotProofPhraseGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final int WORD_COUNT = 4;

    /**
     * Curated word list for proof phrases. Identical to the list that previously
     * lived in {@link BallotService}; do not reorder or change it without also
     * accepting that newly generated phrases use the new list (already-stored
     * verifier hashes are unaffected because only the hash is persisted).
     */
    private static final String[] BALLOT_PROOF_WORDS = {
            "amber","apple","arch","ash","atlas","aurora","badger","bamboo","barley","beacon","berry","birch",
            "blossom","bluejay","brook","cedar","chalk","cherry","cinder","clover","cobalt","comet","coral","copper",
            "cotton","cove","crystal","daisy","delta","drift","dune","ember","falcon","fern","field","flint",
            "forest","fox","frost","garden","glade","glow","granite","harbor","hazel","heather","hollow","horizon",
            "indigo","iris","ivory","jade","jetty","juniper","keystone","lagoon","lantern","laurel","leaf","linen",
            "lilac","maple","marble","marsh","meadow","meteor","mist","monarch","moon","moss","mountain","mulberry",
            "oasis","ocean","olive","opal","orchard","otter","pearl","pine","plains","plum","prairie","quartz",
            "quill","raven","reed","river","robin","rose","saffron","sage","sail","sand","scarlet","shore",
            "silver","sky","snow","solstice","sparrow","spruce","star","stone","storm","summit","sunrise","thistle",
            "timber","topaz","trail","valley","velvet","violet","willow","wind","winter","wren","yarrow","zephyr"
    };

    /**
     * @return a fresh four-word, hyphen-joined proof phrase
     */
    public String generate() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < WORD_COUNT; i++) {
            if (i > 0) {
                sb.append('-');
            }
            sb.append(BALLOT_PROOF_WORDS[SECURE_RANDOM.nextInt(BALLOT_PROOF_WORDS.length)]);
        }
        return sb.toString();
    }
}
