package com.gberguy.ecpatches.runtime;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class CookingSortRefresh {
    public interface Ordered { }

    private CookingSortRefresh() { }

    public static <T> void restoreOrder(List<T> items, Comparator<T> comparator) {
        if (comparator instanceof Ordered) {
            items.removeIf(Objects::isNull);
            items.sort(comparator);
        }
    }
}
