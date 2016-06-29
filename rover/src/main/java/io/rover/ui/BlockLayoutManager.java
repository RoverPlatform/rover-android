package io.rover.ui;

import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;

import io.rover.model.Alignment;
import io.rover.model.Block;
import io.rover.model.PercentageUnit;
import io.rover.model.PointsUnit;
import io.rover.model.Row;
import io.rover.model.Unit;

/**
 * Created by ata_n on 2016-06-28.
 */
public class BlockLayoutManager extends RecyclerView.LayoutManager{

    public interface BlockProvider {
        int getRowCount();
        Row getRow(int index);
    }

    private Rect[][] mLayoutInfo;
    private BlockProvider mBlockProvider;
    private float density;
    private int verticalScrollOffset = 0;
    private int bottomLimit = 0;

    public BlockLayoutManager(Context context) {
        density = context.getResources().getDisplayMetrics().density;
    }

    public void setBlockProvider(BlockProvider blockProvider) {
        mBlockProvider = blockProvider;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.WRAP_CONTENT, RecyclerView.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        // TODO: CHECK FOR STATE
        prepareLayout();
        fillVisibleChildren(recycler);
    }

    @Override
    public boolean canScrollVertically() {
        return true;
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        // TODO: CHECK FOR STATE
        int travel;
        int verticalSpace = getVerticalSpace();

        if(dy + verticalScrollOffset < 0 /* topLimit */){
            travel = verticalScrollOffset;
            verticalScrollOffset = 0;
        }
        else if(dy + verticalScrollOffset + verticalSpace > bottomLimit){
            travel = bottomLimit - verticalScrollOffset - verticalSpace;
            verticalScrollOffset = bottomLimit - verticalSpace;
        }
        else{
            travel = dy;
            verticalScrollOffset += dy;
        }
        fillVisibleChildren(recycler);
        return travel;
    }

    private void prepareLayout() {
        if (mBlockProvider == null) {
            Log.e("BlockLayoutManager", "No BlockProvider set.");
            return;
        }

        double height = 0;

        int numRows = mBlockProvider.getRowCount();
        mLayoutInfo = new Rect[numRows][];
        for (int i = 0; i < numRows; i++) {

            // TODO: fix height so that it comes from here, doesnt need to come from block provider?
            double rowHeight = getHeightForRow(i);
            double yOffset = height;

            int numBlocks = getBlockCount(i);
            mLayoutInfo[i] = new Rect[numBlocks];
            for (int j = 0; j < numBlocks; j++) {

                Block block = getBlock(i, j);
                boolean isStacked = block.getPosition() == Block.Position.Stacked;

                mLayoutInfo[i][j] = getRectForBlock(block, isStacked ? yOffset : rowHeight, rowHeight);

                yOffset += getFullHeightForItem(block, rowHeight);
                bottomLimit = Math.max(bottomLimit, (int)yOffset);
            }

            height += rowHeight;
        }
    }

    private void fillVisibleChildren(RecyclerView.Recycler recycler) {
        detachAndScrapAttachedViews(recycler);

        int position = 0;

        for (int i = 0; i < mLayoutInfo.length; i++) {
            for (int j = 0; j < mLayoutInfo[i].length; j++) {
                Rect layoutInfo = mLayoutInfo[i][j];
                if (isVisible(layoutInfo)) {
                    View view = recycler.getViewForPosition(position);
                    addView(view);
                    layoutDecorated(view, layoutInfo.left, layoutInfo.top - verticalScrollOffset, layoutInfo.right, layoutInfo.bottom - verticalScrollOffset);
                }

                position += 1;
            }
        }

        for (RecyclerView.ViewHolder viewHolder : recycler.getScrapList()) {
            removeAndRecycleView(viewHolder.itemView, recycler);
        }
    }

    private boolean isVisible(Rect rect) {
        return rect.bottom >= verticalScrollOffset && rect.top <= getVerticalSpace() + verticalScrollOffset;
    }

