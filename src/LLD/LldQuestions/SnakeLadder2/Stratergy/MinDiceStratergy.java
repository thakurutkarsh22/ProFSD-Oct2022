package LLD.LldQuestions.SnakeLadder2.Stratergy;

public class MinDiceStratergy extends  Dice{
    public MinDiceStratergy(int diceCount) {
        super(diceCount);
    }

    @Override
    public int rollDice() {
        int diceCount = this.diceCount;
        int[] dicesValueIGetAfterDicing = new int[diceCount];
        int minValue = Integer.MIN_VALUE;
        for (int i = 0; i < diceCount; i++) {
            dicesValueIGetAfterDicing[i] = this.getRollDiceValue();
            minValue = Math.min(minValue, dicesValueIGetAfterDicing[i]);
        }
        return minValue;
    }
//    If we have more then 2 dice then we would want to get the max one out

}
