package fixture;

// Original test fixture. No Minecraft source or assets.
public final class CompatibilityFixture {
    public static Class<?> desktopDependency() { return java.awt.Desktop.class; }
    public static Object reflect() throws ReflectiveOperationException {
        return Class.forName("java.lang.StringBuilder").getDeclaredConstructor().newInstance();
    }
    public static native long desktopNativeHandle();
}
