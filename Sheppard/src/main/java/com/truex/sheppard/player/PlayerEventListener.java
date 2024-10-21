package com.truex.sheppard.player;

import android.util.Log;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;

/**
 * This class simply listens for playback events and informs the listeners when any playback events
 * occur. Additionally, this class cancels the video stream when and if any playback errors occur.
 */
public class PlayerEventListener implements Player.Listener {
    private static final String CLASSTAG = PlayerEventListener.class.getSimpleName();

    private PlaybackStateListener listener;
    private boolean playbackDidStart;

    public PlayerEventListener(PlaybackStateListener listener) {
        this.listener = listener;
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        // Just report errors, don't cancel the stream, as there can be spurious MediaCodecVideoRenderer exceptions
        // for simple .mp4 streams, even from videos playing in a web view, not technically even related to
        // the current player instance.
        Log.e(CLASSTAG, "onPlayerError: " + error);
        //playbackHandler.cancelStream();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (isPlaying) {
            if (!playbackDidStart) {
                playbackDidStart = true;
                listener.onPlayerDidStart();
            } else {
                // We have already started, so this is a resume from a pause.
                listener.onPlayerDidResume();
            }
        } else if (playbackDidStart) {
            listener.onPlayerDidPause();
        }
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (playbackState == Player.STATE_ENDED) {
            listener.onPlayerDidComplete();
        }
    }
}
