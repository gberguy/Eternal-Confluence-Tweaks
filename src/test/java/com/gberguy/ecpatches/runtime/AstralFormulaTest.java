package com.gberguy.ecpatches.runtime;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.gberguy.ecpatches.core.PatchId;
import com.gberguy.ecpatches.core.PatchSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

public class AstralFormulaTest {
    @TempDir Path directory;

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "prev + 1000 * i * i;4007", "prev*1000+i;7002", "(prev+1)*(i+2);32", "i/4;0.5",
        "2^3^2;512", "-2^2;-4", "(-2)^2;4", "2^-2;0.25", "--i;2",
        "floor(2^(i/2+3));16", "ceil(2.01);3", "sqrt(81);9", "min(3, max(i, prev));3",
        "1e3 + 2.5E-1;1000.25", " .5 * i ;1"
    })
    void parsesDocumentedArithmetic(String expression, double expected) {
        assertEquals(expected, AstralFormula.compile(expression).evaluate(2, 7), 1e-10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "previous+1", "sin(i)", "2i", "i prev", "2**i", "floor(1,2)", "max(1)",
            "min(1,2,3)", "1..2", "1e", "(i+1", "i+1)", "i=2", "NaN", "Infinity", "I+1", "1e999", "i;1"})
    void rejectsUnsupportedOrMalformedInput(String expression) {
        assertThrows(IllegalArgumentException.class, () -> AstralFormula.compile(expression));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1/0", "0/0", "sqrt(-1)", "10^1000", "(-1)^0.5", "min(1,1/0)", "(10^1000)*0"})
    void rejectsInvalidIntermediateArithmetic(String expression) {
        assertThrows(IllegalArgumentException.class, () -> AstralFormula.compile(expression).evaluate(1, 0));
    }

    @Test
    void buildsEntireQuadraticCurveWithFlooredPreviousThreshold() {
        AstralPerkCurve curve = AstralPerkCurve.calculate(AstralFormula.DEFAULT, 100);
        assertEquals(1000L, curve.thresholds.get(1).longValue());
        assertEquals(5000L, curve.thresholds.get(2).longValue());
        assertEquals(14000L, curve.thresholds.get(3).longValue());
        assertEquals(30000L, curve.thresholds.get(4).longValue());
        assertEquals(1015000L, curve.thresholds.get(14).longValue());
        assertEquals(338350000L, curve.thresholds.get(100).longValue());
        AstralPerkCurve fractional = AstralPerkCurve.calculate("prev + 100.9", 3);
        assertEquals(100L, fractional.thresholds.get(1).longValue());
        assertEquals(200L, fractional.thresholds.get(2).longValue());
        assertThrows(UnsupportedOperationException.class, () -> curve.thresholds.put(1, 0L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-i", "100", "1000-i", "prev", "prev+0.5", "1e19+i", "prev+1"})
    void rejectsUnusableCurves(String expression) {
        assertThrows(IllegalArgumentException.class, () -> AstralPerkCurve.calculate(expression, 100));
    }

    @Test
    void validatesLateFailuresAndResourceLimits() {
        String failure = assertThrows(IllegalArgumentException.class,
                () -> AstralPerkCurve.calculate("prev + 1000/(50-i)", 100)).getMessage();
        assertTrue(failure.contains("i=50"));
        assertThrows(IllegalArgumentException.class, () -> AstralFormula.compile(String.join("", java.util.Collections.nCopies(70, "(")) + "i"));
        assertThrows(IllegalArgumentException.class, () -> AstralFormula.compile(String.join("+", java.util.Collections.nCopies(600, "i"))));
        assertThrows(IllegalArgumentException.class, () -> AstralFormula.compile(String.join("", java.util.Collections.nCopies(2049, " "))));
        assertThrows(IllegalArgumentException.class, () -> AstralPerkCurve.calculate("i", 101));
    }

    @Test
    void packetRoundTripsExactThresholdsAndRejectsCorruption() {
        AstralPerkCurve curve = AstralPerkCurve.calculate("prev + 1000 * sqrt(i)", 100);
        AstralPerkCurve decoded = AstralPerkCurve.decode(curve.encode());
        assertTrue(decoded.custom);
        assertEquals(curve.cap, decoded.cap);
        assertEquals(curve.thresholds, decoded.thresholds);
        assertFalse(AstralPerkCurve.decode(new AstralPerkCurve(false, new long[]{158, 324}).encode()).custom);
        for (int offset : new int[]{0, 4, 8}) {
            byte[] bad = curve.encode();
            ByteBuffer.wrap(bad).putInt(offset, -1);
            assertThrows(IllegalArgumentException.class, () -> AstralPerkCurve.decode(bad));
        }
        byte[] bad = curve.encode();
        ByteBuffer.wrap(bad).putLong(12, 0);
        assertThrows(IllegalArgumentException.class, () -> AstralPerkCurve.decode(bad));
        assertThrows(IllegalArgumentException.class, () -> AstralPerkCurve.decode(new byte[10000]));
        assertThrows(IllegalArgumentException.class, () -> AstralPerkCurve.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> AstralPerkCurve.decode(java.util.Arrays.copyOf(curve.encode(), 40)));
    }

    @Test
    void serverSwitchAndDelayedDisconnectCannotReuseOldRequirements() {
        AstralPerkSession state = new AstralPerkSession();
        Object first = new Object(), second = new Object();
        AstralPerkCurve curve = AstralPerkCurve.calculate(AstralFormula.DEFAULT, 100);
        state.begin(first);
        assertTrue(state.waiting(first));
        assertTrue(state.accept(first, curve));
        state.begin(first);
        assertSame(curve, state.curve());
        assertThrows(IllegalArgumentException.class, () -> state.accept(first, curve));
        state.begin(second);
        assertNull(state.curve());
        assertFalse(state.accept(first, curve));
        state.observe(first, true);
        state.end(first);
        assertTrue(state.waiting(second));
        assertTrue(state.accept(second, curve));
        state.end(second);
        assertNull(state.curve());
        assertFalse(state.accept(second, curve));
        Object ordinary = new Object();
        state.observe(ordinary, false);
        assertFalse(state.waiting(ordinary));
        assertFalse(state.accept(ordinary, curve));
        state.observe(ordinary, true);
        assertTrue(state.waiting(ordinary));
        assertTrue(state.accept(ordinary, curve));
        state.observe(ordinary, false);
        assertSame(curve, state.curve());
    }

    @Test
    void configPreservesFormulaAndIndependentToggleIdempotently() throws Exception {
        Path file = directory.resolve("config.cfg");
        String formula = "prev+max(1000, floor(2^(i/2+3)))";
        Files.write(file, ("astralPerkLevelFormulaEnabled=false\nastralPerkLevelFormula=" + formula + "\n# My note\n").getBytes(StandardCharsets.UTF_8));
        PatchSettings settings = PatchSettings.read(file.toFile());
        assertFalse(settings.enabled(PatchId.ASTRAL_PERK_FORMULA));
        assertTrue(settings.enabled(PatchId.ROOTS));
        assertEquals(formula, settings.astralPerkLevelFormula());
        byte[] rendered = Files.readAllBytes(file);
        assertTrue(new String(rendered, StandardCharsets.UTF_8).contains("i begins at 1; prev is 0"));
        assertTrue(new String(rendered, StandardCharsets.UTF_8).contains("# My note"));
        PatchSettings.read(file.toFile());
        assertArrayEquals(rendered, Files.readAllBytes(file));
    }
}
