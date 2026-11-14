package dev.yharnamdice.physics;

public record ImpactEvent(int diceId, Type type, double intensity) {
    public enum Type { TABLE, RIM, DICE }
}
