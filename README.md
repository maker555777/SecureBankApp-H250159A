# Secure Banking Application

## Description

This is a small console banking application I built in Java for my ISS 2101 mini-project. A customer can register, sign in, open an account, make deposits and withdrawals, and check balances and recent transactions. I used text files to save the data between runs, and tried to apply the object-oriented and secure-coding ideas covered in the course.

## Student Details

| | |
|---|---|
| **Name** | MARE Takudzwa |
| **Registration Number** | H250159A |
| **Module** | ISS 2101 Mini-Project |
| **Institution** | Harare Institute of Technology |

## Features

- Customers can register and sign in. Passwords are stored as salted PBKDF2 hashes, not as plain text.
- Three failed sign-in attempts lock an account. The lock is saved, so restarting the program does not clear it.
- Each customer can have up to five Savings or Current accounts.
- Customers can deposit and withdraw. Withdrawals ask for confirmation, and completed transactions get a reference number.
- The application keeps the ten most recent transactions for each account.
- User, account and transaction data is saved in text files and loaded at start-up.
- Sign-ins, failed attempts, lockouts and transactions are written to `data/audit.log`. Passwords and full account numbers are left out.
- A signed-in session expires after two minutes without activity.
- Errors are shown as short messages rather than stack traces.
- `bank.SelfTest` runs 82 checks without needing an external test library. See [Testing](#testing).

## Requirements

- Java 17 or newer (developed and tested on Java 21)
- No external libraries and no database

## How to build and run

Run these commands from the project folder. The application looks for its `data` folder relative to the current directory.

**Linux / macOS**

```bash
mkdir -p out
javac -d out $(find src -name "*.java")
java -cp out bank.Main
```

**Windows (Command Prompt)**

```bat
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt
java -cp out bank.Main
```

If the box-drawing characters do not display properly in your terminal, add `--ascii` to the `java` command to use plain characters instead.

To keep your own test data out of the repository, point the program at another folder:

```bash
java -Dbank.data=/tmp/mybank -cp out bank.Main
```

## Demo account

There is a demo customer with two accounts in `data`, so you can try the application without registering first.

| | |
|---|---|
| Username | `demo_user` |
| Password | `Demo12345` |

These are sample accounts, not real money. Account numbers are masked on screen (for example, `******6686`) and displayed in full only when an account is created.

## Using the application

Once signed in, you can:

1. Open a new account (Savings or Current)
2. My accounts
3. Check balance
4. Deposit
5. Withdraw
6. Recent transactions
7. Sign out

Account rules:

| Type | Rule |
|---|---|
| Savings | Must always keep a minimum balance of $10.00 |
| Current | May go into an overdraft of up to $200.00 |
| Both | Amounts must be positive, with at most 2 decimal places and no more than $1,000,000.00 per transaction |

### Unlocking a locked account

There is no automatic unlock. To reset a locked user, open `data/users.txt`, find the user's line and change its last two fields to `0|false`, for example:

```
demo_user|<salt>|<hash>|0|false
```

## Project structure

```
SecureBankApp-H250159A/
├── README.md
├── TEST_RESULTS.txt          output of the last self-test run
├── data/
│   ├── users.txt             username | salt | passwordHash | failedAttempts | locked
│   ├── accounts.txt          type | accountNumber | owner | balance | openedOn
│   └── transactions.txt      id | accountNumber | type | amount | balanceAfter | timestamp
└── src/bank/
    ├── Main.java             starts the program
    ├── SelfTest.java         automated checks
    ├── model/                Account, SavingsAccount, CurrentAccount, Transaction, User, ...
    ├── exception/            the application's own exceptions
    ├── security/             PasswordHasher, InputValidator, AuditLogger
    ├── storage/              DataStore (reads and writes the text files)
    ├── service/              AuthService, BankService (the business rules)
    └── ui/                   ConsoleUI and the classes that draw the screens
```

The code is split into layers. The `ui` package calls the `service` classes, which handle the banking rules and work with the model, storage and security code. This keeps those rules out of the menus.

## Object-oriented design

| Concept | Where it is used |
|---|---|
| Abstraction and inheritance | `Account` is abstract. `SavingsAccount` and `CurrentAccount` extend it. |
| Polymorphism | Each account type defines `maxWithdrawable()`. `Account.withdraw()` calls it without knowing which type it has. |
| Encapsulation | Balances are private and there is no `setBalance()`. Money moves only through `deposit()` and `withdraw()`. `Transaction` is immutable. |
| Interface | `Storable` lets `User`, `Account` and `Transaction` each turn themselves into one line of a data file. |
| Custom exceptions | `BankException` and its subclasses (`InvalidInputException`, `InsufficientFundsException`, `AuthenticationException`, `StorageException`, `InvalidRecordException`). |
| Template method | `Account.withdraw()` is `final`, so a subclass can change the limit but can never skip the checks. |

## Security measures

| Measure | How it is done |
|---|---|
| Password storage | PBKDF2-HMAC-SHA256, 600,000 iterations, a random 16-byte salt per user. Plain passwords are never saved. |
| Password handling in memory | Passwords are read and passed as `char[]` and overwritten with zeros immediately after use. Typing is hidden in a real terminal. |
| Safe comparison | Hashes are compared in constant time. |
| No username guessing | An unknown username and a wrong password give the same message and take about the same time. |
| Brute-force protection | Account lockout after 3 failed attempts, saved to disk so a restart cannot reset it. |
| Input validation | Allow-lists for usernames, passwords, amounts, account numbers and menu choices, all in `InputValidator`. A username cannot contain `\|` or a line break, so it cannot damage the data files. |
| Money handling | `BigDecimal` instead of `double`, so there are no rounding errors. |
| Ownership checks | Every account operation first confirms the account belongs to the signed-in user. "Not yours" and "does not exist" give the same message. |
| Account numbers | Random, with a Luhn check digit so typing mistakes are caught. Masked on screen except once at creation. |
| Safe saving | Data files are written to a temporary file and renamed into place, so a crash cannot leave a half-written file. A change is applied to a copy of the account and only kept once it has been saved. |
| Tamper and damage detection | A damaged or duplicated record stops the program from starting and names the file and line. It does not quietly skip the record. |
| File permissions | On Linux and macOS the data files are created readable by their owner only (`rw-------`). |
| Audit trail | Security events are logged with line breaks and `\|` removed, so a crafted username cannot forge a log entry. |
| Error handling | Users see short messages. Internal details and stack traces are never shown. |
| Session timeout | Sessions end after 2 minutes of inactivity. |

## Testing

I included a small test program that has no external dependencies. Run it from the project folder:

```bash
java -cp out bank.SelfTest
```

The self-test runs 82 checks in a temporary folder, leaving the real data files alone. It exits with code 0 if they all pass. The checks cover account rules, file records, password hashing, input validation, the audit log, registration and login, lockout, ownership checks and a simulated disk failure. The last run's output is in [`TEST_RESULTS.txt`](TEST_RESULTS.txt).

I also tried the tests against a scratch copy with three rules broken: the savings minimum, the ownership check and account lockout. The tests reported 12 failures.

For a quick manual check, register and sign in, open a Savings account, deposit $500, then try to withdraw $495. That withdrawal should be refused because of the $10 minimum. Restart the program and check that the balance was saved.

## Known limitations

- The data files are not encrypted. Passwords are stored as hashes, but anyone who can read the files can see account balances. Owner-only file permissions help on Linux and macOS, but are not applied on Windows.
- The application checks that records are well-formed, but it cannot tell if someone edits a balance and leaves the record valid. Detecting that kind of change would need a signature or a different storage design.
- The balance and transaction files are not updated as one operation. If the program stops between saving a balance and saving its transaction, the files can get out of sync. A database transaction would avoid that problem.
- The idle timeout is noticed when the user next presses a key. A console program cannot interrupt a read that is already waiting for input.
- The application is intended for one user at a time. Multiple copies using the same data folder are not coordinated.
- There is no password reset or automatic account unlock. A locked account has to be reset manually as described above.
- The sign-in message says when an account is locked, which confirms that the username exists. I kept that message so a customer knows why they cannot sign in.
