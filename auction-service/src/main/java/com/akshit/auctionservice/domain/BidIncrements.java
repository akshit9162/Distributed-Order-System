package com.akshit.auctionservice.domain;

import java.math.BigDecimal;

/**
 * The standard online-auction bid increment table: the smallest amount a new bid must beat the current price by, which depends on
 * how high the price already is. The same table is used to work out the price a proxy bid pushes the auction to.
 */
public final class BidIncrements {

    private static final BigDecimal[][] TABLE = {
            // price below          increment
            {new BigDecimal("1.00"), new BigDecimal("0.05")},
            {new BigDecimal("5.00"), new BigDecimal("0.25")},
            {new BigDecimal("25.00"), new BigDecimal("0.50")},
            {new BigDecimal("100.00"), new BigDecimal("1.00")},
            {new BigDecimal("250.00"), new BigDecimal("2.50")},
            {new BigDecimal("500.00"), new BigDecimal("5.00")},
            {new BigDecimal("1000.00"), new BigDecimal("10.00")},
            {new BigDecimal("2500.00"), new BigDecimal("25.00")},
            {new BigDecimal("5000.00"), new BigDecimal("50.00")},
    };
    private static final BigDecimal TOP = new BigDecimal("100.00");

    private BidIncrements() {
    }

    public static BigDecimal at(BigDecimal price) {
        for (BigDecimal[] row : TABLE) {
            if (price.compareTo(row[0]) < 0) return row[1];
        }
        return TOP;
    }
}
