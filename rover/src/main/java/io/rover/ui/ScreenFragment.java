package io.rover.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import java.util.ArrayList;

import io.rover.R;
import io.rover.model.Action;
import io.rover.model.Block;
import io.rover.model.Image;
import io.rover.model.Row;
import io.rover.model.Screen;

public class ScreenFragment extends Fragment implements RowsAdapter.ActionListener {

    public interface OnActionListener {
        void onAction(Action action);
    }

    public static String TAG = "SCREEN_FRAGMENT";
    public static String ARG_SCREEN = "SCREEN_KEY";

    private Screen mScreen;
    private RowsAdapter mAdapter;
    private ImageView mBackgroundView;

    public ScreenFragment() {}

    public static ScreenFragment newInstance(Screen screen) {
        ScreenFragment fragment = new ScreenFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_SCREEN, screen);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mScreen = getArguments().getParcelable(ARG_SCREEN);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        mAdapter = new RowsAdapter();
        mAdapter.setActionListener(this);
        mAdapter.setRows(new ArrayList<Row>(0));

        BlockLayoutManager layoutManager = new BlockLayoutManager(getActivity());
        layoutManager.setBlockProvider(mAdapter);

        RecyclerView recyclerView = new RecyclerView(getActivity());
        recyclerView.setAdapter(mAdapter);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setLayoutParams(new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        recyclerView.setChildDrawingOrderCallback(new RecyclerView.ChildDrawingOrderCallback() {
            @Override
            public int onGetChildDrawingOrder(int childCount, int i) {
                return childCount - i - 1;
            }
        });

        RelativeLayout layout = new RelativeLayout(getActivity());
        layout.setLayoutParams(new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        mBackgroundView = new ImageView(getActivity());
        mBackgroundView.setLayoutParams(new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        layout.addView(mBackgroundView);
        layout.addView(recyclerView);

        return layout;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mAdapter = null;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mScreen != null) {
            setScreen(mScreen);
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onAction(Action action) {
        if (getActivity() instanceof OnActionListener) {
            ((OnActionListener) getActivity()).onAction(action);
        } else {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(action.getUrl()));
            startActivity(intent);
        }
    }

    public void setScreen(final Screen screen) {
        String title = screen.getTitle();
        if (title != null) {
            getActivity().setTitle(screen.getTitle());
        }
        mAdapter.setRows(screen.getRows());

        if (!screen.useDefaultActionBarStyle()) {

            View view = getView();
            if (view == null) {
                Log.e("ScreenFragment", "Fragment not properly attached.");
                return;
            }

            final ViewGroup rootLayout = (ViewGroup) view.getParent();

            mBackgroundView.setBackgroundColor(screen.getBackgroundColor());

            Image backgroundImage = screen.getBackgroundImage();
            if (backgroundImage != null) {
                AssetManager.getSharedAssetManager(getContext()).fetchAsset(backgroundImage.getImageUrl(), new AssetManager.AssetManagerListener() {
                    @Override
                    public void onAssetSuccess(Bitmap bitmap) {
                        setBackground(bitmap);
                    }

                    @Override
                    public void onAssetFailure() {

                    }
                });
            }

            ActionBar actionBar = ((AppCompatActivity)getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setBackgroundDrawable(new ColorDrawable(screen.getActionBarColor()));

                if (title != null) {
                    Spannable text = new SpannableString(title);
                    text.setSpan(new ForegroundColorSpan(screen.getTitleColor()), 0,
                            text.length(),
                            Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    actionBar.setTitle(text);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Window window = getActivity().getWindow();
                    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    window.setStatusBarColor(screen.getStatusBarColor());


                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && screen.isStatusBarLight()) {
                        rootLayout.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                    }
                }

            }
        }
    }

    private void setBackground(Bitmap bitmap) {
        if (mScreen == null) {
            return;
        }

        BackgroundImageHelper.setBackgroundImage(
                mBackgroundView,
                bitmap,
                getResources().getDisplayMetrics().density,
                (float) mScreen.getBackgroundScale(),
                mScreen.getBackgroundContentMode(),
                getResources());
    }

    public RowsAdapter getAdapter() {
        return mAdapter;
    }
}
