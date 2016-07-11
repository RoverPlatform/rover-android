package io.rover.ui;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Layout;
import android.util.Log;

import io.rover.R;
import io.rover.model.Action;
import io.rover.model.Block;
import io.rover.model.Screen;

public class ScreenActivity extends AppCompatActivity implements RowsAdapter.ActionListener {

    private Screen mScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen);

        Bundle bundle = getIntent().getExtras();
        Screen screen = bundle.getParcelable("EXTRA_SCREEN");
        if (screen != null) {
            mScreen = screen;
        } else {
            Log.e("ScreenActivity", "'EXTRA_SCREEN' not found in the intent.");
            return;
        }

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerView);

        RowsAdapter adapter = new RowsAdapter(screen.getRows());
        adapter.setActionListener(this);
        recyclerView.setAdapter(adapter);

        BlockLayoutManager layoutManager = new BlockLayoutManager(getApplicationContext());
        layoutManager.setBlockProvider(adapter);

        recyclerView.setLayoutManager(layoutManager);
    }

    @Override
    public void onAction(Action action) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(action.getUrl()));
        startActivity(intent);
    }
}
