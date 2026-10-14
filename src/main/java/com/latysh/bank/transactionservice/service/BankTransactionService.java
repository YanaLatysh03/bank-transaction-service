package com.latysh.bank.transactionservice.service;

import com.latysh.bank.transactionservice.rq.IncomingTransactionRq;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

public interface BankTransactionService {

    /**
     * Сохраняет транзакцию и проставляет {@code limit_exceeded}.
     * Перед расчётом блокирует пару (счёт, категория), чтобы параллельные операции
     * одного счёта и категории не прочитали один и тот же остаток.
     * Действующий лимит — последняя запись с датой не позже операции;
     * если записи нет, берётся {@code DEFAULT_LIMIT}.
     * Расходы суммируются за календарный месяц операции в поясе {@code BUSINESS_ZONE}.
     *
     * @param request входящая операция
     * @param sumUsd сумма операции в USD
     * @return идентификатор сохранённой транзакции
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    long acceptTransaction(IncomingTransactionRq request, BigDecimal sumUsd);
}
