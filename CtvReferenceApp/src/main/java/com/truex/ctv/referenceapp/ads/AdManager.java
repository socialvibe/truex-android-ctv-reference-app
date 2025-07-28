package com.truex.ctv.referenceapp.ads;

import android.content.Context;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2;
import androidx.media3.exoplayer.source.MediaSource;

public class AdManager {
    private static final String CLASSTAG = AdManager.class.getSimpleName();

    private List<Ad> ads;
    private MediaSource mediaSource;
    private int currentAdIndexInSegment;
    private AdBreadListener listener;
    private Context context;
    private InfillionAdManager infillionAdManager;
    private ViewGroup adViewGroup;
    private android.os.Handler failsafeHandler;
    private Runnable failsafeRunnable;
    
    public interface AdBreadListener {
        void playMediaSource(MediaSource mediaSource);
        void controlPlayer(PlayerAction action, long seekPositionMs);
        void onAdBreadComplete();
        void onSkipToContent();
    }
    
    public enum PlayerAction {
        PLAY,
        SEEK_AND_PAUSE
    }
    
    public AdManager(Context context, AdBreadListener listener, ViewGroup adViewGroup) {
        this.context = context;
        this.listener = listener;
        this.adViewGroup = adViewGroup;
        this.ads = new ArrayList<>();
        this.currentAdIndexInSegment = 0;
        this.failsafeHandler = new android.os.Handler();
    }

    // Lifecycle methods to forward to InfillionAdManager
    public void onResume() {
        if (infillionAdManager != null) {
            infillionAdManager.onResume();
        }
    }

    public void onPause() {
        if (infillionAdManager != null) {
            infillionAdManager.onPause();
        }
    }

    public void onStop() {
        if (infillionAdManager != null) {
            infillionAdManager.onStop();
        }
        // Clean up when stopping to prevent memory leaks
        cleanupInfillionAdManager();
    }

    // ad pod set up and management
    public void setCurrentAdBreak(List<Ad> ads) {
        // Clean up any existing InfillionAdManager before setting new ad pod
        cleanupInfillionAdManager();
        
        this.ads = ads;
        this.currentAdIndexInSegment = 0;
        this.mediaSource = createMediaSource(ads);
    }
    
    public void startAdBread() {
        // Clean up any existing InfillionAdManager before starting new ad pod
        cleanupInfillionAdManager();

        currentAdIndexInSegment = 0;

        listener.playMediaSource(mediaSource);
        launchInfillionOverlayIfNecessary();
    }

    public boolean isPlayingInteractiveAd() {
        Ad currentAd = getCurrentAd();
        return currentAd != null && currentAd.isInfillionAd();
    }

    // You should call this from outside when a concatenated
    // segment finishes playing
    public void onPlaybackEnded() {
        listener.onAdBreadComplete();
    }

    // You should call this from outside when the player
    // transitions to a new ad in a concatenated segment
    public void onMediaItemCompleted() {
        Ad currentAd = getCurrentAd();
        if (currentAd == null) {
            return;
        }

        moveToNextAd();
    }
    
    private Ad getCurrentAd() {
        if (currentAdIndexInSegment < ads.size()) {
            return ads.get(currentAdIndexInSegment);
        }
        return null;
    }

    // Note that this method does not drive the ads - it  only:
    //  1. advances the internal state to reflect the progress made by the player
    //  2. shows the Infillion overlay if an Infillion ad is playing
    private void moveToNextAd() {
        // Move to next ad in concatenated segment
        currentAdIndexInSegment++;
        if (currentAdIndexInSegment >= ads.size()) {
            listener.onAdBreadComplete();
        }
        else {
            // show the renderer if the new ad is Infillion
            launchInfillionOverlayIfNecessary();
        }
    }
    
    private void showInfillionRenderer(Ad adItem) {
        if (adViewGroup == null) {
            onInfillionAdComplete(false);
            return;
        }
        
        // Clean up any existing InfillionAdManager before creating a new one
        cleanupInfillionAdManager();
        
        InfillionAdManager.CompletionCallback callback = new InfillionAdManager.CompletionCallback() {
            @Override
            public void onAdComplete(boolean receivedCredit) {
                onInfillionAdComplete(receivedCredit);
            }
        };

        infillionAdManager = new InfillionAdManager(context, callback);
        infillionAdManager.startAd(adViewGroup, adItem.vastConfigUrl, adItem.adType);
        
        // Start failsafe timer for IDVx ads (2x duration)
        if (adItem.isInfillionAd()) {
            startFailsafeTimer(adItem);
        }
    }
    
    private void cleanupInfillionAdManager() {
        if (infillionAdManager != null) {
            infillionAdManager.destroy();
            infillionAdManager = null;
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private MediaSource createMediaSource(List<Ad> ads) {
        ConcatenatingMediaSource2.Builder builder = new ConcatenatingMediaSource2.Builder()
            .useDefaultMediaSourceFactory(context);
        
        for (Ad ad : ads) {
            MediaItem mediaItem = MediaItem.fromUri(ad.adUrl);
            // Add with placeholder duration to handle loading times
            builder.add(mediaItem, ad.duration * 1000L);
        }
        
        return builder.build();
    }

    private void launchInfillionOverlayIfNecessary() {
        Ad currentAd = getCurrentAd();
        if (currentAd == null || !currentAd.isInfillionAd()) {
            return;
        }

        // Seek to just before the end of the IDVx placeholder video and pause
        long endPosition = calculateEndPositionOfCurrentAd();
        listener.controlPlayer(PlayerAction.SEEK_AND_PAUSE, endPosition - 100);
        showInfillionRenderer(currentAd);
    }

    private void onInfillionAdComplete(boolean receivedCredit) {
        // Cancel failsafe timer if running
        cancelFailsafeTimer();

        // Clean up the completed InfillionAdManager
        cleanupInfillionAdManager();

        if (receivedCredit) {
            // true[X] credit earned - skip remaining ads and return to content
            listener.onSkipToContent();
        }
        else {
            // Resume playback - the player will trigger moveToNextAd()
            // and this will finalize the transition to the next ad.
            listener.controlPlayer(PlayerAction.PLAY, 0);
        }
    }
    
    private long calculateEndPositionOfCurrentAd() {
        // Calculate position where current ad ends in concatenated timeline
        long positionMs = 0;
        for (int i = 0; i <= currentAdIndexInSegment; i++) {
            if (i < ads.size()) {
                positionMs += ads.get(i).duration * 1000L;
            }
        }
        
        return positionMs;
    }
    
    private void startFailsafeTimer(Ad idvxAd) {
        // Create failsafe timer for 2x the ad duration
        long failsafeTimeoutMs = idvxAd.duration * 2000L;
        
        failsafeRunnable = () -> {
            // Force completion without credit
            onInfillionAdComplete(false);
        };
        
        failsafeHandler.postDelayed(failsafeRunnable, failsafeTimeoutMs);
    }
    
    private void cancelFailsafeTimer() {
        if (failsafeHandler != null && failsafeRunnable != null) {
            failsafeHandler.removeCallbacks(failsafeRunnable);
            failsafeRunnable = null;
        }
    }
}