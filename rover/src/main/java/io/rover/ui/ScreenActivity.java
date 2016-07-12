package io.rover.ui;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import io.rover.R;
import io.rover.model.Action;
import io.rover.model.Block;
import io.rover.model.Screen;

public class ScreenActivity extends AppCompatActivity implements RowsAdapter.ActionListener {

    public static String INTENT_EXTRA_SCREEN = "EXTRA_SCREEN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RelativeLayout relativeLayout = new RelativeLayout(this);
        relativeLayout.setLayoutParams(new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        RowsAdapter adapter = new RowsAdapter();
        adapter.setActionListener(this);

        BlockLayoutManager layoutManager = new BlockLayoutManager(this);
        layoutManager.setBlockProvider(adapter);

        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setLayoutParams(new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        relativeLayout.addView(recyclerView);

        setContentView(relativeLayout);

        Bundle bundle = getIntent().getExtras();
        Screen screen = bundle.getParcelable(INTENT_EXTRA_SCREEN);
        if (screen != null) {

            String title = screen.getTitle();
            if (title != null) {
                setTitle(screen.getTitle());
            }
            adapter.setRows(screen.getRows());

            if (!screen.useDefaultActionBarStyle()) {

                relativeLayout.setBackgroundColor(screen.getBackgroundColor());
                ActionBar actionBar = getSupportActionBar();
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
                        Window window = getWindow();
                        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                        window.setStatusBarColor(screen.getStatusBarColor());
                    }

                    relativeLayout.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                }
            }
        }
    }

    @Override
    public void onAction(Action action) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(action.getUrl()));
        startActivity(intent);
    }
}
