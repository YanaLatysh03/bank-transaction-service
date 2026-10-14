package com.latysh.bank.transactionservice.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Locale;

@Schema(description = "Категория расхода: product — товары, service — услуги", example = "product")
public enum ExpenseCategory {
    PRODUCT,
    SERVICE;

    @JsonValue
    public String jsonValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ExpenseCategory fromJson(String value) {
        if (value == null) {
            return null;
        }
        for (ExpenseCategory category : values()) {
            if (category.jsonValue().equals(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown expense category: " + value);
    }
}
