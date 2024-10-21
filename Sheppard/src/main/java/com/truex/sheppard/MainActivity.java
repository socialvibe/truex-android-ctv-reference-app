package com.truex.sheppard;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.OptIn;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSourceFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

import com.truex.sheppard.ads.TruexAdManager;
import com.truex.sheppard.player.DisplayMode;
import com.truex.sheppard.player.PlaybackHandler;
import com.truex.sheppard.player.PlaybackStateListener;
import com.truex.sheppard.player.PlayerEventListener;

@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends AppCompatActivity implements PlaybackStateListener, PlaybackHandler {
    private static final String CLASSTAG = MainActivity.class.getSimpleName();
    private static final String CONTENT_STREAM_URL = "http://media.truex.com/file_assets/2019-01-30/4ece0ae6-4e93-43a1-a873-936ccd3c7ede.mp4";

    private static final String[] adUrls = {
            "http://media.truex.com/file_assets/2019-01-30/eb27eae5-c9da-4a9b-9420-a83c986baa0b.mp4",
            "http://media.truex.com/file_assets/2019-01-30/7fe9da33-6b9e-446d-816d-e1aec51a3173.mp4",
            "http://media.truex.com/file_assets/2019-01-30/742eb926-6ec0-48b4-b1e6-093cee334dd1.mp4"
    };
    private static final int[] adDurations = {30, 30, 32};

    private static final String INTENT_HDMI = "android.intent.action.HDMI_PLUGGED";
    private static final String INTENT_NOISY_AUDIO = "android.intent.action.ACTION_AUDIO_BECOMING_NOISY";

    // This player view is used to display a fake stream that mimics actual video content
    private PlayerView playerView;
    private ExoPlayer player;

    // The data-source factory is used to build media-sources
    private DataSource.Factory dataSourceFactory;

    // We need to hold onto the ad manager so that the ad manager can listen for lifecycle events
    private TruexAdManager truexAdManager;

    // We need to identify whether or not the user is viewing ads or the content stream
    private DisplayMode displayMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        // Set-up the video content player
        setupExoPlayer();

        // Set-up the data-source factory
        setupDataSourceFactory();

        setupIntents(); // now we can be sensitive to HDMI cable changes

        // Start the content stream
        displayContentStream();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // We need to inform the true[X] ad manager that the application has resumed
        if (truexAdManager != null) {
            truexAdManager.onResume();
        }

        // Resume video playback
        if (player != null && displayMode != DisplayMode.INTERACTIVE_AD) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // We need to inform the true[X] ad manager that the application has paused
        if (truexAdManager != null) {
            truexAdManager.onPause();
        }

        // Pause video playback
        if (player != null && displayMode != DisplayMode.INTERACTIVE_AD) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        // We need to inform the true[X] ad manager that the application has stopped
        if (truexAdManager != null) {
            truexAdManager.onStop();
        }

        // Release the video player
        closeStream();
    }

    /**
     * Called when the player starts displaying the fake content stream
     * Display the true[X] engagement
     */
    public void onPlayerDidStart() {
        Log.i(CLASSTAG, "onPlayerDidStart");
        displayInteractiveAd();
    }

    /**
     * Called when the media stream is resumed
     */
    public void onPlayerDidResume() {
        Log.d(CLASSTAG, "onPlayerDidResume");
    }

    /**
     * Called when the media stream is paused
     */
    public void onPlayerDidPause() {
        Log.d(CLASSTAG, "onPlayerDidPause");
    }

    /**
     * Called when the media stream is complete
     */
    public void onPlayerDidComplete() {
        if (displayMode == DisplayMode.LINEAR_ADS) {
            displayContentStream();
        }
    }

    /**
     * This method resumes and displays the content stream
     * Note: We call this method whenever a true[X] engagement is completed
     */
    @Override
    public void resumeStream() {
        Log.d(CLASSTAG, "resumeStream");
        if (player == null) return;
        playerView.setVisibility(View.VISIBLE);
        player.setPlayWhenReady(true);
        player.prepare();
        player.play();
    }

    /**
     * This method pauses and hides the fake content stream
     * Note: We call this method whenever a true[X] engagement is completed
     */
    public void pauseStream() {
        Log.d(CLASSTAG, "pauseStream");
        if (player == null) return;
        player.setPlayWhenReady(false);
        player.pause();
        playerView.setVisibility(View.GONE);
    }

    /**
     * This method cancels the content stream and releases the video content player
     * Note: We call this method when the application is stopped or when ExoPlayer encounters errors
     */
    @Override
    public void closeStream() {
        Log.d(CLASSTAG, "closeStream");
        if (player == null) return;
        playerView.setPlayer(null);
        player.release();
    }

    /**
     * This method closes the stream and then returns to the tag selection view
     */
    public void cancelStream() {
        // Close the stream
        closeStream();

        // Return to the previous fragment
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
        }
    }


    /**
     * This method cancels the content stream and begins playing a linear ad
     * Note: We call this method whenever the user cancels an engagement without receiving credit
     */
    @Override
    public void displayLinearAds() {
        Log.d(CLASSTAG, "displayLinearAds");
        if (player == null) return;

        displayMode = DisplayMode.LINEAR_ADS;

        ConcatenatingMediaSource2.Builder adBreakBuilder = new ConcatenatingMediaSource2.Builder();

        // Show the fallback ad videos.
        for (int i = 0; i < adUrls.length; i++) {
            String adUrl = adUrls[i];
            long adDuration = adDurations[i] * 1000;
            MediaSource adSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(adUrl));
            adBreakBuilder.add(adSource, adDuration);
        }

        MediaSource adPod = adBreakBuilder.build();
        player.setPlayWhenReady(true);
        player.setMediaSource(adPod);
        player.prepare();
        playerView.setVisibility(View.VISIBLE);
        playerView.hideController();
    }

    private void displayInteractiveAd() {
        Log.d(CLASSTAG, "displayInteractiveAds");
        if (player == null) return;

        // Pause the stream and display a true[X] engagement
        pauseStream();

        displayMode = DisplayMode.INTERACTIVE_AD;

        // Start the true[X] engagement
        ViewGroup viewGroup = (ViewGroup) this.findViewById(R.id.activity_main);
        truexAdManager = new TruexAdManager(this, this);

        // Normally the truex vast config url would come from the Ad SDK's VAST data for the ad.
        // References the Hilton and Kung Fu Panda ads, not very typical.
        //String vastConfigUrl = "https://qa-get.truex.com/81551ffa2b851abc5372ab9ed9f1f58adabe5203/vast/config?asnw=&flag=%2Bamcb%2Bemcr%2Bslcb%2Bvicb%2Baeti-exvt&fw_key_values=&metr=0&prof=g_as3_truex&ptgt=a&pvrn=&resp=vmap1&slid=fw_truex&ssnw=&vdur=&vprn=";

        // This refers to an unrestricted, non-expiring, no-geo-blocked placement and test campaign.
        // with a more modern test ad: https://ee.truex.com/ads/27287
        // placement: https://ee.truex.com/admin/placements/1078
        // campaign: https://ee.truex.com/campaigns/ab0f12462
        String vastConfigUrl = "https://get.truex.com/88ac681ba8d0458e413dc22374194ab9f60b6664/vast/config?dimension_5=PI-2449-ctv-ad";

        truexAdManager.startAd(viewGroup, vastConfigUrl);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_M) {
            // For manual invocation.
            displayInteractiveAd();
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    private void displayContentStream() {
        Log.d(CLASSTAG, "displayContentStream");
        if (player == null) return;

        displayMode = DisplayMode.CONTENT_STREAM;

        Uri uri = Uri.parse(CONTENT_STREAM_URL);
        MediaSource source = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri));
        player.setPlayWhenReady(true);
        player.setMediaSource(source);
        player.prepare();
    }

    private void setupExoPlayer() {
        player = new ExoPlayer.Builder(getApplicationContext()).build();

        playerView = findViewById(R.id.player_view);
        playerView.setPlayer(player);

        // Listen for player events so that we can load the true[X] ad manager when the video stream starts
        player.addListener(new PlayerEventListener(this));
    }

    private void setupDataSourceFactory() {
        String applicationName = getApplicationInfo().loadLabel(getPackageManager()).toString();
        String userAgent = Util.getUserAgent(getApplicationContext(), applicationName);
        dataSourceFactory = new DefaultDataSourceFactory(this, userAgent, null);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void setupIntents() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(hdmiStateChange, new IntentFilter(INTENT_HDMI), RECEIVER_EXPORTED);
            registerReceiver(audioWillBecomeNoisy, new IntentFilter(INTENT_NOISY_AUDIO), RECEIVER_EXPORTED);
        } else {
            registerReceiver(hdmiStateChange, new IntentFilter(INTENT_HDMI));
            registerReceiver(audioWillBecomeNoisy, new IntentFilter(INTENT_NOISY_AUDIO));
        }
    }

    BroadcastReceiver hdmiStateChange = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(INTENT_HDMI)) {
                boolean state = intent.getBooleanExtra("state", false);
                if (state) {
                    onResume();
                } else {
                    onPause();
                }
            }
        }
    };

    BroadcastReceiver audioWillBecomeNoisy = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(INTENT_NOISY_AUDIO)) {
                onPause();
                // here we simply resume playback in 3 seconds
                new android.os.Handler().postDelayed(
                        new Runnable() {
                            public void run() {
                                onResume();
                            }
                        },
                        3000);
            }
        }
    };
}