package skyblockdb.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import skyblockdb.SkyblockApiGuard;

/**
 * SkyblockAPI asks Hypixel for the player's party (through the Hypixel Mod API) when party chat doesn't match
 * what it knows. This mod sends nothing to the server, so the request is answered "not sent" while
 * {@link SkyblockApiGuard} is active.
 */
@Mixin(targets = "tech.thatgravyboat.skyblockapi.impl.events.HypixelEventHandler", remap = false)
public class SkyblockApiPartyMixin {
    // require = 0: if SkyblockAPI renames this, nothing crashes; the request is then just not blocked.
    @Inject(method = "requestPartyInfo$tech_thatgravyboat_skyblock_api", at = @At("HEAD"), cancellable = true, require = 0)
    private void skyblockDb$noPartyRequest(CallbackInfoReturnable<Boolean> cir) {
        if (SkyblockApiGuard.active()) cir.setReturnValue(false);
    }
}
