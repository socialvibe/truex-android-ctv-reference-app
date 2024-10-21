package com.truex.ctv.referenceapp.player;

public interface PlaybackStateListener {
    void onPlayerDidStart();
    void onPlayerDidResume();
    void onPlayerDidPause();
    void onPlayerDidComplete();
}
