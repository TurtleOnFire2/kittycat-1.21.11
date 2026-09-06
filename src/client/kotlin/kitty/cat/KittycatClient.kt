package kitty.cat

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import kitty.cat.config.ConfigManager
import kitty.cat.features.dungeons.AutoLB
import kitty.cat.features.dungeons.LeverTriggerbot
import kitty.cat.features.dungeons.Relics
import kitty.cat.features.dungeons.Storm
import kitty.cat.features.dungeons.Terminals
import kitty.cat.features.huds.BestiaryHud
import kitty.cat.utils.BoneUtils
import kitty.cat.features.kuudra.RendMacro
import kitty.cat.features.misc.ChatMacros
import kitty.cat.features.misc.Pests
import kitty.cat.features.visual.ArrowTracers
import kitty.cat.features.visual.BestiaryESP
import kitty.cat.features.visual.CatEars
import kitty.cat.features.visual.CustomESP
import kitty.cat.features.visual.ClickGui as ClickGuiFeature
import kitty.cat.gui.Hud
import kitty.cat.gui.clickgui.ClickGui
import kitty.cat.features.Feature
import kitty.cat.features.huds.BackboneHud
import kitty.cat.features.huds.SupplyHud
import kitty.cat.features.kuudra.PearlWaypoints
import kitty.cat.features.kuudra.RendDamage
import kitty.cat.features.kuudra.SafeSpots
import kitty.cat.features.kuudra.Stun
import kitty.cat.features.kuudra.Supplies
import kitty.cat.features.kuudra.SupplyCheats
import kitty.cat.features.misc.FarmHelper
import kitty.cat.features.misc.Safari
import kitty.cat.features.settings.KeybindSetting
import kitty.cat.render.nanovg.NVGPIPRenderer
import kitty.cat.utils.Chat
import kitty.cat.utils.LocationUtils
import kitty.cat.render.world.RenderLayers
import kitty.cat.utils.KuudraUtils
import kitty.cat.utils.NameChanger
import kitty.cat.utils.PictureInPictureMode
import kitty.cat.utils.RotationUtils
import kitty.cat.utils.Schedule
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB
import org.lwjgl.glfw.GLFW
import org.reflections.Reflections

object KittycatClient : ClientModInitializer {
	private val keybindPressedState = mutableMapOf<KeybindSetting, Boolean>()

	private val featureList: List<Feature> =
		Reflections("kitty.cat.features")
			.getSubTypesOf(Feature::class.java)
			.mapNotNull { clazz ->
				try {
					clazz.getField("INSTANCE").get(null) as Feature
				} catch (_: Exception) {
					null
				}
			}

	val mc get() = Minecraft.getInstance()
	var openGui = false
	var openHud = false


	var keybindShowHud: KeyMapping? = null

