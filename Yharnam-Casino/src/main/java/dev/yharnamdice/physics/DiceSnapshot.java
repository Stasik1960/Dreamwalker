package dev.yharnamdice.physics;

/** Immutable state sent to clients. Coordinates are table-local blocks/metres. */
public record DiceSnapshot(int id, Vec3 position, Quaternion rotation, Vec3 velocity,
                           Vec3 angularVelocity, int settledFace, boolean settled) { }
