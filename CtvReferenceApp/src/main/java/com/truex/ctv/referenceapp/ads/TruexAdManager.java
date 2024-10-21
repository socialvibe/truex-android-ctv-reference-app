package com.truex.ctv.referenceapp.ads;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.ViewGroup;

import com.truex.adrenderer.TruexAdEvent;
import com.truex.adrenderer.TruexAdOptions;
import com.truex.adrenderer.TruexAdRenderer;
import com.truex.ctv.referenceapp.player.PlaybackHandler;

import java.util.Map;
import java.util.UUID;

/**
 * This class holds a reference to the true[X] ad renderer and handles all of the event handling
 * for the example integration application. This class interacts with the video player by resuming
 * the content when the engagement is complete.
 */
public class TruexAdManager {
    public static boolean supportUserCancelStream = true;

    private static final String CLASSTAG = TruexAdManager.class.getSimpleName();

    private PlaybackHandler playbackHandler;
    private boolean didReceiveCredit;
    private TruexAdRenderer truexAdRenderer;

    private ViewGroup viewGroup;

    // Default to showing the ad immediately while it is being fetched.
    // The HTML5 TAR shows a black screen with a spinner in this case, which is appropriate
    // for most publisher user situations.
    private static final boolean showAdImmediately = true;
    private static final boolean showAdAfterLoad = !showAdImmediately;

    public TruexAdManager(Context context, PlaybackHandler playbackHandler) {
        this.playbackHandler = playbackHandler;

        didReceiveCredit = false;

        // Set-up the true[X] ad renderer
        truexAdRenderer = new TruexAdRenderer(context);

        // Set-up the event listeners
        truexAdRenderer.addEventListener(null, this::adEventHandler); // listen to all events.
        if (supportUserCancelStream) {
            // We use an explicit listener to allow the tar to know user cancel stream is supported.
            truexAdRenderer.addEventListener(TruexAdEvent.USER_CANCEL_STREAM, this::onCancelStream);
        }
    }

    /**
     * Start displaying the true[X] engagement
     * @param viewGroup - the view group in which you would like to display the true[X] engagement
     */
    public void startAd(ViewGroup viewGroup, String vastConfigUrl) {
        this.viewGroup = viewGroup;

        TruexAdOptions options = new TruexAdOptions();
        options.supportsUserCancelStream = true;
        //options.userAdvertisingId = "1234"; // for testing.
        options.fallbackAdvertisingId = UUID.randomUUID().toString();

        truexAdRenderer.init(vastConfigUrl, options);
        if (showAdImmediately) {
            truexAdRenderer.start(viewGroup);
        }
    }

    /**
     * Inform the true[X] ad renderer that the application has resumed
     */
    public void onResume() {
        truexAdRenderer.resume();
    }


    /**
     * Inform the true[X] ad renderer that the application has paused
     */
    public void onPause() {
        truexAdRenderer.pause();
    }

    /**
     * Inform that the true[X] ad renderer that the application has stopped
     */
    public void onStop() {
        truexAdRenderer.stop();
    }

    private void adEventHandler(TruexAdEvent event, Map<String, ?> data) {
        Log.i(CLASSTAG, "ad event: " + event);
        switch (event) {
            case AD_STARTED:
                // The ad has started.
                break;

            case SKIP_CARD_SHOWN:
                // The skip card was shown instead of an ad.
                break;

            case AD_DISPLAYED:
                if (showAdAfterLoad) {
                    // Ad is ready to be shown.
                    Handler handler = new Handler();
                    handler.post(() -> truexAdRenderer.start(viewGroup));
                }
                break;

            case USER_CANCEL_STREAM:
                // User backed out of the choice card, which means backing out of the entire video.
                // The user would like to cancel the stream
                // Handled below in onCancelStream()
                return;

            case AD_ERROR: // An ad error has occurred, forcing its closure
            case AD_COMPLETED: // The ad has completed.
            case NO_ADS_AVAILABLE: // No ads are available, resume playback of fallback ads.
                resumeVideo();
                break;

            case AD_FREE_POD:
                // the user did sufficient interaction for an ad credit
                didReceiveCredit = true;
                break;

            case OPT_IN:
                // User started the engagement experience
            case OPT_OUT:
                // User cancelled out of the choice card, either explicitly, or implicitly via a timeout.
            case USER_CANCEL:
                // User backed out of the ad, now showing the choice card again.
            default:
                break;
        }
    }

    /**
     * This method should be called once the true[X] ad manager is done
     */
    private void resumeVideo() {
        if (didReceiveCredit) {
            // The user received true[ATTENTION] credit
            // Resume the content stream (and skip any linear ads)
            playbackHandler.resumeStream();
        } else {
            // The user did not receive credit
            // Continue the content stream and display linear ads
            playbackHandler.displayLinearAds();
        }
    }

    /**
     * This method should be called if the user has opted to cancel the current stream
     */
    private void onCancelStream(TruexAdEvent event, Map<String, ?> data) {
        if (didReceiveCredit) {
            Log.i(CLASSTAG, "Cancelling stream with credit");
        } else {
            Log.i(CLASSTAG, "Cancelling stream without credit");
        }
        playbackHandler.cancelStream();
    }

}