	override fun onInitializeClient() {
		// Force class-init so the custom render pipelines are registered before the
		// renderer precompiles static pipelines, not lazily mid-frame on first draw.
		RenderLayers.LINES_THROUGH_WALLS

		PictureInPictureRendererRegistry.register { NVGPIPRenderer() }

		ConfigManager.initialize(featureList)
		ClientLifecycleEvents.CLIENT_STOPPING.register {
			ConfigManager.saveNow()
		}

		val keybindCategory = KeyMapping.Category(Identifier.fromNamespaceAndPath("kittycat", "general"))
		keybindShowHud = KeyMappingHelper.registerKeyMapping(
			KeyMapping(
				"key.kittycat.hud_insight",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_LEFT_ALT,
				keybindCategory
			)
		)

		ClientTickEvents.END_CLIENT_TICK.register { client ->
			ConfigManager.onTick()

			if (openGui) {
				openGui = false
				ClickGuiFeature.openGui()
			}

			if (openHud) {
				openHud = false
				Hud.open()
			}

			if (client.gui.screen() is ClickGui) return@register

			val window = client.window
			featureList.forEach { feature ->
				feature.keybindSettings.forEach { setting ->
					if (setting.keyCode == KeybindSetting.UNBOUND) {
						keybindPressedState[setting] = false
						return@forEach
					}

					val pressedNow = InputConstants.isKeyDown(window, setting.keyCode)
					val pressedBefore = keybindPressedState[setting] ?: false
					keybindPressedState[setting] = pressedNow

					if (pressedNow && !pressedBefore) {
						feature.onKeybindPressed(setting)
					}
				}
			}
		}

		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			dispatcher.register(
				literal("pip").executes {
					val active = PictureInPictureMode.toggle()
					Chat.send("Picture-in-picture ${if (active) "enabled" else "disabled"}")
					1
				}
			)
			dispatcher.register(
				literal("kc")
					.then(
						literal("gui").executes { context ->
							openGui = true
							1
						}
					)
					.then(
						literal("hud").executes {
							openHud = true
							1
						}
					)
					.then(
						literal("macros").executes {
							ChatMacros.openGui = true
							1
						}
					)
					.then(
						literal("be").executes {
							BestiaryESP.openGui = true
							1
						}
					)
					.executes {
						openGui = true
						1
					}
			)
			dispatcher.register(
				literal("rotate")
					.then(
						argument("yaw", FloatArgumentType.floatArg())
							.then(
								argument("pitch", FloatArgumentType.floatArg())
									.executes { ctx ->
										val yaw = FloatArgumentType.getFloat(ctx, "yaw")
										val pitch = FloatArgumentType.getFloat(ctx, "pitch")
										RotationUtils.rotate(yaw, pitch)
										1
									}
							)
					)
			)
			dispatcher.register(
				literal("cesp")
					.then(
						literal("add")
							.then(
								argument("name", StringArgumentType.greedyString())
									.executes { ctx ->
										val name = StringArgumentType.getString(ctx, "name")
										val color = if (CustomESP.tracerList.contains(name)) "§2" else "§c"
										Chat.sendWithClickable("Added $name to CESP",
											Chat.Clickable("§c[Remove]", ClickEvent.RunCommand("/cesp remove $name")),
											Chat.Clickable("$color[Tracer]", ClickEvent.RunCommand("/cesp tracer $name"))
										)
										CustomESP.entityList.add(name)
										CustomESP.saveConfig()
										1
									}
							)
					)
					.then(
						literal("remove")
							.then(
								argument("name", StringArgumentType.greedyString())
									.executes { ctx ->
										val name = StringArgumentType.getString(ctx, "name")
										Chat.send("Removed $name from CESP")
										CustomESP.entityList.remove(name)
										CustomESP.tracerList.remove(name)
										CustomESP.saveConfig()
										1
									}
							)
					)
					.then(
						literal("clear").executes {
							Chat.send("Cleared CESP")
							CustomESP.entityList.clear()
							CustomESP.tracerList.clear()
							CustomESP.saveConfig()
							1
						}
					)
					.then(
						literal("list").executes {
							CustomESP.entityList.forEach {
								val color = if (CustomESP.tracerList.contains(it)) "§2" else "§c"
								Chat.sendWithClickable(it,
									Chat.Clickable("§c[Remove]", ClickEvent.RunCommand("/cesp remove $it")),
									Chat.Clickable("$color[Tracer]", ClickEvent.RunCommand("/cesp tracer $it"))
								)
							}
							1
						}
					)
					.then(
						literal("tracer")
							.then(
								argument("name", StringArgumentType.greedyString())
									.executes { ctx ->
										val name = StringArgumentType.getString(ctx, "name")
										if (CustomESP.tracerList.contains(name)) {
											CustomESP.tracerList.remove(name)
										} else {
											CustomESP.tracerList.add(name)
										}
										val color = if (CustomESP.tracerList.contains(name)) "§2" else "§c"
										Chat.sendWithClickable("$name",
											Chat.Clickable("§c[Remove]", ClickEvent.RunCommand("/cesp remove $name")),
											Chat.Clickable("$color[Tracer]", ClickEvent.RunCommand("/cesp tracer $name"))
										)
										CustomESP.saveConfig()
										1
									}
							)

					)
					.then(
						literal("textures")
							.executes { ctx ->
								CustomESP.getAllTextureStrings("")
								1
							}
							.then(
								argument("name", StringArgumentType.greedyString())
									.executes { ctx ->
										val name = StringArgumentType.getString(ctx, "name")
										CustomESP.getAllTextureStrings(name)
										1
									}
							)
					)
					.then(
						literal("mob")
							.executes { ctx ->
								CustomESP.getMobString("")
								1
							}
							.then(
								argument("name", StringArgumentType.greedyString())
									.executes { ctx ->
										val name = StringArgumentType.getString(ctx, "name")
										CustomESP.getMobString(name)
										1
									}
							)
					)
			)
		}

		KuudraUtils.register()
		PearlWaypoints.register()
		Supplies.register()
		RendMacro.register()
		ArrowTracers.register()
		CatEars.register()
		AutoLB.register()
		Pests.register()
		Hud.register()
		BestiaryHud.register()
		ChatMacros.register()
		Schedule.register()
		Storm.register()
		CustomESP.register()
		BestiaryESP.register()
		Relics.register()
		Terminals.register()
		LeverTriggerbot.register()
		LocationUtils.register()
		BoneUtils.register()
		Stun.register()
		Safari.register()
		RendDamage.register()
		SupplyCheats.register()
		SafeSpots.register()
		FarmHelper.register()

		BackboneHud
		SupplyHud
	}
}
