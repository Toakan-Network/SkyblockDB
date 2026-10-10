package skyblockdb;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import skyblockdb.api.ItemCount;
import skyblockdb.api.SkyblockDbApi;
import skyblockdb.record.SkyblockIds;
import skyblockdb.store.Store;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * {@code /skyblockdb status} (what is stored), {@code /skyblockdb item <ID>} (where an item is, via the API) and
 * {@code /skyblockdb debug} (toggle diagnostic logging).
 */
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
                // Goes through the public API, like another mod would.
                .then(literal("item").then(argument("id", StringArgumentType.greedyString()).executes(context -> {
                    String id = SkyblockIds.normalize(StringArgumentType.getString(context, "id"));
                    SkyblockDbApi api = SkyblockDbApi.get().orElseThrow();
                    if (api.currentProfile().isEmpty()) {
                        context.getSource().sendFeedback(Component.literal(SkyblockDb.NAME + ": no SkyBlock profile yet").withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    String name = api.item(id).map(i -> i.name() + (i.observed() ? " (observed)" : i.retired() ? " (retired)" : "")).orElse("unknown item");
                    context.getSource().sendFeedback(Component.literal(SkyblockDb.NAME + ": " + id + " (" + name + ") x" + api.count(id)
                        + ", " + api.recipes(id).size() + " recipe(s), used in " + api.usages(id).size()
                        + ", dropped by " + api.droppedBy(id).size() + " mob(s)").withStyle(ChatFormatting.GOLD));
                    for (ItemCount row : api.counts(id)) {
                        context.getSource().sendFeedback(Component.literal("- " + row.container() + " slot " + row.slot() + ": " + row.amount()).withStyle(ChatFormatting.WHITE));
                    }
                    return 1;
                })))
                .then(literal("debug").executes(context -> {
                    SkyblockDb.debugLogging = !SkyblockDb.debugLogging;
                    context.getSource().sendFeedback(Component.literal(SkyblockDb.NAME + " debug logging "
                        + (SkyblockDb.debugLogging ? "on" : "off")).withStyle(ChatFormatting.GOLD));
                    return 1;
                }))
        ));
    }
}
