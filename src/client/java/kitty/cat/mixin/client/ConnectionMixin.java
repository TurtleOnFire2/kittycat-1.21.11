package kitty.cat.mixin.client;

import io.netty.channel.ChannelHandlerContext;
import kitty.cat.features.dungeons.AutoLB;
import kitty.cat.features.dungeons.Storm;
import kitty.cat.features.huds.SupplyHud;
import kitty.cat.features.kuudra.PearlWaypoints;
import kitty.cat.features.misc.FarmHelper;
import kitty.cat.utils.Schedule;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Unique
    private void handlePacket(Packet<?> packet) {
        if (packet instanceof ClientboundPingPacket common) {
            if (common.getId() == 0) return;
            AutoLB.INSTANCE.serverTick();
            Storm.INSTANCE.serverTick();
            Schedule.INSTANCE.tickServer();
            PearlWaypoints.INSTANCE.serverTick();
            SupplyHud.INSTANCE.serverTick();
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void channelRead0(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        handlePacket(packet);
    }
}
