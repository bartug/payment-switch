# Ödeme Akışı

## 1. Genel mimari

```mermaid
flowchart LR
    POS[POS Terminal<br/>fiziki / sanal] -->|POST /v1/payments<br/>Idempotency-Key| API[payment-api]
    API -->|payment + outbox<br/>tek TX| PG[(PostgreSQL)]
    API -.->|outbox relay| K1[[payment.requested]]
    K1 --> RT[routing-service]
    RT -->|BIN + kurallar| RT
    RT --> KQ[[bank.requests.QNB]]
    RT --> KY[[bank.requests.YKB]]
    RT --> KG[[bank.requests.GARANTI]]
    RT --> KI[[bank.requests.ISBANK]]
    KQ & KY & KG & KI --> AD[bank-adapter<br/>banka başına deployment]
    AD --> SIM[bank-simulator]
    AD --> KR[[payment.results]]
    KR --> API
    API -->|webhook| MER[Üye işyeri]
```

## 2. Başarılı satış akışı

```mermaid
sequenceDiagram
    autonumber
    participant POS as POS Terminal
    participant API as payment-api
    participant DB as PostgreSQL
    participant K as Kafka
    participant RT as routing-service
    participant AD as bank-adapter (YKB)
    participant BANK as Banka (simülatör)

    POS->>API: POST /v1/payments (Idempotency-Key: abc-123)
    API->>API: Idempotency kontrolü
    API->>DB: payment(PENDING) + outbox kaydı (tek transaction)
    API-->>POS: 202 Accepted (paymentId)
    DB-->>K: outbox relay → payment.requested
    K->>RT: PaymentRequested
    RT->>RT: BIN 540061 → YKB / World, taksit=3 → on-us zorunlu
    RT->>K: bank.requests.YKB
    K->>AD: BankAuthorizationRequest
    AD->>BANK: Satış isteği
    BANK-->>AD: 00 - Onay, authCode
    AD->>K: payment.results (APPROVED)
    K->>API: PaymentResult
    API->>DB: PENDING → APPROVED
    API->>POS: Webhook / GET /v1/payments/{id}
```

## 3. Kritik senaryo: banka cevap vermedi

```mermaid
sequenceDiagram
    participant AD as bank-adapter
    participant BANK as Banka
    participant K as Kafka

    AD->>BANK: Satış isteği
    Note over AD,BANK: 10 sn timeout, cevap yok
    AD->>K: payment.results (UNKNOWN)
    AD->>BANK: Inquiry (işlem sorgulama, RRN ile)
    alt Banka işlemi bulursa
        BANK-->>AD: Onaylı
        AD->>K: payment.results (APPROVED)
    else Bulamazsa ya da sorgu da cevapsız kalırsa
        AD->>BANK: Reversal (teknik iptal)
        AD->>K: payment.results (REVERSED)
    end
```

Timeout olan satış isteği **asla retry edilmez**. Banka parayı çekmiş olabilir. Retry, çift çekim demektir.
Detaylı akış ve gerçek ortamda denenen senaryolar: [05-banka-entegrasyonu.md](05-banka-entegrasyonu.md)

## 4. Payment state machine

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> ROUTED
    PENDING --> FAILED: routing reddetti
    PENDING --> APPROVED: banka sonucu routing sonucundan önce geldi
    ROUTED --> APPROVED
    ROUTED --> DECLINED
    ROUTED --> UNKNOWN: timeout / 5xx
    ROUTED --> FAILED: bankaya gönderilemedi (circuit açık)
    UNKNOWN --> APPROVED: inquiry onay buldu
    UNKNOWN --> DECLINED: inquiry red buldu
    UNKNOWN --> REVERSED: işlem bulunamadı → reversal
    APPROVED --> VOIDED: gün sonu öncesi iptal
    APPROVED --> REFUNDED: iade
    DECLINED --> [*]
    FAILED --> [*]
    REVERSED --> [*]
```
