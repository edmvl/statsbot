package ru.zhendozzz.tgbot.messageStatBot;

import ru.zhendozzz.tgbot.messageStatBot.service.AdsService;
import ru.zhendozzz.tgbot.messageStatBot.service.ChallengeService;
import ru.zhendozzz.tgbot.messageStatBot.service.ChatMemberService;
import ru.zhendozzz.tgbot.messageStatBot.service.HolidayService;
import ru.zhendozzz.tgbot.messageStatBot.service.HoroService;
import ru.zhendozzz.tgbot.messageStatBot.service.RemindService;
import ru.zhendozzz.tgbot.messageStatBot.service.SelectedService;
import ru.zhendozzz.tgbot.messageStatBot.service.StatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Job {

    private final StatsService statsService;
    private final SelectedService selectedService;
    private final ChallengeService challengeService;
    private final HoroService horoService;
    private final HolidayService holidayService;
    private final RemindService remindService;
    private final AdsService adsService;
    private final ChatMemberService chatMemberService;

    public Job(StatsService statsService,
               SelectedService selectedService,
               ChallengeService challengeService,
               HoroService horoService,
               HolidayService holidayService,
               RemindService remindService,
               AdsService adsService,
               ChatMemberService chatMemberService) {
        this.statsService = statsService;
        this.selectedService = selectedService;
        this.challengeService = challengeService;
        this.horoService = horoService;
        this.holidayService = holidayService;
        this.remindService = remindService;
        this.adsService = adsService;
        this.chatMemberService = chatMemberService;
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void sendStats() {
        statsService.sendStatAllChat();
    }

    @Scheduled(cron = "0 0 10 * * ?")
    public void sendSelected() {
        selectedService.sendSelectedAllChat();
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void loadHoro() {
        horoService.grubDataFromResource();
    }

    @Scheduled(cron = "0 * 8-23 * * ?")
    public void sendChallenges() {
        challengeService.finishAll();
    }

    @Scheduled(cron = "0 0 8 * * ?")
    public void sendHolidaysAllChat() {
        holidayService.sendHolidaysAllChat();
    }

    @Scheduled(cron = "0 0 10 * * ?")
    public void sendReplyReminder() {
        remindService.playReplyReminder();
    }

    @Scheduled(cron = "0 0 1 * * ?")
    public void sendRemindersAllChat() {
        remindService.playReminder();
    }

    @Scheduled(cron = "0 37 18 * * ?")
    public void updateChatMembers() {
        chatMemberService.updateUserGroupsJob();
    }

    @Scheduled(cron = "0 38 18 * * ?")
    public void reminderToInactiveJob() {
        chatMemberService.reminderToInactiveJob();
    }

    // Реклама: в будни раз в 2 часа, в выходные каждый час.
    // Пост поднимается вниз чата, окно показа у каждого поста своё (AdsService.isActive).
    @Scheduled(cron = "0 0 8-21/2 ? * MON-THU")
    public void adsWeekdays() {
        adsService.sendAds();
    }

    @Scheduled(cron = "0 0 5-22 * * FRI-SUN")
    public void adsWeekends() {
        adsService.sendAds();
    }

    // Каждые 10 минут: публикуем посты, у которых начался период показа,
    // и убираем те, у которых период закончился — чтобы не ждать следующего «поднятия».
    @Scheduled(cron = "0 0/10 * * * ?")
    public void adsReconcile() {
        adsService.reconcileAds();
    }

}
