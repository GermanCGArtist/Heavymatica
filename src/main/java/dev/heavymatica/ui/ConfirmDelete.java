package dev.heavymatica.ui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import net.minecraft.client.Minecraft;

/**
 * Remove / Delete buttons: a plain click opens a confirm dialog, Shift + click acts at once.
 */
final class ConfirmDelete {
    private ConfirmDelete() {}

    /**
     * Runs {@code action} now when Shift is held, else after the user confirms. The dialog's parent is the current
     * screen; GuiConfirmAction reopens it after the listener returns, and the list refreshes on its own tick.
     */
    static void run(String title, String message, Runnable action) {
        if (GuiBase.isShiftDown()) {
            action.run();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        IConfirmationListener listener = new IConfirmationListener() {
            @Override
            public boolean onActionConfirmed() {
                action.run();
                return true;
            }

            @Override
            public boolean onActionCancelled() {
                return true;
            }
        };
        GuiBase.openGui(new GuiConfirmAction(400, title, listener, mc.screen, "%s", message));
    }
}
