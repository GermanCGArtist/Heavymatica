package dev.heavymatica.manhattan;

import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.core.RegionRole;
import dev.heavymatica.manhattan.ManhattanBridge.SendResult;
import dev.heavymatica.place.HeavymaticPlacement;

import java.util.ArrayList;
import java.util.List;

/**
 * The decision logic of "Send to Manhattan", without screens or Minecraft: which regions, the box limits, and what to
 * do with each result. The UI and the bridge are injected, so it is unit-tested.
 */
final class SendRunner {
    static final int MAX_BOXES = 4096;
    static final int CONFIRM_ABOVE = 64;

    record RegionPlan(String regionName, String areaName, RegionRole role, List<Box3i> boxes) {}

    interface Ui {
        void chat(String message);

        /** Asks a yes/no question; exactly one of the callbacks runs later (possibly after a screen round trip). */
        void confirm(String message, Runnable yes, Runnable no);
    }

    interface Putter {
        SendResult put(String areaName, List<Box3i> boxes, RegionRole role, boolean replaceScriptOwned);
    }

    private final String label;
    private final List<RegionPlan> plan;
    private final Putter putter;
    private final Ui ui;
    private final Runnable onAllSent;

    SendRunner(String label, List<RegionPlan> plan, Putter putter, Ui ui, Runnable onAllSent) {
        this.label = label;
        this.plan = plan;
        this.putter = putter;
        this.ui = ui;
        this.onAllSent = onAllSent;
    }

    /** All regions of the placement (visible or not), one Manhattan area each. */
    static List<RegionPlan> plan(HeavymaticPlacement p) {
        List<RegionPlan> out = new ArrayList<>();
        for (HeavymaticRegion r : p.spec().regions()) {
            out.add(new RegionPlan(r.name(), p.manhattanAreaName(r), r.role(), p.worldBoxes(r.name())));
        }
        return out;
    }

    static int totalBoxes(List<RegionPlan> plan) {
        int n = 0;
        for (RegionPlan r : plan) n += r.boxes().size();
        return n;
    }

    /** Error text when a region is over Manhattan's limit, else null. */
    static String limitError(List<RegionPlan> plan) {
        for (RegionPlan r : plan) {
            if (r.boxes().size() > MAX_BOXES) {
                return "Region \"" + r.regionName() + "\" has " + r.boxes().size()
                        + " boxes; Manhattan accepts at most " + MAX_BOXES + " per area. Nothing was sent.";
            }
        }
        return null;
    }

    static boolean needsSizeConfirm(List<RegionPlan> plan) {
        return totalBoxes(plan) > CONFIRM_ABOVE;
    }

    void start() {
        String err = limitError(plan);
        if (err != null) {
            ui.chat(err);
            return;
        }
        if (needsSizeConfirm(plan)) {
            ui.confirm(totalBoxes(plan) + " boxes: Manhattan outlines and scripts with this many boxes are heavy. Send anyway?",
                    () -> step(0, 0), () -> { });
        } else {
            step(0, 0);
        }
    }

    private void step(int index, int skipped) {
        for (int i = index; i < plan.size(); i++) {
            RegionPlan r = plan.get(i);
            SendResult res = putter.put(r.areaName(), r.boxes(), r.role(), false);
            switch (res) {
                case OK -> { }
                case SCRIPT_OWNED -> {
                    int next = i;
                    ui.confirm("Manhattan's area " + r.areaName() + " was written by a script. Replace it?",
                            () -> replace(next, skipped), () -> step(next + 1, skipped + 1));
                    return;
                }
                default -> {
                    ui.chat(failure(r.areaName(), res));
                    return;
                }
            }
        }
        finish(skipped);
    }

    private void replace(int i, int skipped) {
        RegionPlan r = plan.get(i);
        SendResult res = putter.put(r.areaName(), r.boxes(), r.role(), true);
        if (res == SendResult.OK) {
            step(i + 1, skipped);
        } else {
            ui.chat(failure(r.areaName(), res));
        }
    }

    private void finish(int skipped) {
        if (skipped > 0) {
            ui.chat(skipped + " of " + plan.size() + " areas of " + label + " were skipped; not marked as sent.");
            return;
        }
        onAllSent.run();
        ui.chat("Sent " + label + " (" + totalBoxes(plan) + " boxes) to Manhattan.");
    }

    static String failure(String areaName, SendResult res) {
        return switch (res) {
            case IN_USE -> areaName + " is in use by a running script; stop it first.";
            case INVALID -> "Manhattan rejected " + areaName + " (invalid name, no boxes, or outside the world's height).";
            case NOT_IN_WORLD -> "Join a world before sending " + areaName + " to Manhattan.";
            case UNAVAILABLE -> "Manhattan is not available; " + areaName + " was not sent.";
            default -> areaName + " was not sent (" + res + ").";
        };
    }
}
