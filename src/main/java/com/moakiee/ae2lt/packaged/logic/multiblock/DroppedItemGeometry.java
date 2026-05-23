package com.moakiee.ae2lt.packaged.logic.multiblock;

final class DroppedItemGeometry {

    private static final double AA_BEAM_INFLATE = 0.02;

    private DroppedItemGeometry() {
    }

    static Vec3d dropPosition(BlockPos3 origin, AxisDirection facing) {
        return centerOf(relative(origin, facing, 1));
    }

    static BlockPos3 inputDropBlock(BlockPos3 origin, AxisDirection facing) {
        return relative(origin, facing, 1);
    }

    static Aabb inputDropAabb(BlockPos3 origin, AxisDirection facing) {
        var pos = inputDropBlock(origin, facing);
        return new Aabb(
                pos.x(),
                pos.y(),
                pos.z(),
                pos.x() + 1,
                pos.y() + 1,
                pos.z() + 1)
                .inflate(AA_BEAM_INFLATE);
    }

    static Aabb beamAabb(BlockPos3 origin, BlockPos3 hitBlock, AxisDirection facing) {
        return new Aabb(
                Math.min(origin.x(), hitBlock.x() + 1),
                Math.min(origin.y(), hitBlock.y() + 1),
                Math.min(origin.z(), hitBlock.z() + 1),
                Math.max(origin.x(), hitBlock.x() + 1),
                Math.max(origin.y(), hitBlock.y() + 1),
                Math.max(origin.z(), hitBlock.z() + 1))
                .inflate(AA_BEAM_INFLATE)
                .expandTowards(facing.dx(), facing.dy(), facing.dz());
    }

    static BlockPos3 relative(BlockPos3 origin, AxisDirection facing, int distance) {
        return new BlockPos3(
                origin.x() + facing.dx() * distance,
                origin.y() + facing.dy() * distance,
                origin.z() + facing.dz() * distance);
    }

    static Vec3d centerOf(BlockPos3 pos) {
        return new Vec3d(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
    }

    enum AxisDirection {
        DOWN(0, -1, 0),
        UP(0, 1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        WEST(-1, 0, 0),
        EAST(1, 0, 0);

        private final int dx;
        private final int dy;
        private final int dz;

        AxisDirection(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        int dx() {
            return dx;
        }

        int dy() {
            return dy;
        }

        int dz() {
            return dz;
        }
    }

    record BlockPos3(int x, int y, int z) {
    }

    record Vec3d(double x, double y, double z) {
    }

    record Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        Aabb inflate(double amount) {
            return new Aabb(minX - amount, minY - amount, minZ - amount,
                    maxX + amount, maxY + amount, maxZ + amount);
        }

        Aabb expandTowards(int dx, int dy, int dz) {
            return new Aabb(
                    dx < 0 ? minX + dx : minX,
                    dy < 0 ? minY + dy : minY,
                    dz < 0 ? minZ + dz : minZ,
                    dx > 0 ? maxX + dx : maxX,
                    dy > 0 ? maxY + dy : maxY,
                    dz > 0 ? maxZ + dz : maxZ);
        }

        boolean contains(Vec3d vec) {
            return vec.x() >= minX && vec.x() < maxX
                    && vec.y() >= minY && vec.y() < maxY
                    && vec.z() >= minZ && vec.z() < maxZ;
        }
    }
}
