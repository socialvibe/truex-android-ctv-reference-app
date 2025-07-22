package com.truex.ctv.referenceapp;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSourceFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;
import androidx.media3.common.Player;

import com.truex.ctv.referenceapp.ads.AdManager;
import com.truex.ctv.referenceapp.ads.SampleAdProvider;
import com.truex.ctv.referenceapp.player.PlaybackStateListener;
import com.truex.ctv.referenceapp.player.PlayerEventListener;

@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends AppCompatActivity implements PlaybackStateListener, AdManager.AdBreadListener {
    private static final String CLASSTAG = MainActivity.class.getSimpleName();
    private static final String CONTENT_STREAM_URL = "http://media.truex.com/file_assets/2019-01-30/4ece0ae6-4e93-43a1-a873-936ccd3c7ede.mp4";

    private static final String INTENT_HDMI = "android.intent.action.HDMI_PLUGGED";
    private static final String INTENT_NOISY_AUDIO = "android.intent.action.ACTION_AUDIO_BECOMING_NOISY";

    // This player view is used to display a fake stream that mimics actual video content
    private PlayerView playerView;
    private ExoPlayer player;

    // The data-source factory is used to build media-sources
    private DataSource.Factory dataSourceFactory;
    
    // Preloaded content source for faster startup
    private MediaSource preloadedContentSource;

    // Ad pod management
    private AdManager adManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        setupExoPlayer();
        setupDataSourceFactory();
        setupIntents(); // now we can be sensitive to HDMI cable changes
        setupAdBreadManager();
        preloadContentStream();
        displayContentStream();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Forward to ad pod manager for any active ads
        if (adManager != null) {
            adManager.onResume();
        }
        
        // Resume video playback (but not during interactive ads)
        if (player != null && (adManager == null || !adManager.isPlayingInteractiveAd())) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Forward to ad pod manager for any active ads
        if (adManager != null) {
            adManager.onPause();
        }
        
        // Pause video playback (but not during interactive ads)
        if (player != null && (adManager == null || !adManager.isPlayingInteractiveAd())) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();


        // Forward to ad pod manager for any active ads
        if (adManager != null) {
            adManager.onStop();
        }
        
        // Release the video player
        closeVideoPlayer();
    }
    
    private void closeVideoPlayer() {
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    public void onPlayerDidStart() {
        adManager.startAdBread();
    }

    public void onPlayerDidResume() {
    }

    public void onPlayerDidPause() {
    }

    public void onPlayerDidComplete() {
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_M) {
            // For manual invocation - start ad pod sequence
            adManager.startAdBread();
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    private void preloadContentStream() {
        // Create and prepare content source in background
        Uri uri = Uri.parse(CONTENT_STREAM_URL);
        preloadedContentSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri));
    }
    
    private void displayContentStream() {
        if (player == null || preloadedContentSource == null) return;

        // Restore player view visibility
        playerView.setVisibility(View.VISIBLE);

        // Use preloaded content source for faster startup
        player.setPlayWhenReady(true);
        player.setMediaSource(preloadedContentSource);
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

    private void setupAdBreadManager() {
        ViewGroup adViewGroup = (ViewGroup) findViewById(R.id.activity_main);
        adManager = new AdManager(this, this, adViewGroup);
        adManager.setCurrentAdBreak(SampleAdProvider.createPrerollAdBread());
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void setupIntents() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33 and up requires an explicit security flag.
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

    @Override
    public void playMediaSource(MediaSource mediaSource) {
        if (player == null) return;

        // Play the media source
        player.setPlayWhenReady(true);
        player.setMediaSource(mediaSource);
        player.prepare();
        playerView.setVisibility(View.VISIBLE);
        playerView.hideController();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState != Player.STATE_ENDED) {
                    return;
                }

                player.removeListener(this);
                adManager.onPlaybackEnded();
            }

            @Override
            public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    adManager.onMediaItemCompleted();
                }
            }
        });
    }
    
    @Override
    public void controlPlayer(AdManager.PlayerAction action, long seekPositionMs) {
        if (player == null) return;

        switch (action) {
            case PLAY:
                playerView.hideController();
                player.setPlayWhenReady(true);
                playerView.setVisibility(View.VISIBLE);
                break;
            case SEEK_AND_PAUSE:
                playerView.setVisibility(View.INVISIBLE);
                player.seekTo(seekPositionMs);
                player.setPlayWhenReady(false);
                break;
        }
    }

    @Override
    public void onAdBreadComplete() {
        displayContentStream();
    }
    
    @Override
    public void onSkipToContent() {
        displayContentStream();
    }
}