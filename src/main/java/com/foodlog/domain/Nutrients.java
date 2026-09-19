package com.foodlog.domain;

public record Nutrients(double kcal, double proteinG, double fatG, double carbG,
                        double fiberG, double sugarG, double saltG) {

    public static final Nutrients ZERO = new Nutrients(0, 0, 0, 0, 0, 0, 0);

    public Nutrients scale(double factor) {
        return new Nutrients(kcal * factor, proteinG * factor, fatG * factor, carbG * factor,
                fiberG * factor, sugarG * factor, saltG * factor);
    }

    public Nutrients plus(Nutrients o) {
        return new Nutrients(kcal + o.kcal, proteinG + o.proteinG, fatG + o.fatG, carbG + o.carbG,
                fiberG + o.fiberG, sugarG + o.sugarG, saltG + o.saltG);
    }

    public Nutrients rounded() {
        return new Nutrients(round1(kcal), round1(proteinG), round1(fatG), round1(carbG),
                round1(fiberG), round1(sugarG), round1(saltG));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
