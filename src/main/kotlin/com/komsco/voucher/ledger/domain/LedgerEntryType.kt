package com.komsco.voucher.ledger.domain

enum class LedgerEntryType {
    PURCHASE, REDEMPTION, REFUND, WITHDRAWAL, EXPIRY, SETTLEMENT, CANCELLATION, MANUAL_ADJUSTMENT
}

enum class LedgerEntrySide {
    DEBIT, CREDIT
}
