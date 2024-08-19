package com.showroomafrica.annuaire;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    WebView mWebview;
    SwipeRefreshLayout swipeRefreshLayout;
    ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialisation des vues
        progressBar = findViewById(R.id.progressBar);
        mWebview = findViewById(R.id.webview);
        swipeRefreshLayout = findViewById(R.id.reload);

        // Contrôle des barres système
        WindowInsetsControllerCompat windowInsetsController =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        windowInsetsController.setAppearanceLightStatusBars(true);

        // Vérification de la connexion Internet
        if (!isNetworkAvailable()) {
            showError("Pas de connexion Internet disponible");
            return;
        }

        // Configuration sécurisée du WebView
        configureSecureWebView();

        // Chargement du site
        loadSecureUrl("https://www.showroomafrica.com/");

        // Gestion du swipe to refresh
        setupSwipeRefresh();

        // Gestion du bouton back
        setupBackButton();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private void configureSecureWebView() {
        WebSettings webSettings = mWebview.getSettings();

        // Configuration de sécurité
        webSettings.setJavaScriptEnabled(true); // Active si besoin uniquement
        webSettings.setDomStorageEnabled(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webSettings.setAllowFileAccess(false);
        webSettings.setAllowContentAccess(false);
        webSettings.setAllowFileAccessFromFileURLs(false);
        webSettings.setAllowUniversalAccessFromFileURLs(false);

        // Performances
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);

        mWebview.setWebViewClient(new SecureWebViewClient());
    }

    private void loadSecureUrl(String url) {
        if (url.startsWith("https://www.showroomafrica.com")) {
            Log.d("WEBVIEW_NAVIGATION", "Chargement de : " + url);
            mWebview.loadUrl(url);
        } else {
            showError("URL non autorisée");
        }
    }

    private void setupSwipeRefresh() {
        mWebview.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                swipeRefreshLayout.setEnabled(scrollY == 0));

        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (isNetworkAvailable()) {
                mWebview.reload();
            } else {
                swipeRefreshLayout.setRefreshing(false);
                showError("Pas de connexion Internet");
            }
        });
    }

    private void setupBackButton() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mWebview.canGoBack()) {
                    mWebview.goBack();
                } else {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Quitter l'application")
                            .setMessage("Voulez-vous vraiment quitter ?")
                            .setPositiveButton("Oui", (dialog, which) -> finish())
                            .setNegativeButton("Non", null)
                            .show();
                }
            }
        });
    }

    private void showError(String message) {
        progressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        // Afficher une page d'erreur locale
        mWebview.loadUrl("about:blank");
        mWebview.loadUrl("file:///android_res/raw/error.html");
    }

    private class SecureWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            if (url.startsWith("file://") || url.contains("javascript:")) {
                return true;
            }

            if (url.startsWith("https://www.showroomafrica.com")) {
                return false;
            }

            try {
                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                CustomTabsIntent customTabsIntent = builder.build();
                customTabsIntent.launchUrl(MainActivity.this, Uri.parse(url));
            } catch (Exception e) {
                Log.e("SECURITY", "Erreur d'ouverture de l'URL externe", e);
                // Fallback si Custom Tabs indisponible
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
            }

            return true;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);

            if (request.isForMainFrame()) {
                showError("Erreur de chargement");
                Log.e("WEBVIEW_ERROR", "Code: " + error.getErrorCode() + " - " + error.getDescription());
            }
        }


        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            if (request.isForMainFrame()) {
                showError("Erreur HTTP " + errorResponse.getStatusCode());
            }
        }
    }
}
