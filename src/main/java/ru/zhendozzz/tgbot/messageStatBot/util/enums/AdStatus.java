package ru.zhendozzz.tgbot.messageStatBot.util.enums;

/**
 * Статусы рекламного поста и параметры оплаты.
 */
public final class AdStatus {

    /** Создан админом или оплачен пользователем — публикуется по окну показа. */
    public static final String ACTIVE = "ACTIVE";

    /** Предложение пользователя, ждёт решения супер-админа. */
    public static final String PENDING = "PENDING";

    /** Одобрено супер-админом, ждёт оплаты звёздами. */
    public static final String APPROVED = "APPROVED";

    /** Отклонено супер-админом. */
    public static final String REJECTED = "REJECTED";

    /** Цена одного дня показа в звёздах. */
    public static final int STARS_PER_DAY = 100;

    /** Максимальное количество дней в одном заказе. */
    public static final int MAX_DAYS = 30;

    /** Сколько предложений одновременно может быть на модерации у одного пользователя. */
    public static final int MAX_PENDING_PER_USER = 3;

    private AdStatus() {
    }
}
