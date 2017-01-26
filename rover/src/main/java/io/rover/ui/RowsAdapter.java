package io.rover.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
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

import io.rover.model.Action;
import io.rover.model.Appearance;
import io.rover.model.Block;
import io.rover.model.ButtonBlock;
import io.rover.model.Image;
import io.rover.model.ImageBlock;
import io.rover.model.Row;
import io.rover.model.TextBlock;
import io.rover.model.WebBlock;

/**
 * Created by ata_n on 2016-06-28.
 */
public class RowsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements BlockLayoutManager.BlockProvider {

    public interface BlockListener {
        void onBlockClick(Block block);
    }

    public interface BoundsProvider {
        Rect getBounds(int position);
        Rect getRect(int position);
    }

    private List<Row> mRows;
    private BlockListener mBlockListener;
    private BoundsProvider mBoundsProvider;

    public RowsAdapter() {
    }

    public void setRows(List<Row> rows) {
        mRows = rows;
    }

    public void setBlockListener(BlockListener listener) {
        mBlockListener = listener;
    }

    public void setBoundsProvider(BoundsProvider provider) {
        mBoundsProvider = provider;
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
        } else if (block instanceof WebBlock) {
            return 3;
        }
        return super.getItemViewType(position);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final RecyclerView.ViewHolder viewHolder;
        switch (viewType) {
            case 0: {
                viewHolder = new ImageViewHolder(parent.getContext());
                break;
            }
            case 1: {
                viewHolder = new TextViewHolder(parent.getContext());
                break;
            }
            case 2: {
                viewHolder = new ButtonViewHolder(parent.getContext());
                break;
            }
            case 3: {
                viewHolder = new WebViewHolder(parent.getContext());
                break;
            }
            default: {
                viewHolder = new ViewHolder(parent.getContext());
                break;
            }
        }

        final RowsAdapter adapter = this;

        return viewHolder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        final Block block = getBlockAtPosition(position);

        if (holder.itemView instanceof ImageBlockView) {
            ((ImageBlockView) holder.itemView).clearImage();
        }

        if (block == null) {
            return;
        }

        Rect blockLayout = mBoundsProvider.getRect(position);
        int blockWidth = blockLayout.width();
        int blockHeight = blockLayout.height();

        if (block instanceof TextBlock) {
            TextBlock textBlock = (TextBlock) block;
            TextBlockView textBlockView = (TextBlockView) holder.itemView;

            textBlockView.setText(textBlock.getSpannedText());
            textBlockView.setTextOffset(textBlock.getTextOffset());
            textBlockView.setTextColor(textBlock.getTextColor());
            textBlockView.setTextSize(textBlock.getFont().getSize());
            textBlockView.setTextAlignment(textBlock.getTextAlignment());
            textBlockView.setTypeface(textBlock.getFont().getTypeface());
        } else if (block instanceof ImageBlock) {
            ImageBlock imageBlock = (ImageBlock) block;
            ImageBlockView imageBlockView = (ImageBlockView) holder.itemView;
            Image image = imageBlock.getImage();


            imageBlockView.clearImage();
            imageBlockView.cancelDownload();
            if (image != null) {
                String imageUrl = ImageUrlHelper.getOptimizedImageUrl(blockWidth,blockHeight,image, Image.ContentMode.Stretch, 1.0);
                imageBlockView.setImageUrl(imageUrl);
            } else {

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
                    buttonBlockView.setTitleTextSize(appearance.titleFont.getSize(), getButtonViewState(state));
                }
                buttonBlockView.setBackgroundColor(appearance.backgroundColor, getButtonViewState(state));
                buttonBlockView.setBorder((float) appearance.borderWidth, appearance.borderColor, getButtonViewState(state));
                buttonBlockView.setCornerRadius((float) appearance.borderRadius, getButtonViewState(state));
            }
        } else if (block instanceof WebBlock) {
            WebBlock webBlock = (WebBlock) block;
            WebBlockView webBlockView = (WebBlockView) holder.itemView;

            webBlockView.loadUrl(webBlock.getURL());
            webBlockView.setScrollable(webBlock.isScrollable());
        }

        // Appearance
        final BlockView blockView = (BlockView) holder.itemView;
        blockView.setBackgroundColor(block.getBackgroundColor());
        blockView.setBorder((float) block.getBorderWidth(), block.getBorderColor());
        blockView.setCornerRadius((float) block.getBorderRadius());
        blockView.setInset(block.getInset());
        blockView.setAlpha((float) block.getOpacity());

        if (!(blockView instanceof WebBlockView)) {
            if (block.getBackgroundImage() != null) {
                String imageUrl = ImageUrlHelper.getOptimizedImageUrl(blockWidth, blockHeight, block.getBackgroundImage(), block.getBackgroundContentMode(), block.getBackgroundScale());
                AssetManager.getSharedAssetManager(blockView.getContext())
                        .fetchAsset(imageUrl, new AssetManager.AssetManagerListener() {
                            @Override
                            public void onAssetSuccess(Bitmap bitmap) {
                                BackgroundImageHelper.setBackgroundImage(
                                        blockView.getBackgroundView(),
                                        bitmap,
                                        blockView.getResources().getDisplayMetrics().density,
                                        (float) block.getBackgroundScale(),
                                        block.getBackgroundContentMode(),
                                        blockView.getResources());
                            }

                            @Override
                            public void onAssetFailure() {

                            }
                        });
            } else {
                blockView.getBackgroundView().setImageDrawable(null);
            }
        }

        if (mBoundsProvider != null && mBoundsProvider.getBounds(position) != null) {
            blockView.setClipBounds(mBoundsProvider.getBounds(position));
        } else {
            blockView.setClipBounds(null);
        }

        final RowsAdapter adapter = this;

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adapter.onBlockClick(v, block);
            }
        });
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
            case Normal:
                return ButtonBlockView.State.Normal;
            case Highlighted:
                return ButtonBlockView.State.Highlighted;
            case Selected:
                return ButtonBlockView.State.Selected;
            case Disabled:
                return ButtonBlockView.State.Disabled;
            default:
                return ButtonBlockView.State.Normal;
        }
    }

    private void onBlockClick(View view, Block block) {
        if (block != null && mBlockListener != null) {
            mBlockListener.onBlockClick(block);
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(Context context) {
            super(new BlockView(context));
        }
    }

    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        public ImageViewHolder(Context context) {
            super(new ImageBlockView(context));
        }
    }

    public static class TextViewHolder extends RecyclerView.ViewHolder {
        public TextViewHolder(Context context) {
            super(new TextBlockView(context));
        }
    }

    public static class ButtonViewHolder extends RecyclerView.ViewHolder {
        public ButtonViewHolder(Context context) {
            super(new ButtonBlockView(context));
        }
    }

    public static class WebViewHolder extends RecyclerView.ViewHolder {
        public WebViewHolder(Context context) {
            super(new WebBlockView(context));
        }
    }
}
