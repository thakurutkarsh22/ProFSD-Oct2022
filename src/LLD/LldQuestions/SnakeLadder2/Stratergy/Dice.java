package LLD.LldQuestions.SnakeLadder2.Stratergy;

import java.util.concurrent.ThreadLocalRandom;

public abstract class Dice {
    public static int min = 1;
    public static int max = 6;
    int diceCount = 0; // storing total no. of dices

    public Dice(int diceCount) {
        this.diceCount = diceCount;
    }


//    Acutally roalling the Single dice and getting the value

    public int getRollDiceValue() {
        int rollDiceValue = ThreadLocalRandom.current().nextInt(min, max + 1);
        return rollDiceValue;
    }


//    Rolling dice and interpreting the value
    public abstract int rollDice();
}
