package io.rover.model;

import java.sql.Statement;

/**
 * Created by ata_n on 2016-06-16.
 */
public class PointsUnit extends Unit {
    public PointsUnit(Double value) {
        super(value);
    }

    public static PointsUnit ZeroUnit = new PointsUnit(0.0);
}
