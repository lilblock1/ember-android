package net.openid.appauth.internal;

/**
 * Privacy adapter for pinned AppAuth 0.11.1. Installs before any AppAuth activity or request.
 * Uses its package-visible logger constructor; re-audit this boundary on dependency upgrades.
 */
public final class PrivateLogging {
    private PrivateLogging() {}

    public static void install() {
        Logger.setInstance(new Logger(new Logger.LogWrapper() {
            @Override public void println(int level, String tag, String message) { }
            @Override public boolean isLoggable(String tag, int level) { return false; }
            @Override public String getStackTraceString(Throwable error) { return ""; }
        }));
    }
}
