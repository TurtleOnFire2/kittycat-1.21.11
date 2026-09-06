package kitty.cat.features.dungeons

import kitty.cat.KittycatClient.mc
import kitty.cat.gui.categories.Categories
import kitty.cat.features.Feature
import kitty.cat.utils.Chat
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.Schedule.schedule
import kitty.cat.utils.aabb
import kitty.cat.utils.clickSlot
import kitty.cat.utils.getLoadoutIndex
import kitty.cat.utils.getLook
import kitty.cat.utils.normalizeYaw
import kitty.cat.utils.renderPos
import kitty.cat.utils.rotate
import kitty.cat.utils.uuid
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.abs

object Storm: Feature("Storm", "Stuff for Storm Phase", Categories.Category.DUNGEONS) {
    //Arch
    val bowTint = booleanSetting("Apply tint at max pull", false, description = "Applies a red tint when the Death Bow is at max charge")
    val autoSwapCritItem = booleanSetting("Auto swap crit item", description = "Automatically swaps to the selected slot after letting go of the Death Bow")
    val swapDelay = numberSetting("Swap delay", min = 0.0, max = 10.0, 0.0, step = 1.0)
    val swapSlot = numberSetting("Item slot", 1.0, 8.0, 1.0, step = 1.0)
    val autoSwapArmor = booleanSetting("Auto swap armor")
    val clickDelay = numberSetting("Click delay", min = 0.0, max = 10.0, 1.0, step = 1.0)
    val swapWardrobeSlot = numberSetting("Loadout slot", 1.0, 12.0, 1.0, step = 1.0)
    val autoReleaseLB = booleanSetting("Auto release Last Breath", description = "Automatically releases the Last Breath for Storm PY")
    val releaseTime = numberSetting("Release time", min = 34.00, max = 35.0, 34.5, step = 0.05, unit = "s")
    val autoTrack = booleanSetting("Auto track Storm", description = "Tracks Storm for you after releasing Last Breath")
    val pitchLimit = numberSetting("Pitch limit", min = -85.0, max = -0.0, -70.0, step = 1.0)
    val waypointOffset = numberSetting("Waypoint offset", min = -2.0, max = 2.0, 0.0, step = 0.1)
    val autoWalkForward = booleanSetting("Auto walk forward",  description = "Walks forward for you after releasing Last Breath")
    val autoSwapTerm = booleanSetting("Auto swap term in Storm", description = "Swaps to Term for you after releasing Last Breath")
    val leftClickWithTerm = booleanSetting("Left click with term after")
    //Mage
    val autoHit = booleanSetting("Auto hit storm", description = "Automatically hits storm at the specified time")
    val hitTimePurple = numberSetting("Hit time purple", min = 16.00, max = 24.0, 17.9, step = 0.05, unit = "s")
    val hitTimeYellow = numberSetting("Hit time yellow", min = 36.00, max = 40.0, 37.9, step = 0.05, unit = "s")
    val autoSwapAfterLeap = booleanSetting("Swap item after right click leap")
    val swapDelayLeap = numberSetting("Swap delay", min = 0.0, max = 10.0, 0.0, step = 1.0)
    val swapSlotAfterLeap = numberSetting("Item slot leap", 1.0, 8.0, 1.0, step = 1.0)
    val autoSneak = booleanSetting("Auto sneak at yellow")


    var maxor = false
    var storm = false
    var necron = false

    var swapping = false
    var aiming = false
    var useTime = 0
    var stormTicks = 0
    val aimPos = Vec3(100.0, 181.0, 64.0)
    val stormPos = Vec3(83.56969386901531, 184.0, 33.911591511095395)
    var sneak = true

