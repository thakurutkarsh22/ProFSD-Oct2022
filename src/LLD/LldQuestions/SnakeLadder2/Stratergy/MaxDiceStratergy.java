package LLD.LldQuestions.SnakeLadder2.Stratergy;

public class MaxDiceStratergy extends Dice {
    public MaxDiceStratergy(int diceCount) {
        super(diceCount);
    }

    @Override
    public int rollDice() {
        int diceCount = this.diceCount;
        int[] dicesValueIGetAfterDicing = new int[diceCount];
        int maxValue = Integer.MAX_VALUE;
        for (int i = 0; i < diceCount; i++) {
            dicesValueIGetAfterDicing[i] = this.getRollDiceValue();
            maxValue = Math.max(maxValue, dicesValueIGetAfterDicing[i]);
        }
        return maxValue;
    }
}
