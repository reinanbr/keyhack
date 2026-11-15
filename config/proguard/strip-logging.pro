# Release builds strip every android.util.Log call (threat model: accidental
# leakage). Code must still never pass typed text, clipboard contents, file
# contents or API keys to a logger at any level: this is a second line of
# defence, not permission to log secrets in debug builds.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int) return false;
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
    public static java.lang.String getStackTraceString(java.lang.Throwable);
}
