package io.rover.ui;

import android.graphics.drawable.GradientDrawable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import io.rover.R;
import io.rover.model.Block;
import io.rover.model.Row;

/**
 * Created by ata_n on 2016-06-28.
 */
public class RowsAdapter extends RecyclerView.Adapter<RowsAdapter.ViewHolder> implements BlockLayoutManager.BlockProvider {

    private List<Row> mRows;
    private float screenDensity;

    public RowsAdapter(List<Row> rows, float density) {
        mRows = rows;
        screenDensity = density;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View blockView = inflater.inflate(R.layout.text_block, parent, false);
        ViewHolder viewHolder = new ViewHolder(blockView);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
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

        if (block == null) { return; }

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

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(View itemView) {
            super(itemView);
        }
    }

    @Override
    public int getRowCount() {
        return mRows.size();
    }

    @Override
    public Row getRow(int index) {
        return mRows.get(index);
    }
}
