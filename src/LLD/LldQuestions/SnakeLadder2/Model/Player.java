package LLD.LldQuestions.SnakeLadder2.Model;

public class Player {
    String id;
    public String playerName;
    public int playerPosition;

    public Player(String id, String playerName, int playerPosition) {
        this.id = id;
        this.playerName = playerName;
        this.playerPosition = playerPosition;
    }

    public void setPlayerPosition(int position) {
        this.playerPosition = position;
    }
}
