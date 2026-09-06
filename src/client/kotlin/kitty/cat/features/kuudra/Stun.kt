package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.KuudraUtils.build
import kitty.cat.utils.KuudraUtils.stun
import kitty.cat.utils.Schedule.schedule
import kitty.cat.utils.aabb
import kitty.cat.utils.hotbarSlotFromID
import kitty.cat.utils.lore
import kitty.cat.utils.renderPos
import kitty.cat.utils.uuid
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.awt.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

object Stun : Feature("Stun", "", Categories.Category.KUUDRA) {
    val autoOpenShop = booleanSetting("Auto open shop", false)
    val autoSetCursor = booleanSetting("Auto set cursor on shop open", false)
    val autoCloseShop = booleanSetting("Auto close shop", false)
    val noBlind = booleanSetting("No blindness", false)
    val stunWaypoint = booleanSetting("Stun waypoint", false)
    val pod = selectorSetting("Pod", listOf("Left", "Back", "Right"), listOf("Back"), false)
    val aimAssist = booleanSetting("Aim assist", false)
    val aimAssistFov = numberSetting("Aim assist FOV", 5.0, 180.0, 20.0, "°", 1.0)
    val aimAssistStrength = numberSetting("Aim assist strength", 0.01, 1.0, 0.5, "", 0.005)
    val autoPickobulus = booleanSetting("Auto pickobulus", false)

    var purchased = false
    private var podDestroyed = false

    fun register() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { minecraft, level ->
            purchased = false
            podDestroyed = false
        }
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (mc.level == null || mc.player == null || !enabled) return@register

            if (!stun() && !build()) return@register

            if (autoOpenShop.value) {
                ctx.renderBoxBounds(-75.0, 79.0, -106.0, -70.0, 79.05, -101.0, Color.CYAN)
            }

