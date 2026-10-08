package org.pokemmo.gameserver.game.trainer;

public enum TrainerLevelType {
    LowTrainer(0, "LowTrainer"),
    AceTrainer(1, "AceTrainer"),
    RivalTrainer(2, "RivalTrainer"),
    GymLeaderTrainer(3, "GymLeaderTrainer"),
    EliteTrainer(4, "EliteTrainer");
    private byte type;
    private String name;
    private static final TrainerLevelType[] allTypeArray = TrainerLevelType.values();
    TrainerLevelType(int type, String name) {
        this.type = (byte) type;
        this.name = name;
    }
    public byte getType() {
        return type;
    }
    public String getName() {
        return name;
    }
    public static TrainerLevelType getByType(int type) {
       return allTypeArray[type];
    }
    public static TrainerLevelType getByName(String name) {
        for (TrainerLevelType trainerType : allTypeArray) {
            if (trainerType.getName().equals(name)) {
                return trainerType;
            }
        }
        return null;
    }
}
