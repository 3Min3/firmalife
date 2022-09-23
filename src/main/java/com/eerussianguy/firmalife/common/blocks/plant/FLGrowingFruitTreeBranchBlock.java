package com.eerussianguy.firmalife.common.blocks.plant;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.eerussianguy.firmalife.common.blockentities.FLBlockEntities;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blockentities.TickCounterBlockEntity;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.plant.fruit.FruitTreeBranchBlock;
import net.dries007.tfc.common.blocks.plant.fruit.GrowingFruitTreeBranchBlock;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.calendar.ICalendar;
import net.dries007.tfc.util.climate.ClimateRange;
import org.jetbrains.annotations.Nullable;

/**
 * temporary before i fix this in tfc
 */
public class FLGrowingFruitTreeBranchBlock extends GrowingFruitTreeBranchBlock
{
    private static boolean canGrowInto(LevelReader level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || Helpers.isBlock(state, TFCTags.Blocks.FRUIT_TREE_LEAVES);
    }

    private static boolean allNeighborsEmpty(LevelReader level, BlockPos pos, @Nullable Direction excludingSide)
    {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.Plane.HORIZONTAL)
        {
            mutablePos.set(pos).move(direction);
            if (direction != excludingSide && !canGrowInto(level, mutablePos))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean canGrowIntoLocations(LevelReader level, BlockPos... pos)
    {
        for (BlockPos p : pos)
        {
            if (!canGrowInto(level, p))
            {
                return false;
            }
        }
        return true;
    }

    private static final Direction[] NOT_DOWN = new Direction[] {Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH, Direction.UP};

    private final Supplier<? extends Block> body;
    private final Supplier<? extends Block> leaves;

    public FLGrowingFruitTreeBranchBlock(ExtendedProperties properties, Supplier<? extends Block> body, Supplier<? extends Block> leaves, Supplier<ClimateRange> climateRange)
    {
        super(properties, body, leaves, climateRange);

        this.body = body;
        this.leaves = leaves;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, Random rand)
    {
        super.tick(state, level, pos, rand);
        if (level.getBlockEntity(pos) instanceof TickCounterBlockEntity counter)
        {
            long days = counter.getTicksSinceUpdate() / ICalendar.TICKS_IN_DAY;
            int cycles = (int) (days / 5);
            if (cycles >= 1)
            {
                grow(state, level, pos, rand, cycles);
                counter.resetCounter();
            }
        }
    }

    @Override
    public void grow(BlockState state, ServerLevel level, BlockPos pos, Random random, int cyclesLeft)
    {
        FruitTreeBranchBlock body = (FruitTreeBranchBlock) this.body.get();
        BlockPos abovePos = pos.above();
        if (canGrowInto(level, abovePos) && abovePos.getY() < level.getMaxBuildHeight() - 1)
        {
            int stage = state.getValue(STAGE);
            if (stage < 3)
            {
                boolean willGrowUpward = false;
                BlockState belowState = level.getBlockState(pos.below());
                Block belowBlock = belowState.getBlock();
                if (Helpers.isBlock(belowBlock, TFCTags.Blocks.BUSH_PLANTABLE_ON))
                {
                    willGrowUpward = true;
                }
                else if (belowBlock == body)
                {
                    BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
                    int j = 1;
                    for (int k = 0; k < 4; ++k)
                    {
                        mutablePos.setWithOffset(pos, 0, -1 * (j + 1), 0);
                        if (level.getBlockState(mutablePos).getBlock() != body)
                        {
                            break;
                        }
                        ++j;
                    }
                    if (j < 2)
                    {
                        willGrowUpward = true;
                    }
                }
                else if (canGrowInto(level, pos.below()))
                {
                    willGrowUpward = true;
                }

                if (willGrowUpward && allNeighborsEmpty(level, abovePos, null) && canGrowInto(level, pos.above(2)))
                {
                    placeBody(level, pos, stage);
                    placeGrownFlower(level, abovePos, stage, state.getValue(SAPLINGS), cyclesLeft - 1);
                }
                else if (stage < 2)
                {
                    int branches = Math.max(0, state.getValue(SAPLINGS) - stage);
                    BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
                    List<Direction> directions = Direction.Plane.HORIZONTAL.stream().collect(Collectors.toList());
                    while (branches > 0)
                    {
                        Direction test = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                        if (directions.contains(test))
                        {
                            mutablePos.setWithOffset(pos, test);
                            if (canGrowIntoLocations(level, mutablePos, mutablePos.below()) && allNeighborsEmpty(level, mutablePos, test.getOpposite()))
                            {
                                placeGrownFlower(level, mutablePos, stage + 1, state.getValue(SAPLINGS), cyclesLeft - 1);
                            }
                            directions.remove(test);
                            branches--;
                        }
                    }
                    placeBody(level, pos, stage);
                }
                else
                {
                    placeBody(level, pos, stage);
                }
            }
        }
    }
    private void placeGrownFlower(ServerLevel level, BlockPos pos, int stage, int saplings, int cycles)
    {
        level.setBlock(pos, getStateForPlacement(level, pos).setValue(STAGE, stage).setValue(SAPLINGS, saplings), 3);
        if (level.getBlockEntity(pos) instanceof TickCounterBlockEntity counter)
        {
            counter.resetCounter();
            counter.reduceCounter(-1L * ICalendar.TICKS_IN_DAY * cycles * 5);
        }
        addLeaves(level, pos);
        level.getBlockState(pos).randomTick(level, pos, level.random);
    }

    private void placeBody(LevelAccessor level, BlockPos pos, int stage)
    {
        FruitTreeBranchBlock plant = (FruitTreeBranchBlock) this.body.get();
        level.setBlock(pos, plant.getStateForPlacement(level, pos).setValue(STAGE, stage), 3);
        addLeaves(level, pos);
    }

    private void addLeaves(LevelAccessor level, BlockPos pos)
    {
        final BlockState leaves = this.leaves.get().defaultBlockState();
        BlockState downState = level.getBlockState(pos.below(2));
        if (!(downState.isAir() || Helpers.isBlock(downState, TFCTags.Blocks.FRUIT_TREE_LEAVES) || Helpers.isBlock(downState, TFCTags.Blocks.FRUIT_TREE_BRANCH)))
        {
            return;
        }
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (Direction d : NOT_DOWN)
        {
            mutablePos.setWithOffset(pos, d);
            if (level.isEmptyBlock(mutablePos))
            {
                level.setBlock(mutablePos, leaves, 2);
            }
        }
    }


}