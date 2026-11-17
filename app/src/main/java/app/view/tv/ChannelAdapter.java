package app.view.tv;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Rechte Seite: Senderkacheln mit Logo. */
final class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {

    static final float FOCUS_SCALE = 1.09f;

    interface Listener {
        void onChannelClick(int position);

        void onChannelLongClick(int position);

        /** true = Taste verbraucht. */
        boolean onChannelKey(int position, int keyCode);
    }

    final ArrayList<Xtream.Channel> items = new ArrayList<>();
    private final Set<String> favorites;
    private final Listener listener;
    private final int tileW;
    private final int tileH;
    private final DecelerateInterpolator interpolator = new DecelerateInterpolator();

    ChannelAdapter(Set<String> favorites, Listener listener, int tileW, int tileH) {
        this.favorites = favorites;
        this.listener = listener;
        this.tileW = tileW;
        this.tileH = tileH;
        setHasStableIds(true);
    }

    void setItems(List<Xtream.Channel> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).stableId;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        lp.width = tileW;
        v.setLayoutParams(lp);
        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams cl = card.getLayoutParams();
        cl.width = tileW;
        cl.height = tileH;
        card.setLayoutParams(cl);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Xtream.Channel c = items.get(position);
        h.name.setText(c.title);
        h.initials.setText(c.initials);
        h.initials.setVisibility(View.VISIBLE);
        h.bindFavorite(favorites.contains(c.id));

        h.itemView.animate().cancel();
        float s = h.itemView.isFocused() ? FOCUS_SCALE : 1f;
        h.itemView.setScaleX(s);
        h.itemView.setScaleY(s);

        Context app = h.itemView.getContext().getApplicationContext();
        Glide.with(app).clear(h.logo);
        if (c.logo == null || c.logo.isEmpty()) {
            h.logo.setImageDrawable(null);
        } else {
            Glide.with(app)
                    .load(c.logo)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .transition(DrawableTransitionOptions.withCrossFade(150))
                    .listener(h)
                    .into(h.logo);
        }
    }

    @Override
    public void onViewRecycled(@NonNull VH h) {
        Glide.with(h.itemView.getContext().getApplicationContext()).clear(h.logo);
        h.logo.setImageDrawable(null);
        h.itemView.animate().cancel();
        h.itemView.setScaleX(1f);
        h.itemView.setScaleY(1f);
    }

    final class VH extends RecyclerView.ViewHolder implements RequestListener<Drawable> {
        final ImageView logo;
        final ImageView fav;
        final TextView initials;
        final TextView name;

        VH(View v) {
            super(v);
            logo = v.findViewById(R.id.logo);
            fav = v.findViewById(R.id.fav);
            initials = v.findViewById(R.id.initials);
            name = v.findViewById(R.id.name);

            v.setOnClickListener(x -> {
                int p = getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION) listener.onChannelClick(p);
            });
            v.setOnLongClickListener(x -> {
                int p = getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION) listener.onChannelLongClick(p);
                return true;
            });
            v.setOnKeyListener((x, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_MENU) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                        int p = getBindingAdapterPosition();
                        if (p != RecyclerView.NO_POSITION) listener.onChannelKey(p, keyCode);
                    }
                    return true;
                }
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int p = getBindingAdapterPosition();
                if (p == RecyclerView.NO_POSITION) return false;
                return listener.onChannelKey(p, keyCode);
            });
            v.setOnFocusChangeListener((x, hasFocus) -> {
                float s = hasFocus ? FOCUS_SCALE : 1f;
                x.animate().scaleX(s).scaleY(s).setDuration(150).setInterpolator(interpolator).start();
            });
        }

        void bindFavorite(boolean on) {
            fav.setVisibility(on ? View.VISIBLE : View.GONE);
        }

        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                    @NonNull Target<Drawable> target, boolean isFirstResource) {
            initials.setVisibility(View.VISIBLE);
            return false;
        }

        @Override
        public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model,
                                       Target<Drawable> target, @NonNull DataSource dataSource,
                                       boolean isFirstResource) {
            initials.setVisibility(View.INVISIBLE);
            return false;
        }
    }
}
