/*
 * Copyright (c) 2023, Macweese <macweese@pm.me>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.macweese.clickminimaporb;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Objects;

@PluginDescriptor(
	name = "Click Minimap Orbs",
	description = "Prevents clicking through the minimap orbs on resizable mode",
	tags = {"minimap", "status", "orbs", "click", "through"},
	conflicts = "ClickMinimapOrbsPlugin"
)
public class ClickMinimapOrbsPlugin extends Plugin
{
	@Inject
	private ClickMinimapOrbsConfig config;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	// Enum 906 contains a map of all the item ids and
	// the amount of special attack energy they require
	// (since rev 217.1)
	private static final int ENUM_SPEC_ITEM_MAP = 906;

	private static final int SCRIPT_UPDATE_HITPOINTS_ORB = 446;
	private static final int SCRIPT_UPDATE_SPEC_ORB = 2792;

	private Widget specOrbWidget;
	private Widget hitpointsOrbWidget;

	private boolean consumeSpecOrb;
	private boolean consumeHitpointsOrb;

	@Provides
	ClickMinimapOrbsConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClickMinimapOrbsConfig.class);
	}

	@Override
	protected void startUp()
	{
		consumeSpecOrb = config.blockSpecialAttackOrb();
		consumeHitpointsOrb = config.blockHitpointsOrb();

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			specOrbWidget = client.getWidget(ComponentID.MINIMAP_SPEC_ORB).getStaticChildren()[1];
			hitpointsOrbWidget = client.getWidget(ComponentID.MINIMAP_HEALTH_ORB).getStaticChildren()[1];

			if (!client.isResized())
			{
				return;
			}

			setSpecOrbConsuming(consumeSpecOrb);
			setHitpointsOrbConsuming(consumeHitpointsOrb);
		});
	}

	@Override
	protected void shutDown()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			setSpecOrbConsuming(false);
			setHitpointsOrbConsuming(false);
		});
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equalsIgnoreCase("clickminimaporbs2d3a8ae"))
		{
			return;
		}

		consumeSpecOrb = config.blockSpecialAttackOrb();
		consumeHitpointsOrb = config.blockHitpointsOrb();

		clientThread.invokeLater(() ->
		{
			if (!client.isResized())
			{
				return;
			}

			setSpecOrbConsuming(consumeSpecOrb);
			setHitpointsOrbConsuming(consumeHitpointsOrb);
		});
	}

	@Subscribe
	private void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.MINIMAP)
		{
			return;
		}

		specOrbWidget = client.getWidget(ComponentID.MINIMAP_SPEC_ORB).getStaticChildren()[1];
		hitpointsOrbWidget = client.getWidget(ComponentID.MINIMAP_HEALTH_ORB).getStaticChildren()[1];
	}

	@Subscribe
	private void onScriptPostFired(ScriptPostFired event)
	{
		if (!client.isResized())
		{
			return;
		}

		if (event.getScriptId() == SCRIPT_UPDATE_HITPOINTS_ORB)
		{
			if (hitpointsOrbWidget == null)
			{
				hitpointsOrbWidget = client.getWidget(ComponentID.MINIMAP_HEALTH_ORB).getStaticChildren()[1];
			}

			boolean permeable = hitpointsOrbWidget.isHidden() || !hitpointsOrbWidget.getNoClickThrough();

			if (permeable)
			{
				setHitpointsOrbConsuming(consumeHitpointsOrb);
			}
		}
		else if (event.getScriptId() == SCRIPT_UPDATE_SPEC_ORB)
		{
			if (specOrbWidget == null)
			{
				specOrbWidget = client.getWidget(ComponentID.MINIMAP_SPEC_ORB).getStaticChildren()[1];
			}

			boolean permeable = specOrbWidget.isHidden() || !specOrbWidget.getNoClickThrough();

			if (permeable)
			{
				setSpecOrbConsuming(consumeSpecOrb);
			}
		}
	}

	private boolean hasSpecialAttackItem()
	{
		final ItemContainer equipmentContainer = client.getItemContainer(InventoryID.EQUIPMENT);

		if (equipmentContainer == null)
		{
			return false;
		}

		final Item[] items = client.getItemContainer(InventoryID.EQUIPMENT).getItems();

		return Arrays.stream(items)
				.dropWhile(Objects::isNull)
				.mapToInt(Item::getId)
				.anyMatch(i -> Arrays.stream(client.getEnum(ENUM_SPEC_ITEM_MAP).getKeys()).anyMatch(j -> i == j));
	}

	private boolean isDebilitated()
	{
		return client.getVarbitValue(Varbits.PARASITE) > 0
				|| client.getVarpValue(VarPlayer.POISON) > 0
				|| client.getVarpValue(VarPlayer.DISEASE_VALUE) > 0;
	}

	private void setSpecOrbConsuming(boolean consume)
	{
		if (specOrbWidget == null)
		{
			return;
		}

		if (consume)
		{
			specOrbWidget.setNoClickThrough(consumeSpecOrb);
			specOrbWidget.setHidden(false);
		}
		else
		{
			specOrbWidget.setHidden(!hasSpecialAttackItem());
		}
	}

	private void setHitpointsOrbConsuming(boolean consume)
	{
		if (hitpointsOrbWidget == null)
		{
			return;
		}

		boolean isPoisonedOrDiseased = isDebilitated();

		if (consume)
		{
			hitpointsOrbWidget.setNoClickThrough(consumeHitpointsOrb);
			hitpointsOrbWidget.setHidden(false);
		}
		else
		{
			hitpointsOrbWidget.setNoClickThrough(isPoisonedOrDiseased);
			hitpointsOrbWidget.setHidden(!isPoisonedOrDiseased);
		}
	}
}