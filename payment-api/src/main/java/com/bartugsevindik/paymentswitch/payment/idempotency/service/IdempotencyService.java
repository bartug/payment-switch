/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.idempotency.service;

import com.bartugsevindik.paymentswitch.payment.idempotency.dto.IdempotencyContext;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * <h1>IdempotencyService</h1>
 * <p>Idempotency-Key kayıtlarının oluşturulması, sorgulanması ve temizlenmesi.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
@Service
public interface IdempotencyService {

    /**
     * <h1>İstek Context'i Oluşturma</h1>
     * <p>İstek gövdesinin HMAC-SHA256 değerini hesaplar. Alan sırası ve {@code 10.50 / 10.5} gibi
     * gösterim farkları hash'i değiştirmez.</p>
     *
     * @param merchantId     Üye işyeri numarası
     * @param idempotencyKey Client'ın gönderdiği key
     * @param request        İstek gövdesi
     * @return Idempotency context
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-2
     */
    IdempotencyContext createContext(String merchantId, String idempotencyKey, Object request);

    /**
     * <h1>Önceki Kaynağı Bulma</h1>
     * <p>Key daha önce kullanıldıysa oluşan kaynağın ID'sini döndürür. Key farklı bir body ile
     * kullanıldıysa {@code IdempotencyKeyReuseException} fırlatılır.</p>
     *
     * @param context Idempotency context
     * @return Daha önce oluşan kaynağın ID'si
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-2
     */
    Optional<String> findExistingResourceId(IdempotencyContext context);

    /**
     * <h1>Kayıt Oluşturma</h1>
     * <p>Kaynakla <b>aynı transaction</b> içinde çağrılmalıdır. Aynı key ile eş zamanlı ikinci bir kayıt
     * {@code DataIntegrityViolationException} ile sonuçlanır.</p>
     *
     * @param context      Idempotency context
     * @param resourceType Kaynak tipi
     * @param resourceId   Kaynak ID
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-2
     */
    void saveRecord(IdempotencyContext context, String resourceType, String resourceId);

    /**
     * <h1>Süresi Dolan Kayıtları Silme</h1>
     *
     * @return Silinen kayıt sayısı
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-2
     */
    int deleteExpiredRecords();
}
