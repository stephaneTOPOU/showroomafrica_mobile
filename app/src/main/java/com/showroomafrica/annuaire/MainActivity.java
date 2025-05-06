package com.showroomafrica.annuaire;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
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

        // Utilisation de ViewBinding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Apparence barre système (texte sombre)
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);

        // Vérification connexion internet
        if (!isNetworkAvailable()) {
            showError("Pas de connexion Internet disponible");
            return;
        }

        configureSecureWebView();
        loadSecureUrl(HOME_URL);
        setupSwipeRefresh();
        setupBackButton();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29+
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null &&
                    (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            // Pour les versions antérieures, isConnected() est déprécié mais toujours fonctionnel
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
    }


    private void configureSecureWebView() {
        WebSettings settings = binding.webview.getSettings();

        // Sécurité
        settings.setJavaScriptEnabled(true); // activer uniquement si nécessaire
        settings.setDomStorageEnabled(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);

        // Performance
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE); // Pas de cache pour contenu dynamique

        // WebViewClient personnalisé
        binding.webview.setWebViewClient(new SecureWebViewClient());

        // Désactiver zoom (optionnel)
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
    }

    private void loadSecureUrl(String url) {
        if (url.startsWith(HOME_URL)) {
            Log.d(TAG, "Chargement URL : " + url);
            binding.webview.loadUrl(url);
        } else {
            showError("URL non autorisée");
        }
    }

    private void setupSwipeRefresh() {
        binding.webview.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                binding.reload.setEnabled(scrollY == 0));

        binding.reload.setOnRefreshListener(() -> {
            if (isNetworkAvailable()) {
                binding.webview.reload();
            } else {
                binding.reload.setRefreshing(false);
                showError("Pas de connexion Internet");
            }
        });
    }

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
                            .setPositiveButton("Oui", (dialog, which) -> finish())
                            .setNegativeButton("Non", null)
                            .show();
                }
            }
        });
    }

    private void showError(String message) {
        binding.progressBar.setVisibility(View.GONE);
        binding.reload.setRefreshing(false);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        // Charge une page d'erreur locale si possible
        binding.webview.loadUrl("about:blank");
        binding.webview.loadUrl("file:///android_res/raw/error.html");
    }

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
            String url = request.getUrl().toString();

            if (url.startsWith("file://") || url.contains("javascript:")) {
                // Bloquer les URL dangereuses
                return true;
            }

            if (url.startsWith(HOME_URL)) {
                // Navigation interne dans WebView
                return false;
            }

            // URLs externes ouvertes via Custom Tabs ou navigateur
            try {
                CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
                customTabsIntent.launchUrl(MainActivity.this, Uri.parse(url));
            } catch (Exception e) {
                Log.e(TAG, "Erreur ouverture URL externe", e);
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }

            return true;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);

            if (request.isForMainFrame()) {
                showError("Erreur de chargement");
                Log.e(TAG, "Code: " + error.getErrorCode() + " - " + error.getDescription());
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

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Nettoyer le WebView pour éviter fuites mémoire
        if (binding.webview != null) {
            binding.webview.loadUrl("about:blank");
            binding.webview.stopLoading();
            binding.webview.setWebChromeClient(null);
            binding.webview.setWebViewClient(null);
            binding.webview.destroy();
        }

        binding = null;
    }
}
