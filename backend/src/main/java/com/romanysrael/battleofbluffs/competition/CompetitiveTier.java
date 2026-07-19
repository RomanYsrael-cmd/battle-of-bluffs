package com.romanysrael.battleofbluffs.competition;

public enum CompetitiveTier {
    CADET("Cadet", Integer.MIN_VALUE, 900),
    PRIVATE("Private", 900, 1100),
    SERGEANT("Sergeant", 1100, 1300),
    LIEUTENANT("Lieutenant", 1300, 1500),
    CAPTAIN("Captain", 1500, 1700),
    COLONEL("Colonel", 1700, 1900),
    GENERAL("General", 1900, 2100),
    GRAND_GENERAL("Grand General", 2100, Integer.MAX_VALUE);

    private final String label;
    private final int minimum;
    private final int nextMinimum;

    CompetitiveTier(String label, int minimum, int nextMinimum) {
        this.label = label;
        this.minimum = minimum;
        this.nextMinimum = nextMinimum;
    }

    public String label() {
        return label;
    }

    public int minimum() {
        return minimum;
    }

    public int nextMinimum() {
        return nextMinimum;
    }

    public static CompetitiveTier forRating(int rating) {
        for (CompetitiveTier tier : values()) {
            if (rating < tier.nextMinimum) {
                return tier;
            }
        }
        return GRAND_GENERAL;
    }
}
