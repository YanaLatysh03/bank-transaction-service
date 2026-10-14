package com.latysh.bank.transactionservice.service;

import com.latysh.bank.transactionservice.exception.RateNotFoundException;
import com.latysh.bank.transactionservice.rq.IncomingTransactionRq;

public interface IntegrationTransactionService {

    /**
     * Принимает расходную операцию.
     * Сумма переводится в USD на календарную дату операции в поясе {@code BUSINESS_ZONE},
     * затем транзакция сохраняется вместе с флагом превышения лимита.
     *
     * @param request входящая операция
     * @return идентификатор сохранённой транзакции
     * @throws RateNotFoundException если курс к USD не найден ни за день операции, ни среди прошлых закрытий
     */
    long accept(IncomingTransactionRq request);
}
