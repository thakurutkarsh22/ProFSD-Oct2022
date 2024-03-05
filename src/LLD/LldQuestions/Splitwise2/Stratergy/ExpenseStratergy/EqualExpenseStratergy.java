package LLD.LldQuestions.Splitwise2.Stratergy.ExpenseStratergy;

import LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy.EqualSplitStratergy;
import LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy.SplitStratergy;
import LLD.LldQuestions.Splitwise2.models.ExpenseMetaData;
import LLD.LldQuestions.Splitwise2.models.User;

import java.util.List;

public class EqualExpenseStratergy extends Expense {
    public EqualExpenseStratergy(double amount, User paidBy, List<SplitStratergy> splits, ExpenseMetaData metaData) {
        super(amount, paidBy, splits, metaData);
    }

    @Override
    public boolean validate() {
        for(SplitStratergy split : getSplits()) {
            if(!(split instanceof EqualSplitStratergy)) {
                return false;
            }
        }

        return true;
    }
}
