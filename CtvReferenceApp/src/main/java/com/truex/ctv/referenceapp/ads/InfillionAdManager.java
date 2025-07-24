package com.truex.ctv.referenceapp.ads;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.ViewGroup;

import com.truex.adrenderer.IEventEmitter;
import com.truex.adrenderer.TruexAdEvent;
import com.truex.adrenderer.TruexAdOptions;
import com.truex.adrenderer.TruexAdRenderer;

import java.util.Map;
import java.util.UUID;

/**
 * This class holds a reference to the true[X] ad renderer and handles all of the event handling
 * for the example integration application. This class interacts with the ad pod manager when
 * the engagement is complete.
 * 
 * Supports both true[X] (interactive choice card with credit) and IDVx (interactive without credit) ad types.
 */
public class InfillionAdManager {
    public static boolean supportUserCancelStream = true;

    private static final String CLASSTAG = InfillionAdManager.class.getSimpleName();

    private IEventEmitter.IEventHandler adEventHandler = this::adEventHandler;

    public interface CompletionCallback {
        void onAdComplete(boolean receivedCredit);
    }
    
    private CompletionCallback completionCallback;
    private boolean didReceiveCredit;
    private TruexAdRenderer truexAdRenderer;

    private ViewGroup viewGroup;

    // Default to showing the ad immediately while it is being fetched.
    // The HTML5 TAR shows a black screen with a spinner in this case, which is appropriate
    // for most publisher user situations.
    private static final boolean showAdImmediately = true;
    private static final boolean showAdAfterLoad = !showAdImmediately;

    public InfillionAdManager(Context context, CompletionCallback completionCallback) {
        this.completionCallback = completionCallback;

        didReceiveCredit = false;

        // Set-up the true[X] ad renderer
        truexAdRenderer = new TruexAdRenderer(context);

        // Set-up the event listeners
        truexAdRenderer.addEventListener(null, adEventHandler); // listen to all events.
        if (supportUserCancelStream) {
            // We use an explicit listener to allow the tar to know user cancel stream is supported.
            truexAdRenderer.addEventListener(TruexAdEvent.USER_CANCEL_STREAM, this::onCancelStream);
        }
    }

    /**
     * Start displaying the Infillion engagement (true[X] or IDVx)
     * @param viewGroup - the view group in which you would like to display the engagement
     * @param vastConfigUrl - the VAST config URL for the ad
     * @param adType - the type of ad (TRUEX or IDVX)
     */
    public void startAd(ViewGroup viewGroup, String vastConfigUrl, AdType adType) {
        Log.d(CLASSTAG, "startAd called - ViewGroup: " + viewGroup + ", URL: " + vastConfigUrl + ", Type: " + adType);
        this.viewGroup = viewGroup;

        TruexAdOptions options = new TruexAdOptions();
        // Only true[X] ads support user cancel stream, IDVx ads should not
        options.supportsUserCancelStream = (adType == AdType.TRUEX) && supportUserCancelStream;
        //options.userAdvertisingId = "1234"; // for testing.
        options.fallbackAdvertisingId = UUID.randomUUID().toString();

        Log.d(CLASSTAG, "Calling truexAdRenderer.init()");
        truexAdRenderer.init(vastConfigUrl, options);
        if (showAdImmediately) {
            Log.d(CLASSTAG, "Calling truexAdRenderer.start() with ViewGroup: " + viewGroup);
            truexAdRenderer.start(viewGroup);
        } else {
            Log.d(CLASSTAG, "showAdImmediately is false, not starting renderer yet");
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
    
    /**
     * Cleanup and destroy the TruexAdManager
     * Should be called when the ad is complete or when disposing
     */
    public void destroy() {
        Log.d(CLASSTAG, "Destroying InfillionAdManager");
        if (truexAdRenderer != null) {
            truexAdRenderer.removeEventListener(null, adEventHandler);
            truexAdRenderer = null;
        }
        completionCallback = null;
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
                // Notify completion with credit status
                completionCallback.onAdComplete(didReceiveCredit);
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
     * This method should be called if the user has opted to cancel the current stream
     */
    private void onCancelStream(TruexAdEvent event, Map<String, ?> data) {
        if (didReceiveCredit) {
            Log.i(CLASSTAG, "Cancelling stream with credit");
        } else {
            Log.i(CLASSTAG, "Cancelling stream without credit");
        }
        if (completionCallback == null) {
            return;
        }

        // For stream cancellation, we don't provide credit
        completionCallback.onAdComplete(false);
    }

}
