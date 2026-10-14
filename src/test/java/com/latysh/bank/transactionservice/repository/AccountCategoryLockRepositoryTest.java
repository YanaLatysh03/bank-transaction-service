package com.latysh.bank.transactionservice.repository;

import com.latysh.bank.transactionservice.entity.AccountCategoryLock;
import com.latysh.bank.transactionservice.entity.AccountCategoryLockId;
import com.latysh.bank.transactionservice.entity.ExpenseCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class AccountCategoryLockRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15");

    @Autowired
    AccountCategoryLockRepository accountCategoryLockRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void insertsLockRowForAccountAndCategory() {
        // when
        accountCategoryLockRepository.insertIfAbsent("0000000123", "product");

        // then
        Optional<AccountCategoryLock> found = accountCategoryLockRepository.findById(
            new AccountCategoryLockId("0000000123", ExpenseCategory.PRODUCT)
        );

        assertThat(found).isPresent();
        AccountCategoryLock actual = found.orElseThrow();
        assertThat(actual.getId().getAccountFrom()).isEqualTo("0000000123");
        assertThat(actual.getId().getExpenseCategory()).isEqualTo(ExpenseCategory.PRODUCT);
    }

    @Test
    void insertsTheSamePairOnlyOnce() {
        // when
        accountCategoryLockRepository.insertIfAbsent("0000000123", "product");
        accountCategoryLockRepository.insertIfAbsent("0000000123", "product");

        // then
        assertThat(accountCategoryLockRepository.count()).isEqualTo(1);
    }

    @Test
    void insertsAnotherCategoryAsASeparateRow() {
        // when
        accountCategoryLockRepository.insertIfAbsent("0000000123", "product");
        accountCategoryLockRepository.insertIfAbsent("0000000123", "service");

        // then
        assertThat(accountCategoryLockRepository.count()).isEqualTo(2);
    }

    @Test
    void locksTheInsertedRow() {
        // given
        accountCategoryLockRepository.insertIfAbsent("0000000123", "product");

        // when
        AccountCategoryLock locked = accountCategoryLockRepository.lock("0000000123", ExpenseCategory.PRODUCT);

        // then
        assertThat(locked).isNotNull();
        assertThat(locked.getId().getAccountFrom()).isEqualTo("0000000123");
        assertThat(locked.getId().getExpenseCategory()).isEqualTo(ExpenseCategory.PRODUCT);
    }

    @Test
    void returnsNullWhenLockRowIsAbsent() {
        // when
        AccountCategoryLock locked = accountCategoryLockRepository.lock("0000000123", ExpenseCategory.PRODUCT);

        // then
        assertThat(locked).isNull();
    }

    @Test
    void doesNotLockAnotherCategory() {
        // given
        accountCategoryLockRepository.insertIfAbsent("0000000123", "product");

        // when
        AccountCategoryLock locked = accountCategoryLockRepository.lock("0000000123", ExpenseCategory.SERVICE);

        // then
        assertThat(locked).isNull();
    }

    @Test
    void rejectsUnknownCategory() {
        // when / then
        assertThatThrownBy(() -> accountCategoryLockRepository.insertIfAbsent("0000000123", "goods"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lockWaitsForTheRowHeldByAnotherTransaction() throws Exception {
        // given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> accountCategoryLockRepository.insertIfAbsent("0000000123", "product"));

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> holderFailure = new AtomicReference<>();

        Thread holder = new Thread(() -> {
            try {
                transaction.executeWithoutResult(status -> {
                    accountCategoryLockRepository.lock("0000000123", ExpenseCategory.PRODUCT);
                    locked.countDown();
                    await(release);
                });
            } catch (Throwable failure) {
                holderFailure.set(failure);
                locked.countDown();
            }
        });

        holder.start();
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(holderFailure.get()).isNull();

        // when / then
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL lock_timeout = '300ms'");
            accountCategoryLockRepository.lock("0000000123", ExpenseCategory.PRODUCT);
        })).isInstanceOf(CannotAcquireLockException.class);

        // then
        release.countDown();
        holder.join(5_000);
        assertThat(holder.isAlive()).isFalse();
        assertThat(holderFailure.get()).isNull();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out while holding the lock");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
