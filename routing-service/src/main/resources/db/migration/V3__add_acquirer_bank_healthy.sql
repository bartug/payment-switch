-- active: operasyonun elle verdiği karar. healthy: bank-adapter'daki circuit breaker'dan otomatik gelir.
ALTER TABLE acquirer_bank ADD COLUMN healthy BOOLEAN NOT NULL DEFAULT TRUE;
