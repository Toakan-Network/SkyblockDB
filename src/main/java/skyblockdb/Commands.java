package skyblockdb;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import skyblockdb.api.ItemCount;
import skyblockdb.api.SkyblockDbApi;
import skyblockdb.record.SkyblockIds;
import skyblockdb.store.Store;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * {@code /skyblockdb status} (what is stored), {@code /skyblockdb item <ID>} (where an item is, via the API),
 * {@code /skyblockdb history [on|off]} (item history, off by default) and {@code /skyblockdb debug} (toggle
 * diagnostic logging).
 */
final class Commands {
    private Commands() {}

    static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            literal("skyblockdb")
                .then(literal("status").executes(context -> {
                    Store.get().query(store -> {
                            MutableComponent text = Component.translatable("skyblock-db.command.status", SkyblockDb.NAME,
                                store.profiles().size(), store.itemCounts().size(),
                                store.currencies().size(), store.history().size());
                            if (!SkyblockApiSafe.disabled().isEmpty()) {
                                text.append(Component.translatable("skyblock-db.command.status.disabled",
                                    String.join(", ", SkyblockApiSafe.disabled())));
                            }
                            return text;
                        })
                        .whenComplete((text, error) -> {
                            Minecraft client = Minecraft.getInstance();
                            Component message = error == null
                                ? text.withStyle(ChatFormatting.GOLD)
                                : Component.translatable("skyblock-db.command.status.closed", SkyblockDb.NAME).withStyle(ChatFormatting.RED);
                            client.execute(() -> client.gui.hud.getChat().addClientSystemMessage(message));
                        });
                    return 1;
                }))
                // Goes through the public API, like another mod would.
                .then(literal("item").then(argument("id", StringArgumentType.greedyString()).executes(context -> {
                    String id = SkyblockIds.normalize(StringArgumentType.getString(context, "id"));
                    SkyblockDbApi api = SkyblockDbApi.get().orElseThrow();
                    if (api.currentProfile().isEmpty()) {
                        context.getSource().sendFeedback(Component.translatable("skyblock-db.command.no_profile", SkyblockDb.NAME).withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    Component name = api.item(id)
                        .<Component>map(i -> i.observed() ? Component.translatable("skyblock-db.command.item.observed", i.name())
                            : i.retired() ? Component.translatable("skyblock-db.command.item.retired", i.name())
                            : Component.literal(i.name()))
                        .orElseGet(() -> Component.translatable("skyblock-db.command.item.unknown"));
                    context.getSource().sendFeedback(Component.translatable("skyblock-db.command.item", SkyblockDb.NAME, id, name,
                        api.count(id), api.recipes(id).size(), api.usages(id).size(), api.droppedBy(id).size())
                        .withStyle(ChatFormatting.GOLD));
                    for (ItemCount row : api.counts(id)) {
                        context.getSource().sendFeedback(Component.translatable("skyblock-db.command.item.row", row.container(),
                            row.slot(), row.amount()).withStyle(ChatFormatting.WHITE));
                    }
                    return 1;
                })))
                .then(literal("history")
                    .executes(context -> {
                        String state = Store.get().historySwitchedOn() ? "skyblock-db.command.history.on"
                            : SkyblockDb.debugLogging ? "skyblock-db.command.history.debug"
                            : "skyblock-db.command.history.off";
                        context.getSource().sendFeedback(Component.translatable(state, SkyblockDb.NAME).withStyle(ChatFormatting.GOLD));
                        return 1;
                    })
                    .then(literal("on").executes(context -> {
                        Store.get().setRecordHistory(true);
                        context.getSource().sendFeedback(Component.translatable("skyblock-db.command.history.switched_on", SkyblockDb.NAME).withStyle(ChatFormatting.GOLD));
                        return 1;
                    }))
                    .then(literal("off").executes(context -> {
                        Store.get().setRecordHistory(false);
                        context.getSource().sendFeedback(Component.translatable("skyblock-db.command.history.switched_off", SkyblockDb.NAME).withStyle(ChatFormatting.GOLD));
                        return 1;
                    })))
                .then(literal("debug").executes(context -> {
                    SkyblockDb.debugLogging = !SkyblockDb.debugLogging;
                    context.getSource().sendFeedback(Component.translatable(SkyblockDb.debugLogging
                        ? "skyblock-db.command.debug.on" : "skyblock-db.command.debug.off", SkyblockDb.NAME).withStyle(ChatFormatting.GOLD));
                    return 1;
                }))
        ));
    }
}
