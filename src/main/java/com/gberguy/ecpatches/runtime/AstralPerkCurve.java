package com.gberguy.ecpatches.runtime;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AstralPerkCurve {
    public static final long MAX_EXACT_XP = 9007199254740991L;
    private static final int PROTOCOL = 1;
    public final int cap;
    public final boolean custom;
    public final Map<Integer, Long> thresholds;

    public AstralPerkCurve(boolean custom, long[] values) {
        if (values.length < 1 || values.length > 100) throw new IllegalArgumentException("Astral level cap must be 1..100");
        Map<Integer, Long> map = new LinkedHashMap<>();
        long previous = 0;
        for (int index = 0; index < values.length; index++) {
            long value = values[index];
            if (value <= previous || (custom && value > MAX_EXACT_XP)) {
                throw new IllegalArgumentException("Threshold i=" + (index + 1) + " must increase above " + previous
                        + (custom ? " and be at most " + MAX_EXACT_XP : "") + "; got " + value);
            }
            map.put(index + 1, value);
            previous = value;
        }
        this.cap = values.length;
        this.custom = custom;
        this.thresholds = Collections.unmodifiableMap(map);
    }

    public static AstralPerkCurve calculate(String formula, int cap) {
        if (cap < 1 || cap > 100) throw new IllegalArgumentException("Astral level cap must be 1..100");
        AstralFormula compiled = AstralFormula.compile(formula);
        long[] thresholds = new long[cap];
        long previous = 0;
        for (int i = 1; i <= cap; i++) {
            try {
                double result = compiled.evaluate(i, previous);
                if (result < 1 || result > MAX_EXACT_XP) throw new IllegalArgumentException("Result must be 1.." + MAX_EXACT_XP + "; got " + result);
                long threshold = (long) StrictMath.floor(result);
                if (threshold <= previous) throw new IllegalArgumentException("Cumulative threshold must increase above " + previous + "; got " + threshold);
                thresholds[i - 1] = previous = threshold;
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("astralPerkLevelFormula failed at i=" + i + ": " + invalid.getMessage() + "; formula=" + formula, invalid);
            }
        }
        for (int level = 1; level < cap; level++) {
            long start = level == 1 ? 0 : thresholds[level - 1];
            long span = thresholds[level] - start;
            if ((long) ((float) span * 0.08F) < 1) {
                throw new IllegalArgumentException("astralPerkLevelFormula gives level " + level + " an XP award cap below 1; increase threshold spacing");
            }
        }
        return new AstralPerkCurve(true, thresholds);
    }

    public byte[] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(12 + 8 * cap);
        buffer.putInt(PROTOCOL).putInt(custom ? 1 : 0).putInt(cap);
        for (long value : thresholds.values()) buffer.putLong(value);
        return buffer.array();
    }

    public static AstralPerkCurve decode(byte[] payload) {
        if (payload.length < 12 || payload.length > 812) throw new IllegalArgumentException("Invalid Astral curve packet length");
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int version = buffer.getInt(), mode = buffer.getInt(), cap = buffer.getInt();
        if (version != PROTOCOL || (mode != 0 && mode != 1) || cap < 1 || cap > 100 || buffer.remaining() != cap * 8) {
            throw new IllegalArgumentException("Invalid or unsupported Astral curve packet");
        }
        long[] values = new long[cap];
        for (int i = 0; i < cap; i++) values[i] = buffer.getLong();
        return new AstralPerkCurve(mode == 1, values);
    }
}
