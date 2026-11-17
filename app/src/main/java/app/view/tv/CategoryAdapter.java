package app.view.tv;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Linke Liste: Favoriten, Alle Sender, Länder/Kategorien. */
final class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {

    interface Listener {
        /** OK oder Pfeil rechts auf einer Kategorie. */
        void onCategoryEnter(int position);
    }

    private final List<Xtream.Category> items;
    private final Listener listener;
    private int activated = -1;

    CategoryAdapter(List<Xtream.Category> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Xtream.Category c = items.get(position);
        h.title.setText(c.title);
        h.count.setText(String.valueOf(c.channels.size()));
        if (c.type == Xtream.TYPE_FAV) {
            h.badgeText.setVisibility(View.GONE);
            h.badgeIcon.setVisibility(View.VISIBLE);
            h.badgeIcon.setImageResource(R.drawable.ic_star);
        } else if (c.type == Xtream.TYPE_ALL) {
            h.badgeText.setVisibility(View.GONE);
            h.badgeIcon.setVisibility(View.VISIBLE);
            h.badgeIcon.setImageResource(R.drawable.ic_grid);
        } else {
            h.badgeIcon.setVisibility(View.GONE);
            h.badgeText.setVisibility(View.VISIBLE);
            h.badgeText.setText(c.code);
        }
        h.itemView.setActivated(position == activated);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    void resetActivated() {
        activated = -1;
    }

    /** Markiert die aktuell gezeigte Kategorie, ohne die Liste neu zu zeichnen. */
    void setActivated(int position, RecyclerView rv) {
        int old = activated;
        activated = position;
        if (old != position) mark(rv, old, false);
        mark(rv, position, true);
    }

    private void mark(RecyclerView rv, int position, boolean on) {
        if (position < 0) return;
        RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(position);
        if (vh != null) vh.itemView.setActivated(on);
    }

    /** Aktualisiert nur die Zahl rechts (z. B. Anzahl Favoriten). */
    void refreshCount(int position, RecyclerView rv) {
        if (position < 0 || position >= items.size()) return;
        RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(position);
        if (vh instanceof VH) {
            ((VH) vh).count.setText(String.valueOf(items.get(position).channels.size()));
        }
    }

    final class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView count;
        final TextView badgeText;
        final ImageView badgeIcon;

        VH(View v) {
            super(v);
            title = v.findViewById(R.id.title);
            count = v.findViewById(R.id.count);
            badgeText = v.findViewById(R.id.badgeText);
            badgeIcon = v.findViewById(R.id.badgeIcon);
            v.setOnClickListener(x -> {
                int p = getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION) listener.onCategoryEnter(p);
            });
            v.setOnKeyListener((x, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        int p = getBindingAdapterPosition();
                        if (p != RecyclerView.NO_POSITION) listener.onCategoryEnter(p);
                    }
                    return true;
                }
                return false;
            });
        }
    }
}
