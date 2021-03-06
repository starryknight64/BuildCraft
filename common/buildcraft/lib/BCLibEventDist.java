/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.lib;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.WorldServer;

import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.client.model.ModelHolderRegistry;
import buildcraft.lib.client.reload.ReloadManager;
import buildcraft.lib.client.render.DetachedRenderer;
import buildcraft.lib.client.render.fluid.FluidRenderer;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;
import buildcraft.lib.client.sprite.SpriteHolderRegistry;
import buildcraft.lib.debug.BCAdvDebugging;
import buildcraft.lib.debug.ClientDebuggables;
import buildcraft.lib.item.ItemDebugger;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.misc.FakePlayerProvider;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.misc.data.ModelVariableData;
import buildcraft.lib.net.MessageDebugRequest;
import buildcraft.lib.net.MessageManager;
import buildcraft.lib.net.cache.BuildCraftObjectCaches;

import buildcraft.core.client.ConfigGuiFactoryBC;

public enum BCLibEventDist {
    INSTANCE;

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof EntityPlayerMP) {
            EntityPlayerMP playerMP = (EntityPlayerMP) entity;
            // Delay sending join messages to player as it makes it work when in single-player
            MessageUtil.doDelayed(() -> MarkerCache.onPlayerJoinWorld(playerMP));
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        MarkerCache.onWorldUnload(event.getWorld());
        if (event.getWorld() instanceof WorldServer) {
            FakePlayerProvider.INSTANCE.unloadWorld((WorldServer) event.getWorld());
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void onConnectToServer(ClientConnectedToServerEvent event) {
        BuildCraftObjectCaches.onClientJoinServer();
        // Really obnoxious warning
        if (true | !BCLib.DEV) {
            /* If people are in a dev environment or have toggled the flag then they probably already know about this */
            Runnable r = () -> {
                try {
                    Thread.sleep(4000);
                } catch (InterruptedException e) {
                    // NO-OP
                }

                String ver;
                if (BCLib.VERSION.startsWith("$")) {
                    ModContainer mod = Loader.instance().getIndexedModList().get(BCLib.MODID);
                    if (mod == null) {
                        ver = "[UNKNOWN-MANUAL-BUILD]";
                    } else {
                        ver = mod.getDisplayVersion();
                        if (ver.startsWith("${")) {
                            // The difference with the above is intentional
                            ver = "[UNKNOWN_MANUAL_BUILD]";
                        }
                    }
                } else {
                    ver = BCLib.VERSION;
                }

                ITextComponent componentVersion = new TextComponentString(ver);
                Style styleVersion = new Style();
                styleVersion.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, BCLib.VERSION));
                // styleVersion.setHoverEvent(new HoverEvent(HoverEvent.Action., valueIn));
                componentVersion.setStyle(styleVersion);

                String bodyText = "<!--\n" //
                    + "If your issue is more of a question (like how does a machine work or a sugestion), please use our Discord instead: https://discord.gg/BuildCraft\n"//
                    + "Please fill in all relavant information below.\n"//
                    + "Please do not put the entire log here, upload it on pastebin (https://pastebin.com/) or gist (https://gist.github.com/) and paste here the link.\n"//
                    + "-->\n\n" //
                    + "BuildCraft version: " + BCLib.VERSION + "\n" //
                    + "Forge version: " + ForgeVersion.getVersion() + "\n" //
                    + "Link to crash report or log: {none given}\n" //
                    + "Singleplayer or multiplayer: \n" //
                    + "Steps to reproduce: \n" //
                    + "Additional information: \n"//
                    + "Mod list: \n\n"
                    + Loader.instance().getCrashInformation().replaceAll("UCHIJA+", "Loaded").replace("\t|", "|");

                String githubIssuesUrl;
                try {
                    githubIssuesUrl = "https://github.com/BuildCraft/BuildCraft/issues/new?body="
                        + URLEncoder.encode(bodyText, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new Error("UTF-8 isn't a valid charset? What?", e);
                }
                ITextComponent componentGithubLink = new TextComponentString("here");
                Style styleGithubLink = new Style();
                styleGithubLink.setUnderlined(Boolean.TRUE);
                styleGithubLink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, githubIssuesUrl));
                componentGithubLink.setStyle(styleGithubLink);

                TextComponentString textWarn = new TextComponentString("WARNING: BuildCraft ");
                textWarn.appendSibling(componentVersion);
                textWarn.appendText(" is in ALPHA!");

                TextComponentString textReport = new TextComponentString("  Report BuildCraft bugs you find ");
                textReport.appendSibling(componentGithubLink);

                TextComponentString textDesc = new TextComponentString("  and include the BuildCraft version ");
                textDesc.appendSibling(componentVersion);
                textDesc.appendText(" in the description");

                TextComponentString textLag =
                    new TextComponentString("  If you have performance problems then try disabling");
                TextComponentString textConfigLink =
                    new TextComponentString("everything in the BuildCraft perfomance config section.");
                textConfigLink.setStyle(new Style() {

                    {
                        setUnderlined(true);
                    }

                    @Override
                    public Style createShallowCopy() {
                        return this;
                    }

                    @Override
                    public Style createDeepCopy() {
                        return this;
                    }

                    @Override
                    @Nullable
                    public ClickEvent getClickEvent() {
                        // Very hacky, but it technically works
                        StackTraceElement[] trace = new Throwable().getStackTrace();
                        for (StackTraceElement elem : trace) {
                            if (GuiScreen.class.getName().equals(elem.getClassName())) {
                                ConfigGuiFactoryBC.GuiConfigManager newGui =
                                    new ConfigGuiFactoryBC.GuiConfigManager(Minecraft.getMinecraft().currentScreen);
                                Minecraft.getMinecraft().displayGuiScreen(newGui);
                                return null;
                            }
                        }
                        return null;
                    }
                });

                ITextComponent[] lines = { textWarn, textReport, textDesc, textLag, textConfigLink };
                GuiNewChat chat = Minecraft.getMinecraft().ingameGUI.getChatGUI();
                for (ITextComponent line : lines) {
                    chat.printChatMessage(line);
                }
            };
            new Thread(r).start();
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void textureStitchPre(TextureStitchEvent.Pre event) {
        ReloadManager.INSTANCE.preReloadResources();
        TextureMap map = event.getMap();
        SpriteHolderRegistry.onTextureStitchPre(map);
        ModelHolderRegistry.onTextureStitchPre(map);
        FluidRenderer.onTextureStitchPre(map);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void textureStitchPost(TextureStitchEvent.Post event) {
        TextureMap map = event.getMap();
        SpriteHolderRegistry.onTextureStitchPost();
        FluidRenderer.onTextureStitchPost(map);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void modelBake(ModelBakeEvent event) {
        SpriteHolderRegistry.exportTextureMap();
        LaserRenderer_BC8.clearModels();
        ModelHolderRegistry.onModelBake();
        ModelVariableData.onModelBake();
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void renderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null) return;
        float partialTicks = event.getPartialTicks();

        DetachedRenderer.INSTANCE.renderWorldLastEvent(player, partialTicks);
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent event) {
        if (event.phase == Phase.END) {
            BCAdvDebugging.INSTANCE.onServerPostTick();
            MessageUtil.postTick();
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void clientTick(ClientTickEvent event) {
        if (event.phase == Phase.END) {
            BuildCraftObjectCaches.onClientTick();
            MessageUtil.postTick();
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayerSP player = mc.player;
            if (player != null && ItemDebugger.isShowDebugInfo(player)) {
                RayTraceResult mouseOver = mc.objectMouseOver;
                if (mouseOver != null) {
                    IDebuggable debuggable = ClientDebuggables.getDebuggableObject(mouseOver);
                    if (debuggable instanceof TileEntity) {
                        TileEntity tile = (TileEntity) debuggable;
                        MessageManager.sendToServer(new MessageDebugRequest(tile.getPos(), mouseOver.sideHit));
                    } else if (debuggable instanceof Entity) {
                        // TODO: Support entities!
                    }
                }
            }
        }
    }
}