    private Rect getRectForBlock(Block block, double yOffset, double rowHeight) {
        Alignment alignment = block.getAlignment();

        double left, top, right, bottom;
        double parentWidth = getWidth();

        // Horizontal Layout
        switch (alignment.getHorizontal()) {
            case Fill: {
                left = getValueFromUnit(block.getOffset().getLeft(), parentWidth);
                right = parentWidth - getValueFromUnit(block.getOffset().getRight(), parentWidth);
                break;
            }
            case Left: {
                left = getValueFromUnit(block.getOffset().getLeft(), parentWidth);
                double width = getValueFromUnit(block.getWidth(), parentWidth);
                right = left + width;
                break;
            }
            case Right: {
                right = parentWidth - getValueFromUnit(block.getOffset().getRight(), parentWidth);
                double width = getValueFromUnit(block.getWidth(), parentWidth);
                left = right - width;
                break;
            }
            case Center: {
                double centerOffset = getValueFromUnit(block.getOffset().getCenter(), parentWidth);
                double width = getValueFromUnit(block.getWidth(), parentWidth);
                left = ((parentWidth - width) / 2) + centerOffset;
                right = left + width;
                break;
            }
            default: {
                left = 0;
                right = 0;
            }
        }

        // Vertical Layout
        switch (alignment.getVertical()) {
            case Fill: {
                top = getValueFromUnit(block.getOffset().getTop(), rowHeight);
                bottom = rowHeight - getValueFromUnit(block.getOffset().getBottom(), rowHeight);
                break;
            }
            case Top: {
                top = getValueFromUnit(block.getOffset().getTop(), rowHeight);
                double height = getHeightForBlock(block, rowHeight);
                bottom = top + height;
                break;
            }
            case Bottom: {
                bottom = rowHeight - getValueFromUnit(block.getOffset().getBottom(), rowHeight);
                double height = getHeightForBlock(block, rowHeight);
                top = bottom - height;
                break;
            }
            case Middle: {
                double middleOffset = getValueFromUnit(block.getOffset().getMiddle(), rowHeight);
                double height = getHeightForBlock(block, rowHeight);
                top = ((rowHeight - height) / 2) + middleOffset;
                bottom = top + height;
                break;
            }
            default: {
                top = 0;
                bottom = 0;
                break;
            }
        }

        // yOffset Adjustment
        top += yOffset;
        bottom += yOffset;


        return new Rect((int)left, (int)top, (int)right, (int)bottom);
    }

    private double getFullHeightForItem(Block block, double rowHeight) {
        switch (block.getPosition()) {
            case Floating:
                return 0.0;
            case Stacked: {
                double top = getValueFromUnit(block.getOffset().getTop(), rowHeight);
                double height = getHeightForBlock(block, rowHeight);
                double bottom = getValueFromUnit(block.getOffset().getBottom(), rowHeight);

                return top + height + bottom;
            }
            default: {
                return 0.0;
            }
        }
    }

    private double getHeightForBlock(Block block, double rowHeight) {
        double height;

        Unit blockHeight = block.getHeight();
        if (blockHeight != null) {
            height = getValueFromUnit(blockHeight, rowHeight);
        } else {
            // TODO: other block types calculations go here
            height = 0;
        }

        return height;
    }

    private double getHeightForRow(int index) {
        Row row = mBlockProvider.getRow(index);
        Unit fixedHeight = row.getHeight();
        if (fixedHeight != null) {
            return getValueFromUnit(fixedHeight, getHeight());
        }

        double height = 0;
        for (Block block : row.getBlocks()) {
            height += getFullHeightForItem(block, 0);
        }
        return height;
    }

    private double getValueFromUnit(Unit unit, double parentValue) {
        if (unit instanceof PercentageUnit) {
            return unit.getValue() * parentValue / 100.0;
        } else {
            return unit.getValue() * density;
        }
    }

    private Block getBlock(int row, int index) {
        return mBlockProvider.getRow(row).getBlocks().get(index);
    }

    private int getBlockCount(int row) {
        return mBlockProvider.getRow(row).getBlocks().size();
    }

    private int getVerticalSpace() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }
}