            if (stunWaypoint.value && !podDestroyed) {
                val pos = mc.player!!.renderPos.add(getOffset())
                ctx.renderBoxBounds(pos.aabb(1.0), Color.CYAN, phase = true)
            }
        }
    }

    private fun getOffset(): Vec3 {
        return when (pod.selected.first()) {
            "Left" -> Vec3(8.5, -21.5, 13.5)
            "Back" -> Vec3(5.5, -20.5, 29.5)
            else -> Vec3(-6.5, -21.5, 18.5)
        }
    }

    fun onTurn(accumulatedDX: Double, accumulatedDY: Double): DoubleArray? {
        val player = mc.player ?: return null
        if (!enabled || !stun() || podDestroyed || !stunWaypoint.value || !aimAssist.value) return null
        if (abs(accumulatedDX) < 0.001 && abs(accumulatedDY) < 0.001) return null

        val target = player.position().add(getOffset())
        val delta = target.subtract(player.eyePosition)
        val horizontalDistance = sqrt(delta.x * delta.x + delta.z * delta.z)
        val targetYaw = Math.toDegrees(atan2(-delta.x, delta.z)).toFloat()
        val targetPitch = Math.toDegrees(atan2(-delta.y, horizontalDistance)).toFloat()
        val yawDifference = angleDifference(targetYaw, player.yRot)
        val pitchDifference = targetPitch - player.xRot
        val halfFov = aimAssistFov.value / 2.0
        if (abs(yawDifference) > halfFov || abs(pitchDifference) > halfFov) return null

        val scale = rotationGcd() / 0.15
        val neededX = yawDifference / scale
        val neededY = pitchDifference / scale
        val neededMagnitude = sqrt(neededX * neededX + neededY * neededY)
        if (neededMagnitude < 1e-6) return null

        val userMagnitude = sqrt(accumulatedDX * accumulatedDX + accumulatedDY * accumulatedDY)
        val strength = aimAssistStrength.value
        val pull = (userMagnitude * strength).coerceAtMost(neededMagnitude)
        val assistX = neededX / neededMagnitude * pull
        val assistY = neededY / neededMagnitude * pull

        return doubleArrayOf(
            accumulatedDX * (1.0 - strength) + assistX,
            accumulatedDY * (1.0 - strength) + assistY,
        )
    }

    private fun angleDifference(target: Float, current: Float): Float {
        var difference = (target - current) % 360f
        if (difference > 180f) difference -= 360f
        if (difference < -180f) difference += 360f
        return difference
    }

    private fun rotationGcd(): Double {
        val sensitivity = mc.options.sensitivity().get()
        val base = sensitivity * 0.6 + 0.2
        return (base * base * base * 8.0 * 0.15).coerceAtLeast(0.0001)
    }

    fun handleChat(unformatted: String) {
        if (unformatted.endsWith(" destroyed one of Kuudra's pods!")) {
            podDestroyed = true
        }

        if (unformatted == "You purchased Human Cannonball!") {
            purchased = true
            if (mc.player?.containerMenu != null && autoCloseShop.value) {
                mc.player!!.closeContainer()
            }
            schedule(40) {
                purchased = false
            }
        }
    }

    fun useItem(player: Player, interactionHand: InteractionHand, result: InteractionResult) {
        if (!autoOpenShop.value || !build() && !stun()) return

        if (!player.isCrouching) return
        if (player.mainHandItem.uuid() !in listOf("ETHERWARP_CONDUIT", "ASPECT_OF_THE_VOID")) return

        val start = player.eyePosition
        val end = start.add(player.lookAngle.scale(50.0))

        val pos = mc.level?.clip(
            ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
                )
        )?.blockPos ?: return

        if (pos.x in -75..-70 && pos.y == 78 && pos.z in -106..-101) {
            val slot = hotbarSlotFromID("KUUDRA_SHOP_ITEM") ?: return
            player.inventory.selectedSlot = slot
            schedule(1) {
                mc.options.keyUse.clickCount++
            }
        }
    }

    fun onPositionChange(packet: ClientboundPlayerPositionPacket) {
        if (!enabled) return

        if (!stun()) return

        val pos = packet.change.position

        RendMacro.dM(pos.toString())

        if (autoPickobulus.value && !podDestroyed) {
            if (!(pos.x in -171.0..-148.0 && pos.y in 26.0..31.0 && pos.z in -174.0..-151.0)) return

            RendMacro.dM("Searching for picko")

            var slot: Int? = null

            for (i in 0..7) {
                val lore = mc.player!!.inventory.getItem(i).lore

                lore.forEach {
                    if (it.string.contains("Ability: Pickobulus")) {
                        slot = i
                    }
                }
            }

            slot ?: return

            if (mc.player?.inventory?.selectedSlot == slot) {
                mc.options.keyUse.clickCount++
                return
            }

            mc.player?.inventory?.selectedSlot = slot
            schedule(1) {
                mc.options.keyUse.clickCount++
            }
        }
    }

    fun openScreen(packet: ClientboundOpenScreenPacket): Boolean {
        if (!enabled) return false

        if (!stun() && !build()) return false

        if (!purchased) return false

        return autoCloseShop.value
    }

    fun handleSetSlot(packet: ClientboundContainerSetSlotPacket) {
        if (packet.item.hoverName.string != "Human Cannonball" || !autoSetCursor.value) return

        val screen = mc.screen as? AbstractContainerScreen<*> ?: return
        if (packet.containerId != screen.menu.containerId) return
        if (packet.slot !in screen.menu.slots.indices) return

        val slot = screen.menu.getSlot(packet.slot)

        val relativeX = slot.x
        val relativeY = slot.y

        val guiX = screen.leftPos + relativeX + 8.0
        val guiY = screen.topPos + relativeY + 8.0

        val window = mc.window
        val windowX = guiX * window.screenWidth / window.guiScaledWidth
        val windowY = guiY * window.screenHeight / window.guiScaledHeight

        GLFW.glfwSetCursorPos(window.handle(), windowX, windowY)
    }
}
