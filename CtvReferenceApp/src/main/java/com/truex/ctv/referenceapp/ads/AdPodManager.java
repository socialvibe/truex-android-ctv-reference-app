package com.truex.ctv.referenceapp.ads;

import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.datasource.DataSource;

public class AdPodManager {
    private static final String CLASSTAG = AdPodManager.class.getSimpleName();
    
    private List<AdPodItem> adPod;
    private List<AdSegment> adSegments;
    private int currentSegmentIndex;
    private int currentAdIndexInSegment;
    private boolean truexCreditReceived;
    private AdPodListener listener;
    private Context context;
    private InfillionAdManager infillionAdManager;
    private DataSource.Factory dataSourceFactory;
    private ViewGroup adViewGroup;
    private android.os.Handler failsafeHandler;
    private Runnable failsafeRunnable;
    
    private static class AdSegment {
        List<AdPodItem> ads;
        MediaSource mediaSource;
        boolean isConcatenated;
        
        AdSegment(List<AdPodItem> ads, MediaSource mediaSource, boolean isConcatenated) {
            this.ads = ads;
            this.mediaSource = mediaSource;
            this.isConcatenated = isConcatenated;
        }
    }
    
    public interface AdPodListener {
        void playMediaSource(MediaSource mediaSource, boolean notifyOfCompletion);
        void controlPlayer(PlayerAction action, long seekPositionMs);
        void onAdPodComplete();
        void onSkipToContent();
    }
    
    public enum PlayerAction {
        PLAY,
        SEEK_AND_PAUSE
    }
    
