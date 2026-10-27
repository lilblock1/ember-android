package fixture;

import java.util.function.Supplier;

/** Original reproducer for Enum/interface intersection-array conversion, no game code. */
public final class EnumIntersectionFixture {
    interface Named { String nameValue(); }
    enum Choice implements Named { ONE, TWO; public String nameValue() { return name(); } }
    private static <T extends Named> int count(T[] values) { return values.length; }
    private static <E extends Enum<E> & Named> int from(Supplier<E[]> values) {
        E[] array = values.get();
        return count(array);
    }
    public static int run() { return from(Choice::values); }
}
