package dev.compactmods.machines.api.room.upgrade;

import dev.compactmods.machines.api.event.IEventListenerList;
import dev.compactmods.machines.api.room.upgrade.events.RoomUpgradeEvent;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipProvider;

import java.util.stream.Stream;

public interface RoomUpgrade extends TooltipProvider {

    RoomUpgradeType<?> getType();

    default void gatherEvents(IEventListenerList<RoomUpgradeEvent> listenerList) {}
}
