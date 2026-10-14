package com.latysh.bank.transactionservice.service;

import com.latysh.bank.transactionservice.rq.CreateLimitRq;
import com.latysh.bank.transactionservice.rq.GetClientDataRq;
import com.latysh.bank.transactionservice.rs.ExceededTransactionRs;
import com.latysh.bank.transactionservice.rs.LimitRs;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ClientLimitService {

    /**
     * Создаёт новый лимит. Уже сохранённые лимиты не изменяются.
     * Дату установления ставит сервер по {@link java.time.Clock}.
     *
     * @param request счёт, категория и сумма лимита в USD
     * @return сохранённый лимит
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    LimitRs createLimit(CreateLimitRq request);

    /**
     * Возвращает историю лимитов счёта, от более новых к более старым.
     *
     * @param accountFrom счёт клиента
     * @return все лимиты счёта; пустой список, если лимитов не было
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    List<LimitRs> findLimits(String accountFrom);

    /**
     * Возвращает операции счёта с {@code limit_exceeded = true}.
     * В каждой строке — лимит, который действовал в момент операции.
     * Если строки лимита не было, сумма равна {@code DEFAULT_LIMIT}, валюта — {@code USD},
     * дата установления пустая.
     *
     * @param accountFrom счёт клиента
     * @return операции, превысившие лимит; пустой список, если таких нет
     */
    List<ExceededTransactionRs> findLimitExceededTransactions(String accountFrom);
}
