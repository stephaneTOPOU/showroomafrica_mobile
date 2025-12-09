package com.showroomafrica.annuaire;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Network;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.view.WindowInsetsControllerCompat;

import com.showroomafrica.annuaire.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private static final String TAG = "MainActivity";
    private static final String HOME_URL = "https://www.showroomafrica.com/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Apparence barre système
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);

        // Vérifier internet
        if (!isNetworkAvailable()) {
            showError("Pas de connexion Internet disponible");
            return;
        }

        configureSecureWebView();
        binding.webview.loadUrl(HOME_URL);
        setupSwipeRefresh();
        setupBackButton();
    }

    /** Vérification réseau propre */
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        Network network = cm.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities nc = cm.getNetworkCapabilities(network);
        return nc != null &&
                nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    /** Configuration WebView sécurisée */
    private void configureSecureWebView() {
        WebSettings settings = binding.webview.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);

        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // WebView sécurisé
        binding.webview.setWebViewClient(new SecureWebViewClient());

        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
    }

    /** Client Web sécurisé */
    private class SecureWebViewClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            binding.progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            binding.progressBar.setVisibility(View.GONE);
            binding.reload.setRefreshing(false);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String url = uri.toString();

            // Bloquer file://, javascript:// etc.
            if (!"https".equals(uri.getScheme())) {
                return true;
            }

            // Navigation interne : autorisée
            if (url.startsWith(HOME_URL)) {
                return false;
            }

            // Liens externes : Custom Tabs
            try {
                CustomTabsIntent tabs = new CustomTabsIntent.Builder().build();
                tabs.launchUrl(MainActivity.this, uri);
            } catch (Exception e) {
                Log.e(TAG, "Erreur ouverture URL externe", e);
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }

            return true;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
            if (req.isForMainFrame()) {
                showError("Erreur de chargement");
                Log.e(TAG, "Code: " + error.getErrorCode() + " - " + error.getDescription());
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest req,
                                        WebResourceResponse errorResponse) {
            if (req.isForMainFrame()) {
                showError("Erreur HTTP " + errorResponse.getStatusCode());
            }
        }
    }

    /** Swipe-to-refresh */
    private void setupSwipeRefresh() {
        binding.webview.setOnScrollChangeListener((v, x, y, ox, oy) ->
                binding.reload.setEnabled(y == 0));

        binding.reload.setOnRefreshListener(() -> {
            if (isNetworkAvailable()) {
                binding.webview.reload();
            } else {
                binding.reload.setRefreshing(false);
                showError("Pas de connexion Internet");
            }
        });
    }

    /** Bouton retour */
    private void setupBackButton() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.webview.canGoBack()) {
                    binding.webview.goBack();
                } else {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Quitter l'application")
                            .setMessage("Voulez-vous vraiment quitter ?")
                            .setPositiveButton("Oui", (d, w) -> finish())
                            .setNegativeButton("Non", null)
                            .show();
                }
            }
        });
    }

    /** Affichage erreurs */
    private void showError(String msg) {
        //Log.e("WEBVIEW", "showError() appelé : " + msg);
        binding.progressBar.setVisibility(View.GONE);
        binding.reload.setRefreshing(false);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

        binding.webview.loadUrl("about:blank");
        binding.webview.loadUrl("file:///android_res/raw/error.html");
    }

    /** Nettoyage WebView */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (binding != null) {
            WebView wv = binding.webview;

            wv.loadUrl("about:blank");
            wv.stopLoading();

            wv.setWebChromeClient(new WebChromeClient());
            wv.setWebViewClient(new WebViewClient());

            wv.destroy();
            binding = null;
        }
    }
}
