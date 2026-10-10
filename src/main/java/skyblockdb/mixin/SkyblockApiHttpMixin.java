package skyblockdb.mixin;

import kotlin.jvm.functions.Function1;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import skyblockdb.SkyblockApiGuard;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Every web request SkyblockAPI makes goes through its private {@code Http.connect}. Requests this mod doesn't
 * need fail before anything is sent (see {@link SkyblockApiGuard}); SkyblockAPI catches the failure and keeps
 * its old (empty) data.
 */
@Mixin(targets = "tech.thatgravyboat.skyblockapi.utils.http.Http", remap = false)
public class SkyblockApiHttpMixin {
    // require = 0: if SkyblockAPI renames this, nothing crashes; requests are then just not blocked.
    @Inject(method = "connect", at = @At("HEAD"), require = 0)
    private void skyblockDb$blockUnused(String url, int timeout, Map<String, ?> query,
                                        Map<String, String> headers, Function1<?, ?> builder,
                                        CallbackInfoReturnable<HttpResponse<InputStream>> cir) {
        if (SkyblockApiGuard.blocks(url)) {
            throw new IllegalStateException("Not requested: " + url + " (blocked by SkyblockDB)");
        }
    }
}
