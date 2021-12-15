package backend.entities;

import java.util.Objects;

public class Position {

    private Coordinate coordinate;
    private StoneState stoneState;//white, black or not set as defined in the enum


    public Position(int x, int y) {
        this(new Coordinate(x,y));
    }
    public Position(Coordinate coordinate) {
    this(coordinate, StoneState.NONE);
    }

    public Position(Coordinate coordinate, StoneState stoneState) {
        this.coordinate = coordinate;
        this.stoneState = stoneState;
    }

    public StoneState getStoneState() {
        return stoneState;
    }

    public void setStoneState(StoneState stoneState) {
        this.stoneState = stoneState;
    }

    public Coordinate getCoordinate() {
        return coordinate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position = (Position) o;
        return getCoordinate().equals(position.getCoordinate());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCoordinate());
    }

    @Override
    public String toString() {
        return "Position{" +
                "coordinate=" + coordinate +
                ", stoneState=" + stoneState +
                '}';
    }
}
