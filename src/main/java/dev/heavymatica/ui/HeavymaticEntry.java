package dev.heavymatica.ui;

import dev.heavymatica.place.HeavymaticPlacement;
import dev.vitruvius.api.ListEntry;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One placement as a row of Litematica's placement list. Two entries are equal when they show the same placement. */
public final class HeavymaticEntry implements ListEntry {
    private final HeavymaticPlacement placement;

    public HeavymaticEntry(HeavymaticPlacement placement) {
        this.placement = placement;
    }

    public HeavymaticPlacement placement() {
        return placement;
    }

    @Override
    public String id() {
        return placement.entryId();
    }

    /** The preset name, plus the Manhattan name when it differs. */
    @Override
    public String name() {
        String n = placement.presetName();
        if (!placement.manhattanName().equals(n)) n += " (" + placement.manhattanName() + ")";
        return n;
    }

    @Override
    public List<String> searchStrings() {
        List<String> out = new ArrayList<>();
        out.add(name().toLowerCase(Locale.ROOT));
        out.add("heavymatic");
        for (var r : placement.spec().regions()) {
            out.add(r.name().toLowerCase(Locale.ROOT));
            out.add(r.role().name().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    @Override
    public WidgetListEntryBase<?> createWidget(int x, int y, int width, int height, boolean isOdd, int listIndex) {
        return new WidgetHeavymaticRow(x, y, width, height, isOdd, this, listIndex);
    }

    @Override
    public boolean isRenderingEnabled() {
        return placement.enabled();
    }

    @Override
    public void setRenderingEnabled(boolean enabled) {
        if (placement.enabled() != enabled) placement.setEnabled(enabled);
    }

    @Override
    public String cutawayLabel() {
        return placement.cutaway().name();
    }

    @Override
    public void cycleCutaway() {
        placement.setCutaway(WidgetHeavymaticRow.nextCutaway(placement.cutaway()));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HeavymaticEntry e && e.placement.id().equals(placement.id());
    }

    @Override
    public int hashCode() {
        return placement.id().hashCode();
    }
}