    fun register() {
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (mc.player == null) return@register
            if (storm && mc.player!!.x in 33.0..35.0 && mc.player!!.y >= 169.0 && mc.player!!.z in 63.0..70.0 && autoSneak.value) {
                mc.options.keyShift.isDown = sneak
                mc.options.keyDown.isDown = false
                schedule(15) {
                    sneak = false
                }
            } else {
                sneak = true
            }
            if (storm) {
                ctx.renderBoxBounds(aimPos.add(waypointOffset.value, 0.0, 0.0).aabb(0.2), Color.CYAN, phase = false)
                ctx.renderBoxBounds(stormPos.aabb(0.2), Color.CYAN, phase = false)

            }
            if (!aiming) return@register
            if (!inArea()) return@register
            rotate(getLook().first, getLook().second)
        }
        ClientTickEvents.END_CLIENT_TICK.register { ctx ->
            if (mc.player == null) return@register
            if (mc.player!!.xRot < pitchLimit.value.toFloat() && inArea()) {
                if (autoWalkForward.value) mc.options.keyUp.isDown = false
                schedule(5) {
                    aiming = false
                }
            }
        }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { minecraft, level ->
            maxor = false
            storm = false
            necron = false
            aiming = false
            swapping = false
        }
    }

    fun bowReleased(item: ItemStack, entity: LivingEntity) {
        if (entity != mc.player || !enabled) return
        if (maxor) {
            if (!item.hoverName.string.contains("Death Bow") || !autoSwapCritItem.value) return

            schedule(2) { maxor = false }

            schedule(swapDelay.value) {
                if (mc.player?.inventory?.selectedSlot == swapSlot.value.toInt() - 1) return@schedule
                mc.player?.inventory?.selectedSlot = swapSlot.value.toInt() - 1
            }
        }

        if (storm && stormTicks > releaseTime.value - 5 && inArea()) {
            if (!item.hoverName.string.contains("Last Breath") || !autoSwapTerm.value) return
            storm = false

                for (slot in 0 until 9) {
                    val stack = mc.player!!.inventory.getItem(slot)
                    if (stack.hoverName.string.contains("Terminator")) {
                        mc.player!!.inventory.selectedSlot = slot
                        if (leftClickWithTerm.value) {
                            schedule(1) {
                                mc.options.keyAttack.isDown = true
                            }
                        }
                        break
                    }
                }

        }
    }

    fun handleChat(unformatted: String) {
        if (!enabled) return
        if (unformatted.contains("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!")) {
            maxor = true
        } else if (unformatted.contains("[BOSS] Storm: Pathetic Maxor, just like expected.")) {
            swapping = false
            maxor = false
            storm = true
            stormTicks = 0
        } else if (unformatted.contains("[BOSS] Goldor: Who dares trespass into my domain")) {
            storm = false
        } else if (unformatted.contains("⚠ Storm is enraged! ⚠") && leftClickWithTerm.value && inArea()) {
            aiming = false
            mc.options.keyUp.isDown = false
            mc.options.keyAttack.isDown = false
        } else if (unformatted.contains("[BOSS] Necron: You went further than any human before, congratulations.")) {
            necron = true
        } else if (unformatted.contains("[BOSS] Necron: All this, for nothing...")) {
            necron = false
        }
    }

    fun handleScreen(packet: ClientboundOpenScreenPacket) {
        if (!packet.title.string.contains("Loadout") || !swapping || mc.player == null) return
        swapping = false

        schedule(clickDelay.value, true) {
            val sc = mc.screen as? AbstractContainerScreen<*> ?: return@schedule
            if (!sc.title.string.contains("Loadout")) return@schedule

            mc.player!!.clickSlot(sc.menu.containerId, getLoadoutIndex(swapWardrobeSlot.value.toInt()))
            schedule(0) {
                if (mc.player?.containerMenu != null) {
                    mc.player!!.closeContainer()
                }
            }
        }
    }

    fun useItem(player: Player, interactionHand: InteractionHand, result: InteractionResult) {
        if (player.mainHandItem.uuid() == "STARRED_BONE_BOOMERANG" && necron && autoSwapCritItem.value && player.y > 20) {
            if (autoSwapArmor.value) {
                mc.connection?.sendCommand("loadout")
                swapping = true
            }
            schedule(swapDelay.value) {
                if (player.inventory.selectedSlot == swapSlot.value.toInt() - 1) return@schedule
                player.inventory.selectedSlot = swapSlot.value.toInt() - 1
            }
        }

        if (player.mainHandItem.uuid() == "INFINITE_SPIRIT_LEAP" && storm && autoSwapAfterLeap.value) {
            val pos = player.position()
            schedule(swapDelayLeap.value) {
                if (pos.x in 88.0..100.0 && pos.y in 163.0..168.0 && pos.z in 88.0..97.0) {
                    if (player.inventory.selectedSlot == swapSlotAfterLeap.value.toInt() - 1) return@schedule
                    player.inventory.selectedSlot = swapSlotAfterLeap.value.toInt() - 1
                }
            }
        }
    }

    fun serverTick() {
        if (mc.player == null || !enabled) return

        stormTicks++

        if (mc.player!!.mainHandItem.item is BowItem && mc.player!!.isUsingItem) {
            useTime++
        } else {
            if (useTime >= 20 && autoSwapArmor.value && maxor) {
                mc.connection?.sendCommand("loadout")
                swapping = true
            }
            useTime = 0
        }

        if (storm && stormTicks >= releaseTime.value * 20 && inArea()) {
            if (autoWalkForward.value) { mc.options.keyUp.isDown = true }

            val (yaw, pitch) = getLook()
            if (autoTrack.value && abs(normalizeYaw(mc.player!!.yRot) - yaw) < 5.0 && abs(mc.player!!.xRot - pitch) < 2.0) { aiming = true }

            if (mc.player!!.mainHandItem.hoverName.string.contains("Last Breath") && mc.player!!.isUsingItem && autoReleaseLB.value) {
                mc.options.keyUse.isDown = false
            }
        }

        if (autoHit.value && mc.player?.mainHandItem?.uuid() == "HYPERION") {
            if (stormTicks.toDouble() == (hitTimePurple.value * 20) - 10 && storm) {
                mc.options.keyAttack.isDown = false
                schedule(10, true) {
                    Chat.send("purple")
                    mc.options.keyAttack.clickCount++
                }
            }

            if (stormTicks.toDouble() == (hitTimeYellow.value * 20) - 10 && storm) {
                mc.options.keyAttack.isDown = false
                schedule(10, true) {
                    Chat.send("yellow")
                    mc.options.keyAttack.clickCount++
                }
            }
        }
    }

    private fun inArea(): Boolean {
        val pos = mc.player?.position() ?: return false
        return (pos.x in 86.0..110.00 && pos.y == 169.0 && pos.z in 60.0..78.0)
    }

    private fun getLook(): Pair<Float, Float> =
        aimPos.add(waypointOffset.value, 0.0, 0.0).getLook(Vec3(mc.player!!.renderPos.x,mc.player!!.eyePosition.y, mc.player!!.renderPos.z))

    var tintActive = false

    @JvmStatic
    fun tintBow(): Boolean {
        if (!enabled) return false
        if (BowItem.getPowerForTime(useTime) == 1f && bowTint.value && tintActive) return true
        return false
    }

    @JvmStatic
    fun tintArgb(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (((argb ushr 8) and 0xFF) * 0.3f).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * 0.3f).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
