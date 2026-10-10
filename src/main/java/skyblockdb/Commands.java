package skyblockdb;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import skyblockdb.store.Store;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** {@code /skyblockdb status} (what is stored) and {@code /skyblockdb debug} (toggle diagnostic logging). */
final class Commands {
    private Commands() {}

    static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            literal("skyblockdb")
                .then(literal("status").executes(context -> {
                    Store.get().query(store -> String.format(
                            "%d profile(s), %d item rows, %d currency values, %d history entries",
                            store.profiles().size(), store.itemCounts().size(),
                            store.currencies().size(), store.history().size()))
                        .whenComplete((text, error) -> {
                            Minecraft client = Minecraft.getInstance();
                            Component message = error == null
                                ? Component.literal(SkyblockDb.NAME + ": " + text).withStyle(ChatFormatting.GOLD)
                                : Component.literal(SkyblockDb.NAME + ": store not open (see latest.log)").withStyle(ChatFormatting.RED);
                            client.execute(() -> client.gui.hud.getChat().addClientSystemMessage(message));
                        });
                    return 1;
                }))
                .then(literal("debug").executes(context -> {
                    SkyblockDb.debugLogging = !SkyblockDb.debugLogging;
                    context.getSource().sendFeedback(Component.literal(SkyblockDb.NAME + " debug logging "
                        + (SkyblockDb.debugLogging ? "on" : "off")).withStyle(ChatFormatting.GOLD));
                    return 1;
                }))
        ));
    }
}
