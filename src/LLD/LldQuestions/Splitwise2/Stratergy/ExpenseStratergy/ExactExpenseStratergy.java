package LLD.LldQuestions.Splitwise2.Stratergy.ExpenseStratergy;

import LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy.ExactSplitStratergy;
import LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy.SplitStratergy;
import LLD.LldQuestions.Splitwise2.models.ExpenseMetaData;
import LLD.LldQuestions.Splitwise2.models.User;

import java.util.List;

public class ExactExpenseStratergy extends Expense {
    public ExactExpenseStratergy(double amount, User paidBy, List<SplitStratergy> splits, ExpenseMetaData metaData) {
        super(amount, paidBy, splits, metaData);
    }

    @Override
    public boolean validate() {
//        now we validate u1 spends 1000 -> u2 ows u1 -> 370 and U2 ows u1 -> 630.

        double totalExpense = this.getAmount();
        double totalSplitAmount = 0;

        for(SplitStratergy split: this.getSplits()) {
            if(!(split instanceof ExactSplitStratergy)) {
                return false;
            }

            ExactSplitStratergy exactSplit = (ExactSplitStratergy) split;
            totalSplitAmount += exactSplit.getAmount();
        }

        if(totalExpense != totalSplitAmount) return false;

        return true;
    }
}
