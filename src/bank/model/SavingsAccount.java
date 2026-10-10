package bank.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Savings: must always keep a minimum balance, never goes overdrawn. */
public class SavingsAccount extends Account {

    public static final BigDecimal MINIMUM_BALANCE = new BigDecimal("10.00");

    public SavingsAccount(String accountNumber, String ownerUsername) {
        super(accountNumber, ownerUsername);
    }

    public SavingsAccount(String accountNumber, String ownerUsername,
                          BigDecimal balance, LocalDate openedOn) {
        super(accountNumber, ownerUsername, balance, openedOn);
    }

    @Override
    public AccountType getType() {
        return AccountType.SAVINGS;
    }

    @Override
    public BigDecimal maxWithdrawable() {
        BigDecimal available = getBalance().subtract(MINIMUM_BALANCE);
        return available.signum() > 0 ? available : BigDecimal.ZERO.setScale(2);
    }
}
