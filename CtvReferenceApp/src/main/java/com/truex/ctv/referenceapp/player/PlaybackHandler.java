package com.truex.ctv.referenceapp.player;

public interface PlaybackHandler {
    void resumeStream();
    void closeStream();
    void cancelStream();
    void displayLinearAds();
}
