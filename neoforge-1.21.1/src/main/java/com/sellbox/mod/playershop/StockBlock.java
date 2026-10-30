package com.sellbox.mod.playershop;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class StockBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public StockBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(StockBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (level.isClientSide) return true;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof StockBlockEntity stock) {
            UUID owner = stock.getOwner();
            if (owner != null && !owner.equals(player.getUUID())) {
                if (!player.isCreative()) {
                    if (player instanceof ServerPlayer sp) {
                        sp.sendSystemMessage(Component.translatable("message.sellbox.not_owner_break", stock.getOwnerName()));
                    }
                    return false;
                }
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StockBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof StockBlockEntity stock)) return ItemInteractionResult.SUCCESS;

        if (stock.getOwner() == null) {
            stock.setOwner(player.getUUID());
            stock.setOwnerName(player.getName().getString());
            stock.setChanged();
        }

        // 非 owner 玩家所有交互禁止，无 creative 豁免；提示归属
        if (!stock.getOwner().equals(player.getUUID())) {
            String ownerName = stock.getOwnerName();
            if (ownerName == null || ownerName.isEmpty()) {
                ownerName = stock.getOwner().toString().substring(0, 8);
            }
            player.displayClientMessage(
                    Component.translatable("message.sellbox.not_owner_stock")
                            .append(Component.literal(" "))
                            .append(Component.translatable("message.sellbox.owner_is", ownerName)),
                    true);
            return ItemInteractionResult.FAIL;
        }

        level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);

        SimpleMenuProvider provider = new SimpleMenuProvider(
                (id, inv, p) -> new StockMenu(id, inv, stock),
                stock.getDisplayName()
        );
        ((ServerPlayer) player).openMenu(provider, pos);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Container container) {
                Containers.dropContents(level, pos, container);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}
