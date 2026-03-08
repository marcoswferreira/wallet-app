package com.financial.wallet.domain;

/**
 * Represents the direction of a ledger entry.
 * CREDIT = money entering the account (balance increases).
 * DEBIT = money leaving the account (balance decreases).
 */
public enum TransactionType {
    CREDIT,
    DEBIT
}
