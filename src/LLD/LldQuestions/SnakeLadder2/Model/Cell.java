package LLD.LldQuestions.SnakeLadder2.Model;

import LLD.LldQuestions.SnakeLadder2.Model.BoardEntity.Jump;

public class Cell {

//    cell should contain the ID and JUMP (Basically from that cell where can we jump)
    private int id;

    private Jump jump;

    public int getId() {
        return this.id;
    }

    public Jump getJump() {
        return  this.jump;
    }

    public void setJump(Jump jump) {
        this.jump = jump;
    }

    public void setId(int id) {
        this.id = id;
    }

}
