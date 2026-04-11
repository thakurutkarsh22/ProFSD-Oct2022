package LLD.LldQuestions.SnakeLadder2.Stratergy;

public class SumDiceStratergy extends Dice{
    public SumDiceStratergy(int diceCount) {
        super(diceCount);
    }

    @Override
    public int rollDice() {
        int diceCount = this.diceCount;
        int[] dicesValueIGetAfterDicing = new int[diceCount];
        int value = 0;
        for (int i = 0; i < diceCount; i++) {
            dicesValueIGetAfterDicing[i] = this.getRollDiceValue();
            value += dicesValueIGetAfterDicing[i];
        }
        return value;
    }
}
