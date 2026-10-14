package com.latysh.bank.transactionservice.service;

import com.latysh.bank.transactionservice.exception.RateNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ExchangeRateService {

    /**
     * Переводит сумму в USD на указанный день.
     * Курс USD к USD равен {@code 1}. Для остальных валют он ищется по цепочке:
     * кэш за день, строка в базе, провайдер, последнее закрытие в базе.
     * Сумма округляется до двух знаков ({@code HALF_UP}).
     *
     * @param currency код валюты ISO в верхнем регистре
     * @param sum сумма в валюте операции
     * @param day календарная дата курса
     * @return сумма в USD
     * @throws RateNotFoundException если курса нет ни за этот день, ни среди прошлых закрытий
     */
    BigDecimal toUsd(String currency, BigDecimal sum, LocalDate day);
}
