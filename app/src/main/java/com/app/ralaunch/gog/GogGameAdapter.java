package com.app.ralaunch.gog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.ralaunch.R;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

/**
 * GOG 游戏列表适配器
 */
public class GogGameAdapter extends RecyclerView.Adapter<GogGameAdapter.GameViewHolder> {

    private List<GogApiClient.GogGame> games;
    private final OnGameClickListener listener;
    private boolean isGridView = false; // 默认列表视图

    public interface OnGameClickListener {
        void onGameClick(GogApiClient.GogGame game);
    }

    public GogGameAdapter(List<GogApiClient.GogGame> games, OnGameClickListener listener) {
        this.games = games;
        this.listener = listener;
    }

    /**
     * 设置视图类型
     * @param isGridView true 为网格视图，false 为列表视图
     */
    public void setViewType(boolean isGridView) {
        if (this.isGridView != isGridView) {
            this.isGridView = isGridView;
            notifyDataSetChanged();
        }
    }

    /**
     * 更新游戏列表
     */
    public void updateGames(List<GogApiClient.GogGame> newGames) {
        this.games = newGames;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return isGridView ? 1 : 0; // 1 = 网格视图, 0 = 列表视图
    }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = viewType == 1 ? R.layout.item_gog_game_grid : R.layout.item_gog_game;
        View view = LayoutInflater.from(parent.getContext())
                .inflate(layoutId, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        GogApiClient.GogGame game = games.get(position);
        holder.bind(game, listener);
    }

    @Override
    public int getItemCount() {
        return games.size();
    }

    static class GameViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView gameCard;
        private final ShapeableImageView gameImage;
        private final TextView gameTitle;
        private final MaterialCardView imageCard; // 网格视图的图片容器

        public GameViewHolder(@NonNull View itemView) {
            super(itemView);
            gameCard = itemView.findViewById(R.id.gameCard);
            gameImage = itemView.findViewById(R.id.gameImage);
            gameTitle = itemView.findViewById(R.id.gameTitle);
            imageCard = itemView.findViewById(R.id.imageCard); // 可能为 null（列表视图）
        }

        public void bind(GogApiClient.GogGame game, OnGameClickListener listener) {
            gameTitle.setText(game.title);

            // 如果是网格视图，设置图片宽高比（16:9）
            if (imageCard != null) {
                imageCard.post(() -> {
                    int width = imageCard.getWidth();
                    if (width > 0) {
                        // 计算 16:9 的高度
                        int height = (int) (width * 9.0f / 16.0f);
                        ViewGroup.LayoutParams params = imageCard.getLayoutParams();
                        params.height = height;
                        imageCard.setLayoutParams(params);
                    }
                });
            }

            // 加载游戏图标
            if (game.image != null && !game.image.isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(game.image)
                        .placeholder(R.drawable.ic_games)
                        .error(R.drawable.ic_games)
                        .into(gameImage);
            } else {
                gameImage.setImageResource(R.drawable.ic_games);
            }

            // 卡片点击 - 直接触发下载
            gameCard.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onGameClick(game);
                }
            });
        }
    }
}
