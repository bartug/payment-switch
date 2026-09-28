/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.idempotency.service.impl;

import com.bartugsevindik.paymentswitch.payment.idempotency.config.IdempotencyProperties;
import com.bartugsevindik.paymentswitch.payment.idempotency.dto.IdempotencyContext;
import com.bartugsevindik.paymentswitch.payment.idempotency.entity.IdempotencyRecord;
import com.bartugsevindik.paymentswitch.payment.idempotency.exception.IdempotencyKeyReuseException;
import com.bartugsevindik.paymentswitch.payment.idempotency.repository.IdempotencyRecordRepository;
import com.bartugsevindik.paymentswitch.payment.idempotency.service.IdempotencyService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final IdempotencyProperties properties;
    private final ObjectMapper canonicalMapper;
    private final SecretKeySpec hashKey;

    public IdempotencyServiceImpl(IdempotencyRecordRepository idempotencyRecordRepository,
                                  IdempotencyProperties properties) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.properties = properties;
        this.canonicalMapper = buildCanonicalMapper();
        this.hashKey = new SecretKeySpec(properties.getHashSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

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
    @Override
    public IdempotencyContext createContext(@NotNull String merchantId, @NotNull String idempotencyKey, @NotNull Object request) {
        return new IdempotencyContext(merchantId, idempotencyKey, hash(request));
    }

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
    @Override
    @Transactional(readOnly = true)
    public Optional<String> findExistingResourceId(@NotNull IdempotencyContext context) {
        return idempotencyRecordRepository
                .findByMerchantIdAndIdempotencyKey(context.merchantId(), context.idempotencyKey())
                .map(existing -> {
                    // Hash karşılaştırması sabit sürede yapılır (timing attack)
                    if (!MessageDigest.isEqual(existing.getRequestHash().getBytes(StandardCharsets.UTF_8),
                            context.requestHash().getBytes(StandardCharsets.UTF_8))) {
                        throw new IdempotencyKeyReuseException(context.idempotencyKey());
                    }
                    return existing.getResourceId();
                });
    }

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
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveRecord(@NotNull IdempotencyContext context, @NotNull String resourceType, @NotNull String resourceId) {
        idempotencyRecordRepository.save(IdempotencyRecord.builder()
                .merchantId(context.merchantId())
                .idempotencyKey(context.idempotencyKey())
                .requestHash(context.requestHash())
                .resourceType(resourceType)
                .resourceId(resourceId)
                .expiresAt(LocalDateTime.now().plus(properties.getRecordTtl()))
                .build());
    }

    /**
     * <h1>Süresi Dolan Kayıtları Silme</h1>
     *
     * @return Silinen kayıt sayısı
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-2
     */
    @Override
    @Transactional
    public int deleteExpiredRecords() {
        return idempotencyRecordRepository.deleteExpired(LocalDateTime.now(), properties.getCleanupBatchSize());
    }

    private String hash(Object request) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hashKey);
            byte[] canonical = canonicalMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(mac.doFinal(canonical));
        } catch (GeneralSecurityException | JsonProcessingException e) {
            throw new IllegalStateException("Request hash could not be calculated", e);
        }
    }

    /**
     * Hash için ayrı bir mapper. Alanlar alfabetik sıralanır ve {@code WRITE_ONLY} alanlar (kart no, CVV)
     * da dahil edilir. Aksi halde sadece kartı farklı olan iki istek aynı hash'i üretirdi.
     */
    private static ObjectMapper buildCanonicalMapper() {
        SimpleModule decimalModule = new SimpleModule();
        decimalModule.addSerializer(BigDecimal.class, new JsonSerializer<>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(value.stripTrailingZeros().toPlainString());
            }
        });

        return JsonMapper.builder()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .annotationIntrospector(new JacksonAnnotationIntrospector() {
                    @Override
                    public JsonProperty.Access findPropertyAccess(Annotated annotated) {
                        return JsonProperty.Access.AUTO;
                    }
                })
                .addModule(decimalModule)
                .build();
    }
}
