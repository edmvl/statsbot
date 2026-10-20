package ru.zhendozzz.tgbot.messageStatBot.util.enums;

import lombok.Getter;

public enum BotCommands {

    START_COMMAND("/start", ""),
    GET_CHATTY(".топ", "Топ 10 за день"),
    GET_WEEK_STATS(".неделя", "Статистика за неделю"),
    ADD_WORD(".цензура", "Добавить слово в список удаляемых"),
    GET_CHATTY_DAYS(".дни", "статистика по дням"),
    CHALLANGE_REGISTRATION("/reg", "зарегистрировться на розыгрыш"),
    TRIP(".поездки", "Посмотреть лог чата попутчиков"),
    REMINDER(".запомнить", "Создать напоминание, для использования необходимо выполнить команду реплаем на сообщении"),
    REMOVE_REMINDER(".забыть", "Удалить напоминание, для использования необходимо выполнить команду на упоминаемом сообшении"),
    QUIZ(".викторина", "Запустить викторину"),
    FEEDBACK(".связь", "Связаться с администратором"),
    INTERESTED(".следить", "Подписаться на сообщения пользователя"),
    PREDICTION(".предсказание", "Открыть печенье с предсказанием"),
    SEND_TO_CHAT(".вчат", "Написать в чат"),
    STATUS(".статус", "Проверить статус пользователя в канале поездок"),
    ;

    @Getter
    private final String command;
    @Getter
    private final String explainer;

    BotCommands(String command, String explainer) {
        this.command = command;
        this.explainer = explainer;
    }
}
