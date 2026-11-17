package app.view.tv;

import java.util.ArrayList;

/** Daten, die zwischen Senderliste und Player geteilt werden. */
final class DataStore {
    private DataStore() {}

    static ArrayList<Xtream.Channel> playList;
    static int playIndex;
    static String playTitle = "";
    /** Position, auf die die Senderliste nach dem Player springt (-1 = nichts). */
    static int returnIndex = -1;
}
