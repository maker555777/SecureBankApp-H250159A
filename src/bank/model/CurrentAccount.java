package bank.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Current: day-to-day account that may dip into a small fixed overdraft. */
public class CurrentAccount extends Account {

    public static final BigDecimal OVERDRAFT_LIMIT = new BigDecimal("200.00");

    public CurrentAccount(String accountNumber, String ownerUsername) {
        super(accountNumber, ownerUsername);
    }

    public CurrentAccount(String accountNumber, String ownerUsername,
                          BigDecimal balance, LocalDate openedOn) {
        super(accountNumber, ownerUsername, balance, openedOn);
    }

    @Override
    public AccountType getType() {
        return AccountType.CURRENT;
    }

    @Override
    public BigDecimal maxWithdrawable() {
        return getBalance().add(OVERDRAFT_LIMIT);
    }
}
