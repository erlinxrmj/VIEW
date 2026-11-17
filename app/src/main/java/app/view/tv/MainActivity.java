package app.view.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements CategoryAdapter.Listener, ChannelAdapter.Listener {

    private static final int COLS = 4;
    private static final long CATEGORY_DELAY_MS = 170;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    private VerticalGridView categoriesView;
    private VerticalGridView channelsView;
    private ViewGroup contentRoot;
    private View loadingView;
    private View loadingButtons;
    private View emptyView;
    private View menuView;
    private View toastView;
    private ProgressBar loadingSpinner;
    private TextView loadingText;
    private TextView catTitle;
    private TextView catSub;
    private TextView expiryText;
    private TextView toastText;
    private ImageView toastIcon;
    private TextView menuInfo;

    private final ArrayList<Xtream.Category> entries = new ArrayList<>();
    private final LinkedHashSet<String> favIds = new LinkedHashSet<>();
    private Xtream.Data data;
    private CategoryAdapter categoryAdapter;
    private ChannelAdapter channelAdapter;
    private int current = -1;
    private int pendingCategory = -1;
    private boolean loading;
    private boolean destroyed;
    private View focusBeforeMenu;

    private final Runnable applyPending = () -> {
        if (pendingCategory >= 0) {
            int p = pendingCategory;
            pendingCategory = -1;
            showCategory(p);
        }
    };

    private final Runnable hideToast = () -> toastView.animate().alpha(0f).translationY(20f).setDuration(220)
            .withEndAction(() -> toastView.setVisibility(View.GONE)).start();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Prefs.loggedIn(this)) {
            openLogin();
            return;
        }
        setContentView(R.layout.activity_main);

        contentRoot = findViewById(R.id.contentRoot);
        categoriesView = findViewById(R.id.categories);
        channelsView = findViewById(R.id.channels);
        loadingView = findViewById(R.id.loading);
        loadingButtons = findViewById(R.id.loadingButtons);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        loadingText = findViewById(R.id.loadingText);
        emptyView = findViewById(R.id.empty);
        menuView = findViewById(R.id.menu);
        toastView = findViewById(R.id.toast);
        toastText = findViewById(R.id.toastText);
        toastIcon = findViewById(R.id.toastIcon);
        catTitle = findViewById(R.id.catTitle);
        catSub = findViewById(R.id.catSub);
        expiryText = findViewById(R.id.expiry);
        menuInfo = findViewById(R.id.menuInfo);

        Ui.gradientText(findViewById(R.id.brand));
        Ui.gradientText(findViewById(R.id.loadingBrand));
        Ui.gradientText(findViewById(R.id.menuBrand));

        favIds.addAll(Prefs.favorites(this));
        expiryText.setText(Ui.expiryText(Prefs.expiry(this)));

        // ---- Kategorien links
        categoryAdapter = new CategoryAdapter(entries, this);
        categoriesView.setItemAnimator(null);
        categoriesView.setVerticalSpacing(Ui.dp(this, 6));
        categoriesView.setWindowAlignmentOffsetPercent(38f);
        categoriesView.setAdapter(categoryAdapter);
        categoriesView.setOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(RecyclerView parent, RecyclerView.ViewHolder child,
                                                  int position, int subposition) {
                if (categoriesView.hasFocus()) onCategoryFocused(position);
            }
        });

        // ---- Sender rechts
        int gridPad = Ui.dp(this, 16);
        int hSpace = Ui.dp(this, 18);
        int vSpace = Ui.dp(this, 20);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int inner = screenW - Ui.dp(this, 300) - Ui.dp(this, 24) - Ui.dp(this, 48) - gridPad * 2;
        int tileW = Math.max(Ui.dp(this, 90), (inner - hSpace * (COLS - 1)) / COLS);
        int tileH = Math.round(tileW * 0.62f);
        channelAdapter = new ChannelAdapter(favIds, this, tileW, tileH);
        channelsView.setNumColumns(COLS);
        channelsView.setHorizontalSpacing(hSpace);
        channelsView.setVerticalSpacing(vSpace);
        channelsView.setItemAnimator(null);
        channelsView.setAdapter(channelAdapter);

        // ---- Buttons
        findViewById(R.id.btnClose).setOnClickListener(v -> hideMenu());
        findViewById(R.id.btnRefresh).setOnClickListener(v -> {
            hideMenu();
            loadData(true);
        });
        findViewById(R.id.btnLogout).setOnClickListener(v -> logout());
        findViewById(R.id.btnExit).setOnClickListener(v -> finish());
        findViewById(R.id.btnRetry).setOnClickListener(v -> loadData(false));
        findViewById(R.id.btnErrLogout).setOnClickListener(v -> logout());

        loadData(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (channelAdapter == null) return;
        int r = DataStore.returnIndex;
        DataStore.returnIndex = -1;
        if (r >= 0 && r < channelAdapter.getItemCount() && menuView.getVisibility() != View.VISIBLE
                && !isLoadingVisible()) {
            focusChannel(r);
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        ui.removeCallbacksAndMessages(null);
        bg.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ Laden

    private void loadData(final boolean force) {
        if (loading) return;
        loading = true;
        showLoading(force ? "Senderliste wird aktualisiert …" : "Senderliste wird geladen …");
        final String s = Prefs.server(this);
        final String u = Prefs.user(this);
        final String p = Prefs.pass(this);
        final Context app = getApplicationContext();
        bg.execute(() -> {
            try {
                final Xtream.Data d = Xtream.load(app, s, u, p, force);
                ui.post(() -> {
                    loading = false;
                    if (!destroyed) apply(d);
                });
                final Xtream.LoginResult r = Xtream.login(s, u, p);
                if (r.ok) {
                    Prefs.setExpiry(app, r.expiry);
                    ui.post(() -> {
                        if (!destroyed) expiryText.setText(Ui.expiryText(r.expiry));
                    });
                }
            } catch (Throwable e) {
                final String msg = Xtream.describe(e);
                ui.post(() -> {
                    loading = false;
                    if (!destroyed) showLoadError(msg);
                });
            }
        });
    }

    private void showLoading(String text) {
        loadingSpinner.setVisibility(View.VISIBLE);
        loadingButtons.setVisibility(View.GONE);
        loadingText.setTextColor(getColor(R.color.text2));
        loadingText.setText(text);
        loadingView.animate().cancel();
        loadingView.setAlpha(1f);
        loadingView.setVisibility(View.VISIBLE);
        updateFocusability();
    }

    private void showLoadError(String msg) {
        loadingSpinner.setVisibility(View.GONE);
        loadingText.setTextColor(getColor(R.color.error));
        loadingText.setText("Senderliste konnte nicht geladen werden.\n" + msg);
        loadingButtons.setVisibility(View.VISIBLE);
        updateFocusability();
        findViewById(R.id.btnRetry).requestFocus();
    }

    private void hideLoading() {
        loadingButtons.setVisibility(View.GONE);
        loadingView.animate().alpha(0f).setDuration(260)
                .withEndAction(() -> loadingView.setVisibility(View.GONE)).start();
        updateFocusability();
    }

    private void updateFocusability() {
        boolean block = menuView.getVisibility() == View.VISIBLE || loading
                || loadingButtons.getVisibility() == View.VISIBLE;
        contentRoot.setDescendantFocusability(block
                ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    private boolean isLoadingVisible() {
        return loading || loadingButtons.getVisibility() == View.VISIBLE;
    }

    private void apply(Xtream.Data d) {
        String keepId = (current >= 0 && current < entries.size()) ? entries.get(current).id : null;
        data = d;

        entries.clear();
        Xtream.Category fav = new Xtream.Category("__fav", "Favoriten", "", Xtream.TYPE_FAV);
        Xtream.Category all = new Xtream.Category("__all", "Alle Sender", "", Xtream.TYPE_ALL);
        all.channels.addAll(d.all);
        entries.add(fav);
        entries.add(all);
        entries.addAll(d.categories);
        rebuildFavorites();

        int start = -1;
        if (keepId != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).id.equals(keepId)) {
                    start = i;
                    break;
                }
            }
        }
        if (start < 0) start = fav.channels.isEmpty() ? 1 : 0; // Favoriten immer zuerst

        current = -1;
        pendingCategory = -1;
        ui.removeCallbacks(applyPending);
        categoryAdapter.resetActivated();
        categoryAdapter.notifyDataSetChanged();
        categoriesView.setSelectedPosition(start);
        showCategory(start);

        hideLoading();
        if (channelAdapter.getItemCount() > 0) focusChannel(0);
        else focusCategories();
    }

    private void rebuildFavorites() {
        if (entries.isEmpty()) return;
        Xtream.Category fav = entries.get(0);
        fav.channels.clear();
        if (data == null) return;
        for (String id : favIds) {
            Xtream.Channel c = data.byId.get(id);
            if (c != null) fav.channels.add(c);
        }
    }

    // ------------------------------------------------------------------ Kategorien

    private void showCategory(int pos) {
        if (pos < 0 || pos >= entries.size()) return;
        ui.removeCallbacks(applyPending);
        pendingCategory = -1;
        boolean same = pos == current;
        current = pos;
        categoryAdapter.setActivated(pos, categoriesView);
        if (!same) {
            channelAdapter.setItems(entries.get(pos).channels);
            channelsView.setSelectedPosition(0);
        }
        updateHeader();
    }

    private void updateHeader() {
        if (current < 0 || current >= entries.size()) return;
        Xtream.Category e = entries.get(current);
        catTitle.setText(e.title);
        int n = channelAdapter.getItemCount();
        if (e.type == Xtream.TYPE_FAV) {
            catSub.setText(n == 0 ? "Deine Lieblingssender"
                    : (n == 1 ? "1 Favorit" : n + " Favoriten") + "   ·   OK lange drücken zum Entfernen");
        } else {
            catSub.setText((n == 1 ? "1 Sender" : n + " Sender") + "   ·   OK lange drücken = Favorit");
        }
        emptyView.setVisibility(e.type == Xtream.TYPE_FAV && n == 0 ? View.VISIBLE : View.GONE);
    }

    private void onCategoryFocused(int pos) {
        ui.removeCallbacks(applyPending);
        if (pos == current) {
            pendingCategory = -1;
            return;
        }
        pendingCategory = pos;
        ui.postDelayed(applyPending, CATEGORY_DELAY_MS);
    }

    @Override
    public void onCategoryEnter(int position) {
        boolean same = position == current;
        showCategory(position);
        if (channelAdapter.getItemCount() == 0) {
            if (entries.get(position).type == Xtream.TYPE_FAV) {
                showToast("Halte OK auf einem Sender gedrückt, um ihn zu speichern", true);
            }
            return;
        }
        int sel = same ? channelsView.getSelectedPosition() : 0;
        focusChannel(sel < 0 ? 0 : sel);
    }

    private void focusCategories() {
        if (current < 0 || current >= entries.size()) return;
        categoriesView.setSelectedPosition(current);
        tryFocus(categoriesView, current, 0);
    }

    private void focusChannel(int pos) {
        int n = channelAdapter.getItemCount();
        if (n == 0) return;
        int p = Math.max(0, Math.min(pos, n - 1));
        channelsView.setSelectedPosition(p);
        tryFocus(channelsView, p, 0);
    }

    /** Fokus erst setzen, wenn die Kachel wirklich angezeigt wird – verhindert Fokus-Sprünge. */
    private void tryFocus(final VerticalGridView grid, final int pos, final int attempt) {
        if (destroyed) return;
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null && vh.itemView.isAttachedToWindow() && vh.itemView.getVisibility() == View.VISIBLE) {
            vh.itemView.requestFocus();
            return;
        }
        if (attempt >= 10) {
            grid.requestFocus();
            return;
        }
        ui.postDelayed(() -> tryFocus(grid, pos, attempt + 1), 30);
    }

    // ------------------------------------------------------------------ Sender

    @Override
    public void onChannelClick(int position) {
        if (position < 0 || position >= channelAdapter.getItemCount() || current < 0) return;
        DataStore.playList = new ArrayList<>(channelAdapter.items);
        DataStore.playIndex = position;
        DataStore.playTitle = entries.get(current).title;
        DataStore.returnIndex = -1;
        startActivity(new Intent(this, PlayerActivity.class));
    }

    @Override
    public void onChannelLongClick(int position) {
        toggleFavorite(position);
    }

    @Override
    public boolean onChannelKey(int position, int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && position % COLS == 0) {
            focusCategories();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            toggleFavorite(position);
            return true;
        }
        return false;
    }

    private void toggleFavorite(int pos) {
        if (pos < 0 || pos >= channelAdapter.items.size() || current < 0) return;
        Xtream.Channel ch = channelAdapter.items.get(pos);
        boolean added;
        if (favIds.remove(ch.id)) {
            added = false;
        } else {
            favIds.add(ch.id);
            added = true;
        }
        Prefs.setFavorites(this, favIds);
        rebuildFavorites();
        categoryAdapter.refreshCount(0, categoriesView);

        Xtream.Category e = entries.get(current);
        if (e.type == Xtream.TYPE_FAV && !added) {
            channelAdapter.items.remove(pos);
            channelAdapter.notifyItemRemoved(pos);
            updateHeader();
            if (channelAdapter.getItemCount() == 0) focusCategories();
            else focusChannel(Math.min(pos, channelAdapter.getItemCount() - 1));
        } else {
            RecyclerView.ViewHolder vh = channelsView.findViewHolderForAdapterPosition(pos);
            if (vh instanceof ChannelAdapter.VH) ((ChannelAdapter.VH) vh).bindFavorite(favIds.contains(ch.id));
            else channelAdapter.notifyItemChanged(pos);
        }
        showToast(added ? ch.title + " ist jetzt ein Favorit" : ch.title + " aus Favoriten entfernt", added);
    }

    private void showToast(String text, boolean star) {
        ui.removeCallbacks(hideToast);
        toastText.setText(text);
        toastIcon.setAlpha(star ? 1f : 0.35f);
        toastView.animate().cancel();
        if (toastView.getVisibility() != View.VISIBLE) {
            toastView.setAlpha(0f);
            toastView.setTranslationY(20f);
            toastView.setVisibility(View.VISIBLE);
        }
        toastView.animate().alpha(1f).translationY(0f).setDuration(180).start();
        ui.postDelayed(hideToast, 2000);
    }

    // ------------------------------------------------------------------ Menü

    private void showMenu() {
        if (menuView.getVisibility() == View.VISIBLE) return;
        focusBeforeMenu = getCurrentFocus();
        String host = Prefs.server(this);
        try {
            String h = Uri.parse(host).getHost();
            if (h != null && !h.isEmpty()) host = h;
        } catch (Exception ignored) {
        }
        menuInfo.setText(Prefs.user(this) + "  ·  " + host + "\n" + Ui.expiryText(Prefs.expiry(this)));
        menuView.setAlpha(0f);
        menuView.setVisibility(View.VISIBLE);
        menuView.animate().alpha(1f).setDuration(160).start();
        findViewById(R.id.btnClose).requestFocus();
        updateFocusability();
    }

    private void hideMenu() {
        if (menuView.getVisibility() != View.VISIBLE) return;
        menuView.setVisibility(View.GONE);
        updateFocusability();
        View before = focusBeforeMenu;
        focusBeforeMenu = null;
        if (before != null && isChildOf(before, channelsView) && channelAdapter.getItemCount() > 0) {
            focusChannel(channelsView.getSelectedPosition());
        } else if (before != null && before.isAttachedToWindow() && isChildOf(before, contentRoot)
                && before.requestFocus()) {
            // Fokus zurück, wo er war
        } else {
            focusCategories();
        }
    }

    private static boolean isChildOf(View v, View parent) {
        Object p = v.getParent();
        while (p instanceof View) {
            if (p == parent) return true;
            p = ((View) p).getParent();
        }
        return false;
    }

    private void logout() {
        Prefs.logout(this);
        Xtream.clearCache(this);
        openLogin();
    }

    private void openLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    // ------------------------------------------------------------------ Tasten

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && contentRoot != null) {
            if (isLoadingVisible()) return true;
            if (menuView.getVisibility() == View.VISIBLE) hideMenu();
            else showMenu();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (contentRoot == null) {
            super.onBackPressed();
            return;
        }
        if (menuView.getVisibility() == View.VISIBLE) {
            hideMenu();
            return;
        }
        if (isLoadingVisible()) {
            finish();
            return;
        }
        if (channelsView.hasFocus()) {
            focusCategories();
            return;
        }
        showMenu();
    }
}
