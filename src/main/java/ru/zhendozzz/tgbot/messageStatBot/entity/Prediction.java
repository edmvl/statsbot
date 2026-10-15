package ru.zhendozzz.tgbot.messageStatBot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Prediction {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private long id;
    private String text;
}
