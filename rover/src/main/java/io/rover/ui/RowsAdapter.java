package io.rover.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import io.rover.R;
import io.rover.model.Block;
import io.rover.model.ImageBlock;
import io.rover.model.Row;
import io.rover.model.TextBlock;

/**
 * Created by ata_n on 2016-06-28.
 */
public class RowsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements BlockLayoutManager.BlockProvider {

    private List<Row> mRows;
    private float screenDensity;

    public RowsAdapter(List<Row> rows, float density) {
        mRows = rows;
        screenDensity = density;
    }

    @Override
    public int getItemViewType(int position) {
        Block block = getBlockAtPosition(position);
        if (block instanceof ImageBlock) {
            return 0;
        } else if (block instanceof TextBlock) {
            return 1;
        }
        return super.getItemViewType(position);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case 0: return new ImageViewHolder(parent.getContext());
            case 1: return new TextViewHolder(parent.getContext());
            default: return new ViewHolder(parent.getContext());
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Block block = getBlockAtPosition(position);

        if (block == null) { return; }

        // Appearance

        GradientDrawable borderDrawable = new GradientDrawable();
        borderDrawable.setColor(block.getBackgroundColor());
        borderDrawable.setStroke((int)(block.getBorderWidth() * screenDensity), block.getBorderColor());
        borderDrawable.setCornerRadius((float)(block.getBorderRadius() * screenDensity));
        holder.itemView.setBackground(borderDrawable);
    }

    @Override
    public int getItemCount() {
        int numBlocks = 0;
        for (Row row : mRows) {
            numBlocks += row.getBlocks().size();
        }
        return numBlocks;
    }

    @Override
    public int getRowCount() {
        return mRows.size();
    }

    @Override
    public Row getRow(int index) {
        return mRows.get(index);
    }

    private Block getBlockAtPosition(int position) {
        Block block = null;

        int count = 0;
        for (int i = 0; i < mRows.size(); i++) {
            List<Block> blocks = mRows.get(i).getBlocks();
            int index = position - count;
            if (index < blocks.size()) {
                block = blocks.get(index);
                break;
            }
            count += blocks.size();
        }

        return block;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(Context context) {
            super(new View(context));
        }
    }

    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        public ImageViewHolder(Context context) { super(new View(context)); }
    }

    public static class TextViewHolder extends RecyclerView.ViewHolder {
        public TextViewHolder(Context context) { super(new View(context)); }
    }


}
