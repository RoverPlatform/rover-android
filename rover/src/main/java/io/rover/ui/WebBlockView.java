package io.rover.ui;

import android.content.Context;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * Created by Rover Labs Inc on 2016-09-09.
 */
public class WebBlockView extends BlockView {

    private WebView mWebView;

    public WebBlockView(Context context) {
        super(context);
        mWebView = new WebView(context);
        mWebView.getSettings().setDomStorageEnabled(true);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.setHorizontalScrollBarEnabled(false);
        mWebView.setWebViewClient(new WebViewClient(){
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        addView(mWebView);
    }

    @Override
    public boolean hasBackgroundImage() {
        return false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mWebView.setLayoutParams(new FrameLayout.LayoutParams(w, h));
    }

    public void loadUrl(String url) {
        mWebView.loadUrl(url);
    }

    public void setScrollable(boolean scrollable) {
        mWebView.setVerticalScrollBarEnabled(scrollable);
    }
}
