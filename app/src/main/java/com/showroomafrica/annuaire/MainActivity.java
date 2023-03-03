package com.showroomafrica.annuaire;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends AppCompatActivity {

    WebView mWebview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mWebview = findViewById(R.id.webview);
        WebSettings webSettings = mWebview.getSettings();
        webSettings.setJavaScriptEnabled(true);
        mWebview.loadUrl("https://www.showroomafrica.com/");
        mWebview.setWebViewClient(new WebViewClient());
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