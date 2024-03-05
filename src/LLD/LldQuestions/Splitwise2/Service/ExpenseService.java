package LLD.LldQuestions.Splitwise2.Service;

import LLD.LldQuestions.Splitwise2.Stratergy.ExpenseStratergy.EqualExpenseStratergy;
import LLD.LldQuestions.Splitwise2.Stratergy.ExpenseStratergy.ExactExpenseStratergy;
import LLD.LldQuestions.Splitwise2.Stratergy.ExpenseStratergy.Expense;
import LLD.LldQuestions.Splitwise2.Stratergy.ExpenseStratergy.PercentageExpenseStratergy;
import LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy.PercentageSplitStratergy;
import LLD.LldQuestions.Splitwise2.Stratergy.SplitStratergy.SplitStratergy;
import LLD.LldQuestions.Splitwise2.models.ExpenseMetaData;
import LLD.LldQuestions.Splitwise2.models.ExpenseType;
import LLD.LldQuestions.Splitwise2.models.User;

import java.util.List;

public class ExpenseService {
    public  static Expense CreateExpense(ExpenseType expenseType, double amount, User paidBy,
                                         List<SplitStratergy> splits, ExpenseMetaData metaData) {

        switch (expenseType) {
            case EXACT:
                return new ExactExpenseStratergy(amount, paidBy, splits, metaData);
            case EQUAL:
                int totalSplits = splits.size();
                double splitAMount = ((double)Math.round(amount*100/totalSplits))/100.0;
                for(SplitStratergy split: splits){
                    split.setAmount(splitAMount);
                }

////                setting amount for the main spender
//                splits.get(0).setAmount();
                return new EqualExpenseStratergy(amount, paidBy, splits, metaData);
            case PERCENT:
                for(SplitStratergy split: splits) {
                    PercentageSplitStratergy percentageSplit = (PercentageSplitStratergy) split;
                    percentageSplit.setAmount((amount*percentageSplit.getPercentage())/100.0);
                }

                return new PercentageExpenseStratergy(amount, paidBy, splits, metaData);

            default:
                return null;
        }

    }
}
