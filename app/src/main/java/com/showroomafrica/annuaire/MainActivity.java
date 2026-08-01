package com.showroomafrica.annuaire;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.showroomafrica.annuaire.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private static final String TAG = "MainActivity";
    private static final String HOME_URL = "https://www.showroomafrica.com/";

    // Callback pour le sélecteur de fichiers (upload depuis <input type="file">)
    private ValueCallback<Uri[]> filePathCallback;

    // Launcher pour choisir un fichier (photo, document, etc.)
    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (filePathCallback == null) return;

                Uri[] results = null;
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String dataString = result.getData().getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    } else if (result.getData().getClipData() != null) {
                        int count = result.getData().getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = result.getData().getClipData().getItemAt(i).getUri();
                        }
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            });

    // Launcher pour la permission de notification (Android 13+, requise pour afficher
    // la progression des téléchargements gérés par DownloadManager)
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // Rien à faire : le téléchargement fonctionne même sans notification visible
            });

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

        requestNotificationPermissionIfNeeded();
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

    /** Demande la permission POST_NOTIFICATIONS sur Android 13+ (utile pour DownloadManager) */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    /** Configuration WebView sécurisée */
    private void configureSecureWebView() {
        WebSettings settings = binding.webview.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);

        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // Cookies (nécessaire pour l'authentification côté site si applicable)
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webview, false);

        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);

        binding.webview.setWebViewClient(new SecureWebViewClient());
        binding.webview.setWebChromeClient(new SecureWebChromeClient());

        // Téléchargements déclenchés depuis la page (PDF, images, fichiers, etc.)
        binding.webview.setDownloadListener(new SecureDownloadListener());
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

    /** Client Chrome : gère l'upload de fichiers (<input type="file">) */
    private class SecureWebChromeClient extends WebChromeClient {

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                         FileChooserParams fileChooserParams) {
            // Annule un éventuel callback précédent resté en attente
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
            }
            filePathCallback = callback;

            try {
                Intent intent = fileChooserParams.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                fileChooserLauncher.launch(intent);
            } catch (Exception e) {
                Log.e(TAG, "Erreur ouverture sélecteur de fichiers", e);
                filePathCallback = null;
                return false;
            }
            return true;
        }
    }

    /** Gère les téléchargements initiés par la page web (PDF, images, etc.) */
    private class SecureDownloadListener implements DownloadListener {

        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                    String mimeType, long contentLength) {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);

                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) {
                    request.addRequestHeader("cookie", cookies);
                }
                request.addRequestHeader("User-Agent", userAgent);

                String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                request.setDescription("Téléchargement en cours…");
                request.setTitle(fileName);
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(request);
                    Toast.makeText(MainActivity.this, "Téléchargement démarré : " + fileName, Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors du téléchargement", e);
                Toast.makeText(MainActivity.this, "Impossible de télécharger le fichier", Toast.LENGTH_LONG).show();
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