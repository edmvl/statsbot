package ru.zhendozzz.tgbot.messageStatBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;

import java.util.Objects;

/**
 * Обработка оплаты рекламы звёздами Telegram: подтверждение предоплаты
 * и активация поста после успешного платежа.
 */
@Slf4j
@Service
public class AdPaymentService implements NeedProcessMessage {

    private final AdsService adsService;
    private final MessageExecutor messageExecutor;

    public AdPaymentService(AdsService adsService, MessageExecutor messageExecutor) {
        this.adsService = adsService;
        this.messageExecutor = messageExecutor;
    }

    /** Telegram присылает это до списания звёзд — ответить нужно за 10 секунд. */
    public void confirmPreCheckout(PreCheckoutQuery query) {
        boolean ok = adsService.canPay(query.getInvoicePayload());
        messageExecutor.answerPreCheckoutQuery(query.getId(), ok,
                ok ? null : "Счёт больше не актуален");
    }

    @Override
    public void processMessage(MessageDTO messageDTO, Update update) {
        Message message = update.getMessage();
        if (Objects.isNull(message) || !message.hasSuccessfulPayment()) {
            return;
        }
        SuccessfulPayment payment = message.getSuccessfulPayment();
        try {
            adsService.handleSuccessfulPayment(
                    payment.getInvoicePayload(),
                    payment.getTotalAmount(),
                    payment.getTelegramPaymentChargeId());
        } catch (Exception e) {
            log.error("Не удалось обработать оплату рекламы {}: {}",
                    payment.getInvoicePayload(), e.getMessage());
        }
    }
}
