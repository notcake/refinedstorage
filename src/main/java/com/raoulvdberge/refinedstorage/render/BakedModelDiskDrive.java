package com.raoulvdberge.refinedstorage.render;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.raoulvdberge.refinedstorage.block.BlockBase;
import com.raoulvdberge.refinedstorage.block.BlockDiskDrive;
import com.raoulvdberge.refinedstorage.tile.TileDiskDrive;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nullable;
import javax.vecmath.Vector3f;
import java.util.*;

public class BakedModelDiskDrive implements IBakedModel {
    private class CacheKey {
        private IBlockState state;
        private EnumFacing side;
        private Integer[] diskState;

        CacheKey(IBlockState state, @Nullable EnumFacing side, Integer[] diskState) {
            this.state = state;
            this.side = side;
            this.diskState = diskState;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CacheKey cacheKey = (CacheKey) o;

            if (!state.equals(cacheKey.state)) {
                return false;
            }

            if (side != cacheKey.side) {
                return false;
            }

            return Arrays.equals(diskState, cacheKey.diskState);
        }

        @Override
        public int hashCode() {
            int result = state.hashCode();
            result = 31 * result + (side != null ? side.hashCode() : 0);
            result = 31 * result + Arrays.hashCode(diskState);
            return result;
        }
    }

    private IBakedModel base;
    private Map<EnumFacing, IBakedModel> models = new HashMap<>();
    private Map<EnumFacing, Map<Integer, List<IBakedModel>>> disks = new HashMap<>();

    private LoadingCache<CacheKey, List<BakedQuad>> cache = CacheBuilder.newBuilder().build(new CacheLoader<CacheKey, List<BakedQuad>>() {
        @Override
        public List<BakedQuad> load(CacheKey key) throws Exception {
            EnumFacing facing = key.state.getValue(BlockBase.DIRECTION);

            List<BakedQuad> quads = models.get(facing).getQuads(key.state, key.side, 0);

            for (int i = 0; i < 8; ++i) {
                if (key.diskState[i] != TileDiskDrive.DISK_STATE_NONE) {
                    quads.addAll(disks.get(facing).get(key.diskState[i]).get(i).getQuads(key.state, key.side, 0));
                }
            }

            return quads;
        }
    });

    public BakedModelDiskDrive(IBakedModel base, IBakedModel disk, IBakedModel diskFull, IBakedModel diskDisconnected) {
        this.base = base;

        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            models.put(facing, new BakedModelTRSR(base, facing));

            disks.put(facing, new HashMap<>());

            disks.get(facing).put(TileDiskDrive.DISK_STATE_NORMAL, new ArrayList<>());
            disks.get(facing).put(TileDiskDrive.DISK_STATE_FULL, new ArrayList<>());
            disks.get(facing).put(TileDiskDrive.DISK_STATE_DISCONNECTED, new ArrayList<>());

            initDiskModels(disk, TileDiskDrive.DISK_STATE_NORMAL, facing);
            initDiskModels(diskFull, TileDiskDrive.DISK_STATE_FULL, facing);
            initDiskModels(diskDisconnected, TileDiskDrive.DISK_STATE_DISCONNECTED, facing);
        }
    }

    private void initDiskModels(IBakedModel disk, int type, EnumFacing facing) {
        for (int y = 0; y < 4; ++y) {
            for (int x = 0; x < 2; ++x) {
                BakedModelTRSR model = new BakedModelTRSR(disk, facing);

                Vector3f trans = model.transformation.getTranslation();

                if (facing == EnumFacing.NORTH || facing == EnumFacing.SOUTH) {
                    trans.x += (((float) x * 7F) / 16F) * (facing == EnumFacing.NORTH ? -1 : 1);
                } else if (facing == EnumFacing.EAST || facing == EnumFacing.WEST) {
                    trans.z += (((float) x * 7F) / 16F) * (facing == EnumFacing.EAST ? -1 : 1);
                }

                trans.y -= ((float) y * 3F) / 16F;

                model.transformation = new TRSRTransformation(trans, model.transformation.getLeftRot(), model.transformation.getScale(), model.transformation.getRightRot());

                disks.get(facing).get(type).add(model);
            }
        }
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        if (!(state instanceof IExtendedBlockState)) {
            return base.getQuads(state, side, rand);
        }

        Integer[] diskState = ((IExtendedBlockState) state).getValue(BlockDiskDrive.DISK_STATE);

        if (diskState == null) {
            return base.getQuads(state, side, rand);
        }

        CacheKey key = new CacheKey(((IExtendedBlockState) state).getClean(), side, diskState);

        return cache.getUnchecked(key);
    }

    @Override
    public boolean isAmbientOcclusion() {
        return base.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return base.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return base.isBuiltInRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return base.getParticleTexture();
    }

    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return base.getItemCameraTransforms();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return base.getOverrides();
    }
}
