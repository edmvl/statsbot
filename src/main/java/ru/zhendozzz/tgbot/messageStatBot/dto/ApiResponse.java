package ru.zhendozzz.tgbot.messageStatBot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@RequiredArgsConstructor
public class ApiResponse {
    @NonNull
    private Boolean success;
    @NonNull
    private String message;
    private String feedbackId;
}
