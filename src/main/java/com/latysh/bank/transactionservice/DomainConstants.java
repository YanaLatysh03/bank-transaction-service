package com.latysh.bank.transactionservice;

import java.math.BigDecimal;
import java.time.ZoneId;

public final class DomainConstants {
    public static final String USD = "USD";
    public static final BigDecimal DEFAULT_LIMIT = new BigDecimal("1000.00");
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Minsk");

    private DomainConstants() {
    }
}
