package LLD.LldQuestions.TicTacToe.model;

public class Player {

    private final int id;
    private final String name;
    private final String symbol;


    public Player(int id, String name, String symbol) {
        this.id = id;
        this.name = name;
        this.symbol = symbol;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }
}
