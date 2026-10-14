package com.latysh.bank.transactionservice.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;


@Converter(autoApply = true)
public class ExpenseCategoryConverter implements AttributeConverter<ExpenseCategory, String> {
    @Override
    public String convertToDatabaseColumn(ExpenseCategory category) {
        return category == null ? null : category.name().toLowerCase();
    }
    @Override
    public ExpenseCategory convertToEntityAttribute(String dbValue) {
        return dbValue == null ? null : ExpenseCategory.valueOf(dbValue.toUpperCase());
    }
}
