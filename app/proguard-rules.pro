# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile


# --- ViewBinding ---
-keep class **.databinding.*Binding { *; }
-keep class **.databinding.* { *; }
-keepclassmembers class * extends androidx.viewbinding.ViewBinding {
    public static *** bind(android.view.View);
}

# --- WebView: permet d’éviter les erreurs d’obfuscation ---
-keepclassmembers class * extends android.webkit.WebView {
   public <init>(android.content.Context);
   public <init>(android.content.Context, android.util.AttributeSet);
   public void loadUrl(java.lang.String);
   public void setWebViewClient(android.webkit.WebViewClient);
   public void setWebChromeClient(android.webkit.WebChromeClient);
}

# --- Classes principales Android ---
-keep class androidx.appcompat.** { *; }
-keep class com.google.android.material.** { *; }
-keep class androidx.constraintlayout.** { *; }

# --- Pour conserver les annotations utiles ---
-keepattributes *Annotation*

# --- Garder les noms des classes de test (si jamais incluses) ---
-keep class *Test* { *; }

# --- Garder les classes si tu fais des appels réflexifs ---
# Exemple : Class.forName("com.example.MyClass")
-keepnames class * {
    public protected *;
}

# --- Supprimer les logs en release (optionnel) ---
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
