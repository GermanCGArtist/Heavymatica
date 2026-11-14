package dev.heavymatica.manhattan;

import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.place.HeavymaticPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * "Send to Manhattan" for one placement, with its confirm dialogs (many boxes, replacing a script's area) and
 * messages. Client thread only. Decisions live in {@link SendRunner}.
 */
public final class SendFlow {
    private SendFlow() {}

    private static final SendRunner.Ui UI = new SendRunner.Ui() {
        @Override
        public void chat(String message) {
            SendFlow.chat(message);
        }

        @Override
        public void confirm(String message, Runnable yes, Runnable no) {
            Minecraft mc = Minecraft.getInstance();
            // GuiConfirmAction reopens its parent screen after the listener returns, so the follow-up (which may open
            // another dialog) must run later, not inside the listener.
            IConfirmationListener listener = new IConfirmationListener() {
                @Override
                public boolean onActionConfirmed() {
                    mc.execute(yes);
                    return true;
                }

                @Override
                public boolean onActionCancelled() {
                    mc.execute(no);
                    return true;
                }
            };
            GuiConfirmAction gui = new GuiConfirmAction(400, "Send to Manhattan", listener, mc.screen, "%s", message);
            GuiBase.openGui(gui);
        }
    };

    private static void chat(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[Heavymatica] " + message));
    }

    /** Starts sending; may open confirm screens and returns at once. */
    public static void send(HeavymaticPlacement placement) {
        if (!ManhattanBridge.available()) {
            chat("Manhattan is not installed.");
            return;
        }
        new SendRunner(placement.manhattanName(), SendRunner.plan(placement), ManhattanBridge::put, UI, placement::markSent).start();
    }

    /** Removes the placement's areas from Manhattan (only ones Heavymatica owns). */
    public static void unsend(HeavymaticPlacement placement) {
        if (!ManhattanBridge.available()) {
            chat("Manhattan is not installed.");
            return;
        }
        int removed = 0;
        int total = 0;
        for (HeavymaticRegion r : placement.spec().regions()) {
            total++;
            if (ManhattanBridge.remove(placement.manhattanAreaName(r))) removed++;
        }
        chat("Removed " + removed + " of " + total + " areas of " + placement.manhattanName()
                + " from Manhattan" + (removed < total ? " (the rest were missing, in use, or not owned by Heavymatica)." : "."));
        // HeavymaticPlacement has no way to reset lastSentHash; flag the row so it no longer claims to be in sync.
        if (removed > 0 && placement.lastSentHash() != 0) placement.markOutOfSync();
    }
}
