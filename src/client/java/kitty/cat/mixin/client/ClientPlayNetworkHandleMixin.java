package kitty.cat.mixin.client;

import kitty.cat.features.dungeons.AutoLB;
import kitty.cat.features.dungeons.Relics;
import kitty.cat.features.dungeons.Storm;
import kitty.cat.features.huds.BestiaryHud;
import kitty.cat.features.huds.SupplyHud;
import kitty.cat.features.kuudra.*;
import kitty.cat.features.misc.ChatMacros;
import kitty.cat.features.misc.FarmHelper;
import kitty.cat.features.misc.Pests;
import kitty.cat.features.visual.ArrowTracers;
import kitty.cat.utils.KuudraUtils;
import kitty.cat.utils.LocationUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandleMixin {
    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    void handleAddEntity(ClientboundAddEntityPacket clientboundAddEntityPacket, CallbackInfo ci) {
        ArrowTracers.INSTANCE.handleAddEntity(clientboundAddEntityPacket);
    }

    @Inject(method = "handleRemoveEntities", at = @At("TAIL"))
    void handleRemoveEntities(ClientboundRemoveEntitiesPacket clientboundRemoveEntitiesPacket, CallbackInfo ci) {
        ArrowTracers.INSTANCE.handleRemoveEntities(clientboundRemoveEntitiesPacket);
    }

    @Inject(method = "handlePlayerInfoUpdate", at = @At("TAIL"))
    void handleInfoUpdate(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        BestiaryHud.INSTANCE.handleTabChange(packet);
    }

    @Inject(method = "handleSystemChat(Lnet/minecraft/network/protocol/game/ClientboundSystemChatPacket;)V", at = @At("HEAD"))
    void handleSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().packetProcessor().isSameThread()) return;

        var component = packet.content();
        var message = component.getString();
        var unformatted = ChatFormatting.stripFormatting(message);

        AutoLB.INSTANCE.handleChat(unformatted);
        ChatMacros.INSTANCE.handleChat(unformatted);
        Storm.INSTANCE.handleChat(unformatted);
        Relics.INSTANCE.handleChat(unformatted);
        LocationUtils.INSTANCE.handleChat(unformatted);
        KuudraUtils.INSTANCE.handleChat(unformatted);
        AutoGFS.INSTANCE.handleChat(unformatted);
        Stun.INSTANCE.handleChat(unformatted);
        FarmHelper.INSTANCE.handleChat(unformatted);
    }

    @Inject(method = "handleOpenScreen(Lnet/minecraft/network/protocol/game/ClientboundOpenScreenPacket;)V", at = @At("HEAD"), cancellable = true)
    void handleOpenScreen(ClientboundOpenScreenPacket clientboundOpenScreenPacket, CallbackInfo ci) {
        Storm.INSTANCE.handleScreen(clientboundOpenScreenPacket);
        RendMacro.INSTANCE.openScreen(clientboundOpenScreenPacket);
        FarmHelper.INSTANCE.openScreen(clientboundOpenScreenPacket);

        var connection = Minecraft.getInstance().getConnection();
        if (Stun.INSTANCE.openScreen(clientboundOpenScreenPacket)) {
            connection.send(new ServerboundContainerClosePacket(clientboundOpenScreenPacket.getContainerId()));
            ci.cancel();
        };
    }

    @Inject(method = "handleContainerSetSlot", at = @At("HEAD"))
    void handleContainerSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        Stun.INSTANCE.handleSetSlot(packet);
    }

    @Inject(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V", at = @At("TAIL"))
    void handleMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        RendMacro.INSTANCE.onPositionChange(packet);
        Supplies.INSTANCE.onPositionChange(packet);
        Stun.INSTANCE.onPositionChange(packet);
    }

    @Inject(method = "setTitleText", at = @At("HEAD"), cancellable = true)
    void handleSetTitleText(ClientboundSetTitleTextPacket clientboundSetTitleTextPacket, CallbackInfo ci) {
        PearlWaypoints.INSTANCE.handleTitle(clientboundSetTitleTextPacket);
        if(SupplyHud.INSTANCE.handleTitle(clientboundSetTitleTextPacket)) {
            ci.cancel();
        };
    }

    @Inject(method = "handleSetEntityData", at = @At("HEAD"))
    void handleSetEntityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        RendDamage.INSTANCE.handleSetEntityData(packet);
    }
}
