package ru.zhendozzz.tgbot.messageStatBot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.time.LocalDate;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Horo {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private long id;
    private String sign;
    private String text;
    private LocalDate date;
}
