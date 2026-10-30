package com.sellbox.mod.playershop;

import com.mojang.serialization.MapCodec;
import com.sellbox.mod.playershop.network.OpenPlayerShopScreenPayload;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class MarketBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public MarketBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(MarketBlock::new);
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
        if (be instanceof MarketBlockEntity market) {
            UUID owner = market.getOwner();
            if (owner != null && !owner.equals(player.getUUID())) {
                if (!player.isCreative()) {
                    if (player instanceof ServerPlayer sp) {
                        sp.sendSystemMessage(Component.translatable("message.sellbox.not_owner_break", market.getOwnerName()));
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
        return new MarketBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MarketBlockEntity market)) return ItemInteractionResult.SUCCESS;

        if (market.getOwner() == null) {
            market.setOwner(player.getUUID());
            market.setOwnerName(player.getName().getString());
            market.setChanged();
        }

        // 非 owner 玩家所有交互（右键、Shift+右键）全部禁止，无 creative 豁免；提示归属
        if (!market.getOwner().equals(player.getUUID())) {
            String ownerName = market.getOwnerName();
            if (ownerName == null || ownerName.isEmpty()) {
                ownerName = market.getOwner().toString().substring(0, 8);
            }
            player.displayClientMessage(
                    Component.translatable("message.sellbox.not_owner_market")
                            .append(Component.literal(" "))
                            .append(Component.translatable("message.sellbox.owner_is", ownerName)),
                    true);
            return ItemInteractionResult.FAIL;
        }

        if (player.isShiftKeyDown()) {
            level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
            SimpleMenuProvider provider = new SimpleMenuProvider(
                    (id, inv, p) -> new MarketMenu(id, inv, market),
                    market.getDisplayName()
            );
            ((ServerPlayer) player).openMenu(provider, pos);
            return ItemInteractionResult.SUCCESS;
        }

        PacketDistributor.sendToPlayer((ServerPlayer) player, new OpenPlayerShopScreenPayload(pos));
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

