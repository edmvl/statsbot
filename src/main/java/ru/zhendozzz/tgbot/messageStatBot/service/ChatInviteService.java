package ru.zhendozzz.tgbot.messageStatBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatInviteViewDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.repo.ChatInviteRepo;

import java.util.List;
import java.util.Objects;

/**
 * Запоминает, кто кого добавил в чат (событие {@code new_chat_members}).
 */
@Slf4j
@Service
public class ChatInviteService implements NeedProcessMessage {

    private final ChatInviteRepo chatInviteRepo;

    public ChatInviteService(ChatInviteRepo chatInviteRepo) {
        this.chatInviteRepo = chatInviteRepo;
    }

    @Override
    public void processMessage(MessageDTO messageDTO, Update update) {
        if (!messageDTO.isServiceMessage()) {
            return;
        }
        Message message = Objects.nonNull(update.getMessage()) ? update.getMessage() : update.getEditedMessage();
        if (Objects.isNull(message) || Objects.isNull(message.getNewChatMembers())
                || message.getNewChatMembers().isEmpty()) {
            return;
        }
        User inviter = message.getFrom();
        // При входе по ссылке Telegram может указать в from самого вступившего —
        // пригласившего в таком событии нет.
        if (Objects.isNull(inviter) || Objects.isNull(inviter.getId())
                || Boolean.TRUE.equals(inviter.getIsBot())) {
            return;
        }
        String chatId = String.valueOf(message.getChatId());
        String inviterId = String.valueOf(inviter.getId());
        for (User member : message.getNewChatMembers()) {
            if (Objects.isNull(member) || Objects.isNull(member.getId())
                    || Objects.equals(inviter.getId(), member.getId())) {
                continue;
            }
            try {
                chatInviteRepo.upsert(chatId, inviterId, String.valueOf(member.getId()));
            } catch (Exception e) {
                log.error("Не удалось сохранить приглашение {} -> {} в чате {}: {}",
                        inviterId, member.getId(), chatId, e.getMessage());
            }
        }
    }

    /** Кто добавил пользователя в чат (самое свежее приглашение) или {@code null}. */
    public ChatInviteViewDto findInviter(String chatId, String inviteeId) {
        if (isBlank(chatId) || isBlank(inviteeId)) {
            return null;
        }
        List<ChatInviteViewDto> inviters = chatInviteRepo.findInviters(chatId, inviteeId);
        return inviters.isEmpty() ? null : inviters.get(0);
    }

    /** Кого пользователь добавлял в этот чат. */
    public List<ChatInviteViewDto> findInvitees(String chatId, String inviterId) {
        if (isBlank(chatId) || isBlank(inviterId)) {
            return List.of();
        }
        return chatInviteRepo.findInvitees(chatId, inviterId);
    }

    private boolean isBlank(String value) {
        return Objects.isNull(value) || value.trim().isEmpty();
    }
}
