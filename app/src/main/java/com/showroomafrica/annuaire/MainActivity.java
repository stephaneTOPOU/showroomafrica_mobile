package com.showroomafrica.annuaire;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends AppCompatActivity {

    WebView mWebview;
    SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        WindowInsetsControllerCompat windowInsetsController =
                ViewCompat.getWindowInsetsController(getWindow().getDecorView());
        if (windowInsetsController == null) {
            return;
        }

        //windowInsetsController.setAppearanceLightNavigationBars(true);
        setContentView(R.layout.activity_main);


        mWebview = findViewById(R.id.webview);
        swipeRefreshLayout = findViewById(R.id.reload);

        WebSettings webSettings = mWebview.getSettings();
        webSettings.setJavaScriptEnabled(true);

        mWebview.loadUrl("https://www.showroomafrica.com/");
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                mWebview.reload();
            }
        });
        mWebview.setWebViewClient(new WebViewClient(){
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefreshLayout.setRefreshing(false);
            }
        });

    }


    @Override
    public void onBackPressed() {
        if (mWebview.canGoBack()){
            mWebview.goBack();
        }else {
            super.onBackPressed();
        }
    }
}