package io.rover.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.List;

import io.rover.R;
import io.rover.model.Action;
import io.rover.model.Appearance;
import io.rover.model.Block;
import io.rover.model.ButtonBlock;
import io.rover.model.Image;
import io.rover.model.ImageBlock;
import io.rover.model.Row;
import io.rover.model.TextBlock;

/**
 * Created by ata_n on 2016-06-28.
 */
public class RowsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements BlockLayoutManager.BlockProvider {

    public interface ActionListener {
        void onAction(Action action);
    }

    private List<Row> mRows;
    final private ButtonViewHolder.OnClickListener mButtonClickListener = new ButtonClickListener();
    private ActionListener mActionListener;

    public RowsAdapter(List<Row> rows) {
        mRows = rows;
    }

    public void setActionListener(ActionListener listener) {
        mActionListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        Block block = getBlockAtPosition(position);
        if (block instanceof ImageBlock) {
            return 0;
        } else if (block instanceof TextBlock) {
            return 1;
        } else if (block instanceof ButtonBlock) {
            return 2;
        }
        return super.getItemViewType(position);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case 0: return new ImageViewHolder(parent.getContext());
            case 1: return new TextViewHolder(parent.getContext());
            case 2: {
                ButtonViewHolder viewHolder = new ButtonViewHolder(parent.getContext());
                viewHolder.setOnClickListener(mButtonClickListener);
                return viewHolder;
            }
            default: return new ViewHolder(parent.getContext());
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Block block = getBlockAtPosition(position);

        if (block == null) { return; }

        if (block instanceof TextBlock) {
            TextBlock textBlock = (TextBlock) block;
            TextBlockView textBlockView = (TextBlockView)holder.itemView;

            textBlockView.setText(textBlock.getText());
            textBlockView.setTextOffset(textBlock.getTextOffset());
            textBlockView.setTextColor(textBlock.getTextColor());
            textBlockView.setTextSize(textBlock.getFont().getSize());
            textBlockView.setTextAlignment(textBlock.getTextAlignment());
            textBlockView.setTypeface(textBlock.getFont().getTypeface());
        } else if (block instanceof ImageBlock) {
            ImageBlock imageBlock = (ImageBlock) block;
            ImageBlockView imageBlockView = (ImageBlockView) holder.itemView;
            Image image = imageBlock.getImage();

            if (image != null) {
                imageBlockView.setImageUrl(image.getImageUrl());
            }
        } else if (block instanceof ButtonBlock) {
            ButtonBlock buttonBlock = (ButtonBlock) block;
            ButtonBlockView buttonBlockView = (ButtonBlockView) holder.itemView;

            for (ButtonBlock.State state : ButtonBlock.State.values()) {
                Appearance appearance = buttonBlock.getAppearance(state);
                if (appearance == null) {
                    continue;
                }

                buttonBlockView.setTitle(appearance.title, getButtonViewState(state));
                buttonBlockView.setTitleColor(appearance.titleColor, getButtonViewState(state));
                buttonBlockView.setTitleAlignment(appearance.titleAlignment, getButtonViewState(state));
                buttonBlockView.setTitleOffset(appearance.titleOffset, getButtonViewState(state));
                if (appearance.titleFont != null) {
                    buttonBlockView.setTitleTypeface(appearance.titleFont.getTypeface(), getButtonViewState(state));
                }
                buttonBlockView.setBackgroundColor(appearance.backgroundColor, getButtonViewState(state));
                buttonBlockView.setBorder((float) appearance.borderWidth, appearance.borderColor, getButtonViewState(state));
                buttonBlockView.setCornerRadius((float) appearance.borderRadius, getButtonViewState(state));
            }
        }

        // Appearance
        BlockView blockView = (BlockView) holder.itemView;
        blockView.setBackgroundColor(block.getBackgroundColor());
        blockView.setBorder((float) block.getBorderWidth(), block.getBorderColor());
        blockView.setCornerRadius((float) block.getBorderRadius());

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

    private ButtonBlockView.State getButtonViewState(ButtonBlock.State state) {
        switch (state) {
            case Normal: return ButtonBlockView.State.Normal;
            case Highlighted: return ButtonBlockView.State.Highlighted;
            case Selected: return ButtonBlockView.State.Selected;
            case Disabled: return ButtonBlockView.State.Disabled;
            default: return ButtonBlockView.State.Normal;
        }
    }

    private class ButtonClickListener implements ButtonViewHolder.OnClickListener {
        @Override
        public void onButtonViewClick(ButtonBlockView view, int position) {
            ButtonBlock block = (ButtonBlock) getBlockAtPosition(position);
            Action action = block.getAction();

            if (action != null && mActionListener != null) {
                mActionListener.onAction(action);
            }
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(Context context) { super(new BlockView(context)); }
    }

    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        public ImageViewHolder(Context context) { super(new ImageBlockView(context)); }
    }

    public static class TextViewHolder extends RecyclerView.ViewHolder {
        public TextViewHolder(Context context) { super(new TextBlockView(context)); }
    }

    public static class ButtonViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        public interface OnClickListener {
            void onButtonViewClick(ButtonBlockView view, int position);
        }

        private OnClickListener mClickListener;

        public ButtonViewHolder(Context context) {
            super(new ButtonBlockView(context));
            itemView.setOnClickListener(this);
        }

        public void setOnClickListener(OnClickListener listner) { mClickListener = listner; }

        @Override
        public void onClick(View v) {
            if (mClickListener != null) {
                mClickListener.onButtonViewClick((ButtonBlockView) v, getAdapterPosition());
            }
        }
    }
}
