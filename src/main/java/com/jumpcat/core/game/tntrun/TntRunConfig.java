package com.jumpcat.core.game.tntrun;

public class TntRunConfig {
    public String templateWorld = "tnt_template_1";
    public int rounds = 3;
    public int graceSeconds = 5;
    public int eliminationY = 0;
    public int decayDelayTicks = 10; // ~0.5s
    public int decayFootprintRadius = 1; // include diagonals around foot

    // Scoring
    public int pointsWinPerPlayer = 200;
    public int pointsSurvivalDrip = 7;
    public int bonus50 = 25;
    public int bonus25 = 50;
    public int bonus10 = 75;

    // Double jump
    public int blocksPerCharge = 100;
    public int doubleJumpCooldownTicks = 4; // 0.2s
    public double jumpVerticalStrong = 1.2; // ~15 blocks vertical feel
    public double jumpHorizontalStrong = 0.9; // ~10 blocks horizontal feel
}
