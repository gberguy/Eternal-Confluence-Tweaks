package com.gberguy.ecpatches.runtime;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class AstralPerkSession {
    private Object connection;
    private AstralPerkCurve curve;
    private boolean expected;
    private final Set<Object> retired = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    public synchronized void begin(Object connection) {
        observe(connection, true);
    }

    public synchronized void observe(Object connection, boolean supported) {
        if (connection == null) throw new IllegalArgumentException("Missing connection");
        if (retired.contains(connection)) return;
        if (this.connection != connection) {
            if (this.connection != null) retired.add(this.connection);
            this.connection = connection;
            this.curve = null;
            this.expected = supported;
        } else if (supported) {
            this.expected = true;
        }
    }

    public synchronized boolean accept(Object connection, AstralPerkCurve curve) {
        if (this.connection != connection || !expected) return false;
        if (curve == null) throw new IllegalArgumentException("Missing curve");
        if (this.curve != null) throw new IllegalArgumentException("Duplicate Astral curve packet; live changes require a restart");
        this.curve = curve;
        return true;
    }

    public synchronized void end(Object connection) {
        if (connection != null) retired.add(connection);
        if (this.connection == connection) { this.connection = null; this.curve = null; this.expected = false; }
    }

    public synchronized boolean waiting(Object connection) { return this.connection == connection && expected && curve == null; }
    public synchronized AstralPerkCurve curve() { return curve; }
}
