package backend.entities;

import java.util.Objects;

public class Player {
    private final String name;
    private final StoneState color;
    private GamePhase phase;
    private int placedStones=0;

    public Player(String name, StoneState color) {
        this.name = name;
        this.phase = GamePhase.PLACE;//initially always place

        if (StoneState.NONE.equals(color) || color == null) {
            throw new IllegalArgumentException("Player color must be white or black");
        }
        this.color=color;
    }

    public String getName() {
        return name;
    }

    public StoneState getColor() {
        return color;
    }

    public GamePhase getPhase() {
        return phase;
    }

    public void setPhase(GamePhase phase) {
        this.phase = phase;
    }

    public int getPlacedStones() {
        return placedStones;
    }

    public void addPlacedStone() {
        this.placedStones++;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return getName().equals(player.getName()) && getColor() == player.getColor();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getColor());
    }

    @Override
    public String toString() {
        return "Player{" +
                "name='" + name + '\'' +
                ", color=" + color +
                ", phase=" + phase +
                ", placedStones=" + placedStones +
                '}';
    }
}