    public AdPodManager(Context context, AdPodListener listener, DataSource.Factory dataSourceFactory, ViewGroup adViewGroup) {
        this.context = context;
        this.listener = listener;
        this.dataSourceFactory = dataSourceFactory;
        this.adViewGroup = adViewGroup;
        this.adPod = new ArrayList<>();
        this.adSegments = new ArrayList<>();
        this.currentSegmentIndex = 0;
        this.currentAdIndexInSegment = 0;
        this.truexCreditReceived = false;
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
    public void setAdPod(List<AdPodItem> adPod) {
        // Clean up any existing InfillionAdManager before setting new ad pod
        cleanupInfillionAdManager();
        
        this.adPod = adPod;
        this.adSegments = createAdSegments(adPod);
        this.currentSegmentIndex = 0;
        this.currentAdIndexInSegment = 0;
        this.truexCreditReceived = false;
    }
    
    public void startAdPod() {
        // Clean up any existing InfillionAdManager before starting new ad pod
        cleanupInfillionAdManager();
        
        currentSegmentIndex = 0;
        currentAdIndexInSegment = 0;
        truexCreditReceived = false;
        playNextSegment();
    }

    public boolean isPlayingInteractiveAd() {
        AdPodItem currentAd = getCurrentAd();
        return currentAd != null && currentAd.isInfillionAd();
    }

    // You should call this from outside when a concatenated
    // segment finishes playing
    public void onPlaybackEnded() {
        currentSegmentIndex++;
        playNextSegment();
    }

    // You should call this from outside when the player
    // transitions to a new ad in a concatenated segment
    public void onMediaItemCompleted() {
        AdPodItem currentAd = getCurrentAd();
        if (currentAd == null) {
            return;
        }

        moveToNextAd();
    }

    private AdSegment getCurrentSegment() {
        if (currentSegmentIndex < adSegments.size()) {
            return adSegments.get(currentSegmentIndex);
        }
        return null;
    }
    
    private AdPodItem getCurrentAd() {
        AdSegment currentSegment = getCurrentSegment();
        if (currentSegment != null && currentAdIndexInSegment < currentSegment.ads.size()) {
            return currentSegment.ads.get(currentAdIndexInSegment);
        }
        return null;
    }

    private void playNextSegment() {
        if (currentSegmentIndex >= adSegments.size()) {
            listener.onAdPodComplete();
            return;
        }

        AdSegment segment = adSegments.get(currentSegmentIndex);
        currentAdIndexInSegment = 0;

        boolean notifyOfCompletion = segment.isConcatenated;
        listener.playMediaSource(segment.mediaSource, notifyOfCompletion);

        launchInfillionOverlayIfNecessary();
    }

    // Note that his method does not drive the ads - it  only:
    //  1. advances the internal state to reflect the progress made by the player
    //  2. shows the Infillion overlay if an Infillion ad is playing
    private void moveToNextAd() {
        AdSegment currentSegment = getCurrentSegment();
        if (currentSegment == null) {
            return;
        }
        
        if (currentSegment.isConcatenated) {
            // Move to next ad in concatenated segment
            currentAdIndexInSegment++;
            if (currentAdIndexInSegment >= currentSegment.ads.size()) {
                // Segment complete, move to next segment
                currentSegmentIndex++;
                playNextSegment();
            }
            else {
                // show the renderer if the new ad is Infillion
                launchInfillionOverlayIfNecessary();
            }
        } else {
            // Individual TrueX ad complete, move to next segment
            currentSegmentIndex++;
            playNextSegment();
        }
    }
    
    private void showInfillionRenderer(AdPodItem adItem) {
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
        infillionAdManager.startAd(adViewGroup, adItem.getVastConfigUrl(), adItem.getAdType());
        
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
    
    private List<AdSegment> createAdSegments(List<AdPodItem> adPod) {
        List<AdSegment> segments = new ArrayList<>();
        
        if (adPod.isEmpty()) {
            return segments;
        }
        
        // Check if first ad is TrueX (if any TrueX exists, it's always first)
        if (adPod.get(0).getAdType() == AdType.TRUEX) {
            // Add TrueX as individual segment
            segments.add(createIndividualSegment(adPod.get(0)));
            
            // Add remaining ads as concatenated segment (if any)
            if (adPod.size() > 1) {
                List<AdPodItem> remainingAds = adPod.subList(1, adPod.size());
                segments.add(createConcatenatedSegment(remainingAds));
            }
        } else {
            // All ads are regular/IDVx - create single concatenated segment
            segments.add(createConcatenatedSegment(adPod));
        }
        
        return segments;
    }

    @OptIn(markerClass = UnstableApi.class)
    private AdSegment createConcatenatedSegment(List<AdPodItem> ads) {
        ConcatenatingMediaSource2.Builder builder = new ConcatenatingMediaSource2.Builder()
            .useDefaultMediaSourceFactory(context);
        
        for (AdPodItem ad : ads) {
            MediaItem mediaItem = MediaItem.fromUri(ad.getAdUrl());
            // Add with placeholder duration to handle loading times
            builder.add(mediaItem, ad.getDuration() * 1000L);
        }
        
        MediaSource concatenatedSource = builder.build();
        return new AdSegment(ads, concatenatedSource, true);
    }

    @OptIn(markerClass = UnstableApi.class)
    private AdSegment createIndividualSegment(AdPodItem ad) {
        MediaItem mediaItem = MediaItem.fromUri(ad.getAdUrl());
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem);
        
        List<AdPodItem> singleAdList = new ArrayList<>();
        singleAdList.add(ad);
        return new AdSegment(singleAdList, mediaSource, false);
    }

    private void launchInfillionOverlayIfNecessary() {
        AdPodItem currentAd = getCurrentAd();
        if (currentAd == null || !currentAd.isInfillionAd()) {
            return;
        }

        // Seek to just before the end of the IDVx placeholder video and pause
        long endPosition = calculateEndPositionOfCurrentAd();
        listener.controlPlayer(PlayerAction.SEEK_AND_PAUSE, endPosition - 100);
        showInfillionRenderer(currentAd);
    }

    private void onInfillionAdComplete(boolean receivedCredit) {
        Log.d(CLASSTAG, "Infillion ad complete, credit: " + receivedCredit);

        // Cancel failsafe timer if running
        cancelFailsafeTimer();

        // Clean up the completed InfillionAdManager
        cleanupInfillionAdManager();

        // For IDVx ads in concatenated segments, resume the player.
        // The Infillion placeholder video had been paused at T-100 ms
        // When it's finished, the player will trigger moveToNextAd()
        // and this will finalize the transition to the next ad.
        AdPodItem currentAd = getCurrentAd();
        if (currentAd != null && currentAd.getAdType() == AdType.IDVX) {
            AdSegment currentSegment = getCurrentSegment();
            if (currentSegment != null && currentSegment.isConcatenated) {
                listener.controlPlayer(PlayerAction.PLAY, 0);
                return;
            }
        }

        // true[X] credit earned - skip remaining ads and return to content
        if (receivedCredit) {
            truexCreditReceived = true;
            listener.onSkipToContent();
            return;
        }

        // true[X] but no credit earned - show more ads
        moveToNextAd();
    }
    
    private long calculateEndPositionOfCurrentAd() {
        AdSegment currentSegment = getCurrentSegment();
        if (currentSegment == null || !currentSegment.isConcatenated) {
            return 0;
        }
        
        // Calculate position where current ad ends in concatenated timeline
        long positionMs = 0;
        for (int i = 0; i <= currentAdIndexInSegment; i++) {
            if (i < currentSegment.ads.size()) {
                positionMs += currentSegment.ads.get(i).getDuration() * 1000L;
            }
        }
        
        return positionMs;
    }
    
    private void startFailsafeTimer(AdPodItem idvxAd) {
        // Create failsafe timer for 2x the ad duration
        long failsafeTimeoutMs = idvxAd.getDuration() * 2000L;
        
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