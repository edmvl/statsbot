package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.entity.TgUser;
import ru.zhendozzz.tgbot.messageStatBot.entity.UserNameHistory;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.UserNameHistoryRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.UserRepo;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepo userRepo = mock(UserRepo.class);
    private final UserNameHistoryRepo userNameHistoryRepo = mock(UserNameHistoryRepo.class);
    private final MessageExecutor messageExecutor = mock(MessageExecutor.class);
    private final UserService userService = new UserService(userRepo, userNameHistoryRepo, messageExecutor);

    @Test
    void doesNotAddHistoryWhenOnlyNameFieldsAreRedistributed() {
        TgUser existing = new TgUser();
        existing.setId("42");
        existing.setFirstName("Evgeniy");
        existing.setLastName("Dmitriev");
        existing.setUserName("edmvl");
        when(userRepo.findById("42")).thenReturn(Optional.of(existing));

        MessageDTO dto = MessageDTO.builder()
                .userId(42L)
                .userFirstName("Evgeniy Dmitriev")
                .userLastName(null)
                .userName("edmvl")
                .build();

        userService.processMessage(dto, new Update());

        verify(userNameHistoryRepo, never()).save(any(UserNameHistory.class));
        verify(userRepo).save(existing);
    }

    @Test
    void doesNotAddHistoryForEmptyVsNull() {
        TgUser existing = new TgUser();
        existing.setId("42");
        existing.setFirstName("Иван");
        existing.setLastName(null);
        existing.setUserName("ivan");
        when(userRepo.findById("42")).thenReturn(Optional.of(existing));

        MessageDTO dto = MessageDTO.builder()
                .userId(42L)
                .userFirstName(" Иван ")
                .userLastName("")
                .userName("ivan")
                .build();

        userService.processMessage(dto, new Update());

        verify(userNameHistoryRepo, never()).save(any(UserNameHistory.class));
    }

    @Test
    void addsHistoryWhenNameActuallyChanged() {
        TgUser existing = new TgUser();
        existing.setId("42");
        existing.setFirstName("Иван");
        existing.setLastName("Петров");
        existing.setUserName("ivan");
        when(userRepo.findById("42")).thenReturn(Optional.of(existing));

        MessageDTO dto = MessageDTO.builder()
                .userId(42L)
                .userFirstName("Пётр")
                .userLastName("Петров")
                .userName("ivan")
                .build();

        userService.processMessage(dto, new Update());

        verify(userNameHistoryRepo).save(any(UserNameHistory.class));
    }
}
