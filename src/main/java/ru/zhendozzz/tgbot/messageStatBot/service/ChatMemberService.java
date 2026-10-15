package ru.zhendozzz.tgbot.messageStatBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.entity.ChatMember;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.ChatMemberRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.ChatSettingConstants;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class ChatMemberService implements NeedProcessMessage {

    private final MessageExecutor messageExecutor;
    private final ChatMemberRepo chatMemberRepo;
    private final LogRepo logRepo;
    private final SettingsService settingsService;

    public ChatMemberService(
            MessageExecutor messageExecutor,
            ChatMemberRepo chatMemberRepo, LogRepo logRepo,
            SettingsService settingsService
    ) {
        this.messageExecutor = messageExecutor;
        this.chatMemberRepo = chatMemberRepo;
        this.logRepo = logRepo;
        this.settingsService = settingsService;
    }

    private void updateUserGroups(MessageDTO messageDTO) {
        List<ChatMember> byChatIdAndUserId = chatMemberRepo.findByUserIdAndChatId(
                String.valueOf(messageDTO.getUserId()), String.valueOf(messageDTO.getChatId())
        );
        if (byChatIdAndUserId.isEmpty()) {
            chatMemberRepo.save(
                    new ChatMember(String.valueOf(messageDTO.getUserId()), String.valueOf(messageDTO.getChatId()), LocalDateTime.now())
            );
        } else {
            ChatMember chatMember = byChatIdAndUserId.get(0);
            chatMember.setLastMessage(LocalDateTime.now());
            chatMemberRepo.save(chatMember);
        }
    }

    private MessageEntity getMessageEntity(
            StringBuilder text, String chatId, String userId
    ) {
        String userName = logRepo.findUserNameByUserId(userId).stream().findFirst().orElse("null");
        User user = new User(Long.valueOf(userId), userName, false);
        MessageEntity messageEntity = new MessageEntity();
        messageEntity.setOffset(text.length());
        String userIdentityText = userName + "\n";
        text.append(userIdentityText);
        messageEntity.setLength(userIdentityText.length());
        messageEntity.setUser(user);
        messageEntity.setType("text_mention");
        return messageEntity;
    }

    private List<MessageEntity> getMessageEntities(List<ChatMember> chatMembers, StringBuilder text) {
        ArrayList<MessageEntity> messageEntities = new ArrayList<>();
        chatMembers.forEach(stats -> messageEntities.add(getMessageEntity(text, stats.getChatId(), stats.getUserId())));
        return messageEntities;
    }

    public void reminderToInactiveJob() {
        List<String> chatIds = settingsService.findEnabledBySettingName(ChatSettingConstants.ENABLE_MEMBER_CONTROL);
        chatIds.forEach(chatId -> {
            List<ChatMember> inactiveMembers = chatMemberRepo.getChatMemberByChatIdAndLastMessageIsBefore(chatId, LocalDateTime.now().minusDays(7));
            if (!inactiveMembers.isEmpty()) {
                StringBuilder text = new StringBuilder("Эти ребята молчат более 7 дней и скоро будут удалены из чата через 14 дней молчания:\n");
                List<MessageEntity> messageEntities = getMessageEntities(inactiveMembers, text);
                SendMessage sendMessage = new SendMessage();
                sendMessage.setEntities(messageEntities);
                sendMessage.setText(text.toString());
                sendMessage.setChatId(chatId);
                messageExecutor.sendMessage(sendMessage);
            }
            List<ChatMember> usersForBan = chatMemberRepo.getChatMemberByChatIdAndLastMessageIsBefore(chatId, LocalDateTime.now().minusDays(14));
            usersForBan.forEach(chatMember -> {
                messageExecutor.banUser(chatMember.getChatId(), chatMember.getUserId());
                chatMemberRepo.deleteById(chatMember.getId());
            });
        });
    }

    public void updateUserGroupsJob() {
        List<String> chatIds = settingsService.findEnabledBySettingName(ChatSettingConstants.ENABLE_MEMBER_CONTROL);
        chatIds.forEach(chatId -> {
            List<ChatMember> chatMemberByChatId = chatMemberRepo.getChatMemberByChatId(chatId);
            chatMemberByChatId.forEach(chatMember -> {
                if (!isMember(chatId, chatMember)) {
                    chatMemberRepo.deleteById(chatMember.getId());
                }
            });
        });
    }

    private boolean isMember(String chatId, ChatMember chatMember) {
        String status = messageExecutor.getUserStatusInChat(chatId, chatMember.getUserId());
        log.info("userId {} status in chat{}  is {}", chatMember.getUserId(), chatId, status);
        if (Objects.isNull(status)) {
            return false;
        }
        return List.of("member", "administrator").contains(status);
    }

    @Override
    public void processMessage(MessageDTO messageDTO, Update update) {
        if (settingsService.isEnabled(messageDTO.getChatId(), ChatSettingConstants.ENABLE_MEMBER_CONTROL)) {
            updateUserGroups(messageDTO);
        }
    }
}
