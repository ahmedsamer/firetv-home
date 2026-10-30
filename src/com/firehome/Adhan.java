package com.firehome;

/**
 * What Mishary Alafasy is reciting, moment by moment, in the two adhan recordings Home Assistant's "Prayers"
 * automation plays on the Echo (Mashary-Azan.mp3, and Mashary_Fajr.mp3 for Fajr). The TV only shows the words;
 * the sound stays on the Echo.
 *
 * Each phrase starts where the muezzin draws breath before it: the pauses found with ffmpeg's silencedetect,
 * matched to a Whisper (medium, Arabic, word timestamps) transcript of each file.
 */
final class Adhan {
    private static final String TAKBIR = "اللهُ أَكْبَرُ، اللهُ أَكْبَرُ";
    private static final String TAWHID = "أَشْهَدُ أَنْ لَا إِلٰهَ إِلَّا اللهُ";
    private static final String RISALA = "أَشْهَدُ أَنَّ مُحَمَّدًا رَسُولُ اللهِ";
    private static final String SALAH = "حَيَّ عَلَى الصَّلَاةِ";
    private static final String FALAH = "حَيَّ عَلَى الْفَلَاحِ";
    private static final String TATHWIB = "الصَّلَاةُ خَيْرٌ مِنَ النَّوْمِ";   // Fajr only
    private static final String TAHLIL = "لَا إِلٰهَ إِلَّا اللهُ";

    // ms into the recording each phrase starts; the last entry is where the voice ends
    private static final long[] AZAN_AT = {0, 5400, 20300, 35000, 51200, 67100, 80800, 93600, 104900, 116200,
            129300, 142000, 152500};
    private static final String[] AZAN = {TAKBIR, TAKBIR, TAWHID, TAWHID, RISALA, RISALA, SALAH, SALAH, FALAH, FALAH,
            TAKBIR, TAHLIL};
    private static final long[] FAJR_AT = {700, 5900, 20700, 35400, 51700, 67600, 81200, 94000, 105400, 116600,
            129000, 141100, 157500, 169900, 181600};
    private static final String[] FAJR = {TAKBIR, TAKBIR, TAWHID, TAWHID, RISALA, RISALA, SALAH, SALAH, FALAH, FALAH,
            TATHWIB, TATHWIB, TAKBIR, TAHLIL};

    // the Echo starts the recording this long after the prayer time (Home Assistant's trigger, then Amazon fetching it)
    static final long START_DELAY_MS = 2000;
    // the last phrase stays a moment after the voice ends
    private static final long HOLD_MS = 2500;

    private Adhan() {
    }

    /**
     * The phrase being recited this many ms into the recording, as its place in the recitation (a repeated phrase is
     * a new place), or -1 before the first and once the last is over.
     */
    static int indexAt(boolean fajr, long ms) {
        long[] at = fajr ? FAJR_AT : AZAN_AT;
        if (ms < at[0] || ms >= at[at.length - 1] + HOLD_MS) {
            return -1;
        }
        int i = 0;
        while (i + 2 < at.length && ms >= at[i + 1]) {
            i++;
        }
        return i;
    }

    static String phrase(boolean fajr, int index) {
        return (fajr ? FAJR : AZAN)[index];
    }

    /** ms into the recording at which the next change comes (a phrase, or the end), or -1 when there is none. */
    static long nextChange(boolean fajr, long ms) {
        long[] at = fajr ? FAJR_AT : AZAN_AT;
        for (int i = 0; i < at.length - 1; i++) {
            if (at[i] > ms) {
                return at[i];
            }
        }
        long end = at[at.length - 1] + HOLD_MS;
        return end > ms ? end : -1;
    }
}
