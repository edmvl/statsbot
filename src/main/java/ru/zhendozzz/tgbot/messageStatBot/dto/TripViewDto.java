package ru.zhendozzz.tgbot.messageStatBot.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TripViewDto {
    String getUserId();
    LocalDateTime getDateTime();
    String getFirstName();
    String getLastName();
    String getUserName();
    String getText();
    BigDecimal getRating();
    String getCnt();
    String getComments();
    String getAvatarPath();
}
