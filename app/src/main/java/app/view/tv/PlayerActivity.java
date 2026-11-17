package app.view.tv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.util.ArrayList;

public class PlayerActivity extends Activity {

    private static final long ZAP_DELAY_MS = 380;
    private static final long INFO_MS = 4500;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private PlayerView playerView;
    private View info;
    private View spinner;
    private View errorBox;
    private TextView infoName;
    private TextView infoNumber;
    private TextView infoCategory;
    private TextView infoInitials;
    private ImageView infoLogo;

    private ArrayList<Xtream.Channel> list;
    private int index;
    private boolean triedHls;
    private int endedRetries;
    private String server;
    private String user;
    private String pass;

    private final Runnable hideInfo = () -> info.animate().alpha(0f).setDuration(260).start();
    private final Runnable playCurrent = this::play;

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int state) {
            if (player == null) return;
            if (state == Player.STATE_BUFFERING) {
                spinner.setVisibility(View.VISIBLE);
            } else if (state == Player.STATE_READY) {
                spinner.setVisibility(View.GONE);
                errorBox.setVisibility(View.GONE);
                endedRetries = 0;
            } else if (state == Player.STATE_ENDED) {
                // Live-Stream abgerissen: neu verbinden
                if (endedRetries < 5) {
                    endedRetries++;
                    ui.removeCallbacks(playCurrent);
                    ui.postDelayed(() -> start(triedHls), 800);
                } else {
                    showError();
                }
            }
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException e) {
            if (player == null) return;
            if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player.seekToDefaultPosition();
                player.prepare();
                return;
            }
            if (!triedHls) {
                triedHls = true;
                start(true);
                return;
            }
            showError();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        list = DataStore.playList;
        if (list == null || list.isEmpty()) {
            finish();
            return;
        }
        index = Math.max(0, Math.min(DataStore.playIndex, list.size() - 1));
        DataStore.returnIndex = index;
        server = Prefs.server(this);
        user = Prefs.user(this);
        pass = Prefs.pass(this);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);
        playerView = findViewById(R.id.playerView);
        info = findViewById(R.id.info);
        spinner = findViewById(R.id.spinner);
        errorBox = findViewById(R.id.errorBox);
        infoName = findViewById(R.id.infoName);
        infoNumber = findViewById(R.id.infoNumber);
        infoCategory = findViewById(R.id.infoCategory);
        infoInitials = findViewById(R.id.infoInitials);
        infoLogo = findViewById(R.id.infoLogo);

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(Xtream.UA)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(30000);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .build();
        player.addListener(listener);

        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setShutterBackgroundColor(Color.BLACK);
        playerView.setKeepContentOnPlayerReset(false);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
        playerView.setPlayer(player);

        showInfo();
        play();
    }

    private Xtream.Channel channel() {
        return list.get(index);
    }

    private void play() {
        if (player == null) return;
        triedHls = false;
        endedRetries = 0;
        start(false);
    }

    private void start(boolean hls) {
        if (player == null) return;
        errorBox.setVisibility(View.GONE);
        spinner.setVisibility(View.VISIBLE);
        String url = Xtream.streamUrl(server, user, pass, channel(), hls);
        MediaItem.Builder b = new MediaItem.Builder().setUri(url);
        if (hls) b.setMimeType(MimeTypes.APPLICATION_M3U8);
        player.setMediaItem(b.build());
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void showError() {
        spinner.setVisibility(View.GONE);
        errorBox.setVisibility(View.VISIBLE);
        showInfo();
    }

    private void zap(int delta) {
        int n = list.size();
        if (n <= 1 || player == null) return;
        index = ((index + delta) % n + n) % n;
        DataStore.returnIndex = index;
        ui.removeCallbacks(playCurrent);
        player.stop();
        errorBox.setVisibility(View.GONE);
        spinner.setVisibility(View.VISIBLE);
        showInfo();
        ui.postDelayed(playCurrent, ZAP_DELAY_MS);
    }

    private void showInfo() {
        Xtream.Channel c = channel();
        infoName.setText(c.title);
        infoNumber.setText((index + 1) + " / " + list.size());
        infoCategory.setText(DataStore.playTitle == null ? "" : DataStore.playTitle);
        infoInitials.setText(c.initials);
        infoInitials.setVisibility(View.VISIBLE);
        Glide.with(getApplicationContext()).clear(infoLogo);
        infoLogo.setImageDrawable(null);
        if (c.logo != null && !c.logo.isEmpty()) {
            Glide.with(getApplicationContext())
                    .load(c.logo)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                    @NonNull Target<Drawable> target, boolean isFirstResource) {
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model,
                                                       Target<Drawable> target, @NonNull DataSource dataSource,
                                                       boolean isFirstResource) {
                            infoInitials.setVisibility(View.INVISIBLE);
                            return false;
                        }
                    })
                    .into(infoLogo);
        }
        info.animate().cancel();
        info.animate().alpha(1f).setDuration(160).start();
        ui.removeCallbacks(hideInfo);
        ui.postDelayed(hideInfo, INFO_MS);
    }

    private static boolean isOurKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_INFO:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (!isOurKey(code)) return super.dispatchKeyEvent(event);
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                zap(-1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                zap(1);
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.getRepeatCount() > 0) break;
                if (errorBox.getVisibility() == View.VISIBLE) {
                    play();
                    showInfo();
                } else if (info.getAlpha() > 0.5f) {
                    ui.removeCallbacks(hideInfo);
                    hideInfo.run();
                } else {
                    showInfo();
                }
                break;
            default:
                showInfo();
                break;
        }
        return true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayer();
        if (!isFinishing()) finish();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        releasePlayer();
        super.onDestroy();
    }

    private void releasePlayer() {
        ui.removeCallbacks(playCurrent);
        if (player != null) {
            player.removeListener(listener);
            if (playerView != null) playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }
}
