package com.latysh.bank.transactionservice.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Категория расхода: PRODUCT — товары, SERVICE — услуги")
public enum ExpenseCategory {
    PRODUCT,
    SERVICE
}
