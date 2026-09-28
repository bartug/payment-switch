/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.messaging.inbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class InboxServiceImpl implements InboxService {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * <h1>İlk Kez İşleme Kontrolü</h1>
     * <p>Event'i işlendi olarak işaretler. {@code false} dönerse mesaj daha önce işlenmiştir ve atlanmalıdır.</p>
     * <p>İş verisiyle <b>aynı transaction</b> içinde çağrılmalıdır. İşlem rollback olursa inbox kaydı da geri alınır
     * ve mesaj tekrar geldiğinde yeniden işlenir.</p>
     *
     * @param eventId  Kafka mesajındaki {@code event-id} header'ı
     * @param consumer Consumer adı
     * @return İlk kez işleniyorsa {@code true}
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-4
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(@NotNull String eventId, @NotNull String consumer) {
        boolean first = processedEventRepository.insertIfAbsent(eventId, consumer) == 1;
        if (!first) {
            log.info("Duplicate event skipped. eventId={}, consumer={}", eventId, consumer);
        }
        return first;
    }

    /**
     * <h1>İşlenmiş mi Kontrolü</h1>
     * <p>Kilit almadan bakar; pahalı bir işe (dış servis çağrısı) girmeden önce tekrarı elemek için kullanılır.
     * Asıl garanti yine {@link #markProcessed(String, String)}'tir.</p>
     *
     * @param eventId  Event ID
     * @param consumer Consumer adı
     * @return Daha önce işlendiyse {@code true}
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-5
     */
    @Override
    @Transactional(readOnly = true)
    public boolean isProcessed(@NotNull String eventId, @NotNull String consumer) {
        return processedEventRepository.existsByEventIdAndConsumer(eventId, consumer);
    }
}
