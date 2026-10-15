package ru.zhendozzz.tgbot.messageStatBot.entity;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import ru.zhendozzz.tgbot.messageStatBot.dto.AdsFileView;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.AdStatus;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "ads")
public class Ads {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id")
    private String chatId;

    @Column(name = "message")
    private String message;

    @Column(name = "last_message")
    private String lastMessage;

    @Column(name = "date_start")
    private LocalDateTime dateStart;

    @Column(name = "date_end")
    private LocalDateTime dateEnd;

    /** Статус поста: {@link AdStatus}. Обычная реклама создаётся сразу активной. */
    @Column(name = "status")
    private String status = AdStatus.ACTIVE;

    /** Telegram id пользователя, предложившего рекламу (для обычной рекламы — {@code null}). */
    @Column(name = "user_id")
    private String userId;

    /** На сколько дней заказан показ (для предложений пользователей). */
    @Column(name = "days")
    private Integer days;

    /** Сколько звёзд оплачено. */
    @Column(name = "stars_paid")
    private Integer starsPaid;

    /** Идентификатор платежа Telegram. */
    @Column(name = "payment_charge_id")
    private String paymentChargeId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Вложения поста. Не хранятся в этой таблице — подтягиваются из {@code ads_file}
     * сервисом перед отдачей в API. {@code READ_ONLY} — чтобы случайный {@code files}
     * в запросе не влиял на сохранение.
     */
    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private List<AdsFileView> files = new ArrayList<>();
}
