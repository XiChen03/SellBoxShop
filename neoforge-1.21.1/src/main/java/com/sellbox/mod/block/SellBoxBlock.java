package com.sellbox.mod.block;

import com.mojang.serialization.MapCodec;
import com.sellbox.mod.gui.SellBoxMenu;
import com.sellbox.mod.registry.ModBlockEntities;
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

public class SellBoxBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public SellBoxBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(SellBoxBlock::new);
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
        if (be instanceof SellBoxBlockEntity sellBox) {
            UUID owner = sellBox.getOwner();
            if (owner != null && !owner.equals(player.getUUID())) {
                if (!(player.isCreative() && player.hasPermissions(2))) {
                    if (player instanceof ServerPlayer sp) {
                        sp.sendSystemMessage(Component.translatable("message.sellbox.not_owner_break", sellBox.getOwnerName()));
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
        return new SellBoxBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SellBoxBlockEntity sellBox)) return ItemInteractionResult.SUCCESS;

        // Shift+右键：本侧无任何功能（连归属绑定都不做）。
        // 「创造+OP2 快捷打开定价界面」放在客户端事件里处理（见 client/ClientBlockShortcuts），
        // 这里绝对不能引用 Minecraft / PricingScreen 等客户端类，否则专用服务器加载本类就会失败
        if (player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (sellBox.getOwner() == null) {
            sellBox.setOwner(player.getUUID());
            sellBox.setOwnerName(player.getName().getString());
            sellBox.setChanged();
        }

        // 非 owner 玩家禁止操作，物品栏上方提示归属
        if (!sellBox.getOwner().equals(player.getUUID())) {
            String ownerName = sellBox.getOwnerName();
            if (ownerName == null || ownerName.isEmpty()) {
                ownerName = sellBox.getOwner().toString().substring(0, 8);
            }
            player.displayClientMessage(Component.translatable("message.sellbox.owner_is", ownerName), true);
            return ItemInteractionResult.FAIL;
        }

        level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);

        SimpleMenuProvider provider = new SimpleMenuProvider(
                (id, inv, p) -> new SellBoxMenu(id, inv, sellBox),
                sellBox.getDisplayName()
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
