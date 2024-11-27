package dev.compactmods.machines.room.upgrade.example;

import com.mojang.serialization.MapCodec;
import dev.compactmods.machines.api.event.IEventListenerList;
import dev.compactmods.machines.api.room.RoomInstance;
import dev.compactmods.machines.api.room.upgrade.RoomUpgrade;
import dev.compactmods.machines.api.room.upgrade.RoomUpgradeType;
import dev.compactmods.machines.api.room.upgrade.events.RoomUpgradeEvent;
import dev.compactmods.machines.api.room.upgrade.events.lifecycle.UpgradeTickedEventListener;
import dev.compactmods.machines.room.upgrade.RoomUpgrades;
import dev.compactmods.machines.util.item.ItemHandlerUtil;
import dev.compactmods.spatial.aabb.AABBHelper;
import dev.compactmods.spatial.vector.VectorUtils;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.CommonColors;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TreeCutterUpgrade implements RoomUpgrade {

    public static final MapCodec<TreeCutterUpgrade> CODEC = MapCodec.unit(TreeCutterUpgrade::new);

    @Override
    public void addToTooltip(@NotNull Item.TooltipContext ctx, Consumer<Component> tooltips, @NotNull TooltipFlag flags) {
        final var c = Component.literal("Tree Cutter")
                .withColor(CommonColors.LIGHT_GRAY);

        tooltips.accept(c);
    }

    @Override
    public void gatherEvents(IEventListenerList<RoomUpgradeEvent> listenerList) {
        final UpgradeTickedEventListener ticker = TreeCutterUpgrade::onTick;
        listenerList.addListener(ticker);
    }

    @Override
    public RoomUpgradeType<TreeCutterUpgrade> getType() {
        return RoomUpgrades.TREECUTTER.get();
    }

    public static void onTick(ServerLevel level, RoomInstance room, ItemStack upgrade) {
        final var innerBounds = room.boundaries().innerBounds();

        final var everythingLoaded = room.boundaries()
                .innerChunkPositions()
                .allMatch(cp -> level.shouldTickBlocksAt(cp.toLong()));

        // TODO - Implement upgrade cooldowns (i.e. retry in 100 ticks if room isn't loaded)
        if (!everythingLoaded) return;

        var energyHandler = upgrade.getCapability(Capabilities.EnergyStorage.ITEM);

        boolean doItemDamage = false;
        boolean preferEnergy = false;
        int maxAllowed = 0;
        if (upgrade.isDamageableItem()) {
            doItemDamage = true;
            var durabilityLeft = upgrade.getMaxDamage() - upgrade.getDamageValue();
            maxAllowed = Math.clamp(durabilityLeft, 0, 5);
        }

        if (energyHandler != null && energyHandler.canExtract()) {
            doItemDamage = true;
            preferEnergy = true;
            maxAllowed = Math.clamp(energyHandler.getEnergyStored() / 10, 0, 5);
        }

        final var treeBlocks = BlockPos.betweenClosedStream(innerBounds)
                .map(pos -> {
                    final var state = level.getBlockState(pos);
                    return Pair.of(pos.immutable(), state);
                })
                .filter(pair -> {
                    BlockState state = pair.right();
                    if(state.is(BlockTags.LOGS)) return true;
                    if(state.is(BlockTags.LEAVES)) {
                        if (state.hasProperty(LeavesBlock.PERSISTENT)) return !state.getValue(LeavesBlock.PERSISTENT);
                        return true;
                    }

                    return false;
                })
                .limit(maxAllowed)
                .collect(Collectors.toUnmodifiableSet());

        final var numLogs = treeBlocks.size();

        if (!treeBlocks.isEmpty()) {

            final var bounds = room.boundaries().innerBounds();
            final var minCorner = AABBHelper.minCorner(bounds);
            final var lastDitch = BlockPos.containing(minCorner.x(), minCorner.y() + 1, minCorner.z());

            // TODO: Actual persistence and cooldowns for when the inventories fill up
            final var inventories = getInventories(level, bounds).toList();

            // If we have no valid inventories, do nothing
            if (inventories.isEmpty())
                return;

            for (Pair<BlockPos, BlockState> pos : treeBlocks) {
                final var blockEntity = level.getBlockEntity(pos.left());
                final var drops = Block.getDrops(pos.right(), level, pos.left(), blockEntity, null, upgrade);

                level.destroyBlock(pos.left(), false);

                if (!drops.isEmpty()) {
                    List<ItemStack> remaining = new ArrayList<>();
                    for (final var cornerInv : inventories) {
                        remaining = ItemHandlerUtil.insertMultipleStacks(cornerInv.inventory, drops);
                        if (remaining.isEmpty()) break;
                    }

                    if (!remaining.isEmpty()) {
                        for (var failedStack : remaining) {
                            Block.popResource(level, lastDitch, failedStack);
                        }
                    }
                }
            }

            if (preferEnergy) {
                energyHandler.extractEnergy(numLogs * 10, false);
            } else {
                upgrade.hurtAndBreak(numLogs, level, null, (item) -> {
                    upgrade.shrink(1);
                });
            }
        }
    }

    private record LocatedInventory(BlockPos pos, IItemHandler inventory) {
    }

    private static Stream<LocatedInventory> getInventories(ServerLevel level, AABB bounds) {
        return AABBHelper.allCorners(bounds)
                .map(BlockPos::immutable)
                .flatMap(pos -> Stream.of(
                                level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null),
                                level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP)
                        )
                        .filter(Objects::nonNull)
                        .map(handler -> new LocatedInventory(pos, handler)));
    }
}
