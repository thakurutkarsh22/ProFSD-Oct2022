package LLD.LldQuestions.Splitwise2.Stratergy.ExpenseStratergy;

import LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy.PercentageSplitStratergy;
import LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy.SplitStratergy;
import LLD.LldQuestions.Splitwise2.models.ExpenseMetaData;
import LLD.LldQuestions.Splitwise2.models.User;

import java.util.List;

public class PercentageExpenseStratergy extends Expense {
    public PercentageExpenseStratergy(double amount, User paidBy, List<SplitStratergy> splits, ExpenseMetaData metaData) {
        super(amount, paidBy, splits, metaData);
    }

    @Override
    public boolean validate() {
        double totalPercentage = 100;
        double sumSplitPercentage = 0;

        for(SplitStratergy split: this.getSplits()) {
            if(!(split instanceof PercentageSplitStratergy)) {
                return false;
            }

            PercentageSplitStratergy exactSplit = (PercentageSplitStratergy) split;
            sumSplitPercentage += exactSplit.getAmount();
        }

        if(totalPercentage != sumSplitPercentage) return false;

        return true;
    }
}
