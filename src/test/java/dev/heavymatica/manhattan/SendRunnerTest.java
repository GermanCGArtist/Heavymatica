package dev.heavymatica.manhattan;

import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.core.HeavymaticSpec;
import dev.heavymatica.core.RegionRole;
import dev.heavymatica.manhattan.ManhattanBridge.SendResult;
import dev.heavymatica.place.HeavymaticPlacement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SendRunnerTest {
    private static List<Box3i> boxes(int n) {
        List<Box3i> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(Box3i.of(i * 2, 0, 0, i * 2, 0, 0));
        return l;
    }

    private static HeavymaticPlacement placement(int a, int b) {
        HeavymaticSpec spec = new HeavymaticSpec("multi", "", "", List.of(
                new HeavymaticRegion("a", RegionRole.DIG, 1, boxes(a)),
                new HeavymaticRegion("b", RegionRole.PROTECT, 2, boxes(b))));
        return HeavymaticPlacement.create(spec, "multi", 0, 0, 0);
    }

    /** Scripted UI: answers confirms from a queue and records chat. */
    private static final class FakeUi implements SendRunner.Ui {
        final List<String> chat = new ArrayList<>();
        final List<String> questions = new ArrayList<>();
        final List<Boolean> answers;

        FakeUi(Boolean... answers) { this.answers = new ArrayList<>(List.of(answers)); }

        @Override public void chat(String m) { chat.add(m); }

        @Override public void confirm(String m, Runnable yes, Runnable no) {
            questions.add(m);
            (answers.remove(0) ? yes : no).run();
        }
    }

    private static final class FakePutter implements SendRunner.Putter {
        final List<String> calls = new ArrayList<>();
        final Map<String, SendResult> first;
        final Map<String, SendResult> second;

        FakePutter(Map<String, SendResult> first, Map<String, SendResult> second) { this.first = first; this.second = second; }

        @Override public SendResult put(String n, List<Box3i> b, RegionRole r, boolean replace) {
            calls.add(n + ":" + r + ":" + replace);
            return (replace ? second : first).getOrDefault(n, SendResult.OK);
        }
    }

    @Test
    void multiRegionNamesAndAllRegionsIncludingHidden() {
        HeavymaticPlacement p = placement(2, 3);
        p.setRegionVisible("b", false);
        var plan = SendRunner.plan(p);
        assertEquals(List.of("multi_a", "multi_b"), plan.stream().map(SendRunner.RegionPlan::areaName).toList());
        assertEquals(5, SendRunner.totalBoxes(plan));
    }

    @Test
    void happyPathMarksSentOnce() {
        HeavymaticPlacement p = placement(2, 3);
        FakeUi ui = new FakeUi();
        FakePutter put = new FakePutter(Map.of(), Map.of());
        new SendRunner("multi", SendRunner.plan(p), put, ui, p::markSent).start();
        assertEquals(List.of("multi_a:DIG:false", "multi_b:PROTECT:false"), put.calls);
        assertNotEquals(0, p.lastSentHash());
        assertEquals(List.of("Sent multi (5 boxes) to Manhattan."), ui.chat);
        assertTrue(ui.questions.isEmpty());
    }

    @Test
    void regionOverLimitStopsBeforeAnything() {
        HeavymaticPlacement p = placement(2, 4097);
        FakeUi ui = new FakeUi();
        FakePutter put = new FakePutter(Map.of(), Map.of());
        new SendRunner("multi", SendRunner.plan(p), put, ui, p::markSent).start();
        assertTrue(put.calls.isEmpty());
        assertEquals(0, p.lastSentHash());
        assertTrue(ui.chat.get(0).contains("\"b\""));
    }

    @Test
    void manyBoxesAsksAndNoCancels() {
        HeavymaticPlacement p = placement(40, 30);
        FakeUi ui = new FakeUi(false);
        FakePutter put = new FakePutter(Map.of(), Map.of());
        new SendRunner("multi", SendRunner.plan(p), put, ui, p::markSent).start();
        assertEquals("70 boxes: Manhattan outlines and scripts with this many boxes are heavy. Send anyway?", ui.questions.get(0));
        assertTrue(put.calls.isEmpty());
        assertEquals(0, p.lastSentHash());
    }

    @Test
    void exactly64BoxesDoesNotAsk() {
        HeavymaticPlacement p = placement(32, 32);
        FakeUi ui = new FakeUi();
        new SendRunner("multi", SendRunner.plan(p), new FakePutter(Map.of(), Map.of()), ui, p::markSent).start();
        assertTrue(ui.questions.isEmpty());
        assertNotEquals(0, p.lastSentHash());
    }

    @Test
    void manyBoxesYesThenSends() {
        HeavymaticPlacement p = placement(40, 30);
        FakeUi ui = new FakeUi(true);
        new SendRunner("multi", SendRunner.plan(p), new FakePutter(Map.of(), Map.of()), ui, p::markSent).start();
        assertNotEquals(0, p.lastSentHash());
    }

    @Test
    void scriptOwnedYesRetriesWithReplace() {
        HeavymaticPlacement p = placement(1, 1);
        FakeUi ui = new FakeUi(true);
        FakePutter put = new FakePutter(Map.of("multi_a", SendResult.SCRIPT_OWNED), Map.of());
        new SendRunner("multi", SendRunner.plan(p), put, ui, p::markSent).start();
        assertEquals("Manhattan's area multi_a was written by a script. Replace it?", ui.questions.get(0));
        assertEquals(List.of("multi_a:DIG:false", "multi_a:DIG:true", "multi_b:PROTECT:false"), put.calls);
        assertNotEquals(0, p.lastSentHash());
    }

    @Test
    void scriptOwnedNoSkipsRegionAndDoesNotMarkSent() {
        HeavymaticPlacement p = placement(1, 1);
        FakeUi ui = new FakeUi(false);
        FakePutter put = new FakePutter(Map.of("multi_a", SendResult.SCRIPT_OWNED), Map.of());
        new SendRunner("multi", SendRunner.plan(p), put, ui, p::markSent).start();
        assertEquals(List.of("multi_a:DIG:false", "multi_b:PROTECT:false"), put.calls);
        assertEquals(0, p.lastSentHash());
        assertTrue(ui.chat.get(0).contains("skipped"));
    }

    @Test
    void inUseStopsWithMessage() {
        HeavymaticPlacement p = placement(1, 1);
        FakeUi ui = new FakeUi();
        FakePutter put = new FakePutter(Map.of("multi_a", SendResult.IN_USE), Map.of());
        new SendRunner("multi", SendRunner.plan(p), put, ui, p::markSent).start();
        assertEquals(List.of("multi_a:DIG:false"), put.calls);
        assertEquals(List.of("multi_a is in use by a running script; stop it first."), ui.chat);
        assertEquals(0, p.lastSentHash());
    }

    @Test
    void otherFailuresStop() {
        for (SendResult r : new SendResult[]{SendResult.INVALID, SendResult.NOT_IN_WORLD, SendResult.UNAVAILABLE}) {
            HeavymaticPlacement p = placement(1, 1);
            FakeUi ui = new FakeUi();
            FakePutter put = new FakePutter(Map.of("multi_b", r), Map.of());
            new SendRunner("multi", SendRunner.plan(p), put, ui, p::markSent).start();
            assertEquals(1, ui.chat.size(), r.name());
            assertEquals(0, p.lastSentHash(), r.name());
        }
    }

    @Test
    void replaceFailureStops() {
        HeavymaticPlacement p = placement(1, 1);
        FakeUi ui = new FakeUi(true);
        FakePutter put = new FakePutter(Map.of("multi_a", SendResult.SCRIPT_OWNED), Map.of("multi_a", SendResult.IN_USE));
        AtomicInteger sent = new AtomicInteger();
        new SendRunner("multi", SendRunner.plan(p), put, ui, sent::incrementAndGet).start();
        assertEquals(0, sent.get());
        assertEquals(1, ui.chat.size());
    }
}
