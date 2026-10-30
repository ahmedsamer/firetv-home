package com.firehome;

/**
 * What Mishary Alafasy is reciting, moment by moment, in the two adhan recordings Home Assistant's "Prayers"
 * automation plays on the Echo (Mashary-Azan.mp3, and Mashary_Fajr.mp3 for Fajr). The TV only shows the words;
 * the sound stays on the Echo.
 *
 * Each phrase runs from where the voice starts to where it stops: the pauses found with ffmpeg's silencedetect
 * (-30 dB, 0.4 s), matched to a Whisper (medium, Arabic, word timestamps) transcript of each file. The pause after a
 * phrase is when it dims.
 */
final class Adhan {
    private static final String TAKBIR = "اللهُ أَكْبَرُ، اللهُ أَكْبَرُ";
    private static final String TAWHID = "أَشْهَدُ أَنْ لَا إِلٰهَ إِلَّا اللهُ";
    private static final String RISALA = "أَشْهَدُ أَنَّ مُحَمَّدًا رَسُولُ اللهِ";
    private static final String SALAH = "حَيَّ عَلَى الصَّلَاةِ";
    private static final String FALAH = "حَيَّ عَلَى الْفَلَاحِ";
    private static final String TATHWIB = "الصَّلَاةُ خَيْرٌ مِنَ النَّوْمِ";   // Fajr only
    private static final String TAHLIL = "لَا إِلٰهَ إِلَّا اللهُ";

    // ms into the recording each phrase's voice starts and stops
    private static final long[] AZAN_FROM = {0, 5400, 20300, 35000, 51200, 67100, 80800, 93600, 104900, 116200,
            129300, 142000};
    private static final long[] AZAN_TO = {3600, 18100, 33400, 49100, 65500, 78400, 92000, 103100, 114500, 126900,
            141400, 152500};
    private static final String[] AZAN = {TAKBIR, TAKBIR, TAWHID, TAWHID, RISALA, RISALA, SALAH, SALAH, FALAH, FALAH,
            TAKBIR, TAHLIL};
    private static final long[] FAJR_FROM = {700, 5900, 20700, 35400, 51700, 67600, 81200, 94000, 105400, 116600,
            129000, 141100, 157500, 169900};
    // the last takbir runs straight into the tahlil with no pause the detector finds; it dims in its last 0.6 s
    private static final long[] FAJR_TO = {4700, 19100, 34500, 49800, 66500, 79600, 92900, 104300, 115400, 127800,
            139900, 156500, 169300, 181600};
    private static final String[] FAJR = {TAKBIR, TAKBIR, TAWHID, TAWHID, RISALA, RISALA, SALAH, SALAH, FALAH, FALAH,
            TATHWIB, TATHWIB, TAKBIR, TAHLIL};

    // the Echo starts the recording this long after the prayer time (Home Assistant's trigger, then Amazon fetching
    // it); checked by ear on 2026-10-30: in time
    static final long START_DELAY_MS = 2000;
    // the last phrase dims over this long once the voice ends, before the next prayer comes back
    private static final long LAST_FADE_MS = 2000;

    private Adhan() {
    }

    /** The phrase whose voice, or the pause after it, is at this many ms into the recording; -1 before and after. */
    static int indexAt(boolean fajr, long ms) {
        long[] from = fajr ? FAJR_FROM : AZAN_FROM;
        if (ms < from[0] || ms >= fadedAt(fajr, from.length - 1)) {
            return -1;
        }
        int i = 0;
        while (i + 1 < from.length && ms >= from[i + 1]) {
            i++;
        }
        return i;
    }

    static String phrase(boolean fajr, int index) {
        return (fajr ? FAJR : AZAN)[index];
    }

    /** Where the voice stops: the phrase starts dimming. */
    static long endsAt(boolean fajr, int index) {
        return (fajr ? FAJR_TO : AZAN_TO)[index];
    }

    /** Where the dimming is over: the next phrase's voice starts, or the last one is done. */
    static long fadedAt(boolean fajr, int index) {
        long[] from = fajr ? FAJR_FROM : AZAN_FROM;
        return index + 1 < from.length ? from[index + 1] : endsAt(fajr, index) + LAST_FADE_MS;
    }

    /** Where the first phrase starts. */
    static long firstAt(boolean fajr) {
        return (fajr ? FAJR_FROM : AZAN_FROM)[0];
    }
}
