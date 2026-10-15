package ru.zhendozzz.tgbot.messageStatBot.service;

import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.entity.Horo;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.HoroRepository;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.HoroscopeEnum;
import lombok.SneakyThrows;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
public class HoroService implements CommandExecutable {

    private final HoroRepository horoRepository;

    private final MessageExecutor messageExecutor;

    public HoroService(HoroRepository horoRepository, MessageExecutor messageExecutor) {
        this.horoRepository = horoRepository;
        this.messageExecutor = messageExecutor;
    }

    synchronized public void grubDataFromResource() {
        LocalDate now = LocalDate.now();
        Arrays.stream(HoroscopeEnum.values())
                .map(this::parseHoro)
                .forEach(data -> horoRepository.save(Horo.builder()
                        .date(now)
                        .sign(data.getFirst())
                        .text(data.getSecond())
                        .build()));
    }

    public String getHoroToDate(LocalDate date) {
        HoroscopeEnum[] values = HoroscopeEnum.values();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder
                .append("\n")
                .append("Гороскоп на сегодня")
                .append("\n")
                .append("---------------------")
                .append("\n");
        for (HoroscopeEnum value : values) {
            List<Horo> horoByDateAndSign = getHoroByDateAndSign(date, value.getSysname());
            if (horoByDateAndSign.size() > 1) {
                Horo horo = horoByDateAndSign.stream().findFirst().get();
                stringBuilder
                        .append(HoroscopeEnum.bySysname(horo.getSign()).getName())
                        .append("\n")
                        .append(horo.getText())
                        .append("\n")
                        .append("--------------------")
                        .append("\n");
            }
        }
        return stringBuilder.toString();
    }

    private List<Horo> getHoroByDateAndSign(LocalDate date, String sysname) {
        return horoRepository.getAllByDateAndSign(date, sysname);
    }

    @SneakyThrows
    private Pair<String, String> parseHoro(HoroscopeEnum sign) {
        String url = "https://horo.mail.ru/prediction/" + sign.getSysname() + "/today/";
        Document document = getHTMLPage(url);
        while (Objects.isNull(document)) {
            Thread.sleep(10000);
            document = getHTMLPage(url);
        }
        String text = document.body().select("[data-qa-detail=ArticleLayout]").text();
        return Pair.of(sign.getSysname(), text);
    }

    private Document getHTMLPage(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com")
                    .followRedirects(true)
                    .execute()
                    .parse();
        } catch (IOException e) {
            return null;
        }
    }

    public void sendHoro(Long chatId, HoroscopeEnum horoscopeEnum) {
        LocalDate now = LocalDate.now();
        String sysname = horoscopeEnum.getSysname();
        Horo horo = getOrReloadHoro(now, sysname);
        messageExecutor.sendMessage(chatId, getFormattedHoroTextToDate(horo.getText(), now, horoscopeEnum));
    }

    @SneakyThrows
    private Horo getOrReloadHoro(LocalDate now, String sysname) {
        List<Horo> horoByDateAndSign = getHoroByDateAndSign(now, sysname);
        if (horoByDateAndSign.size() > 0) {
            return horoByDateAndSign.stream().findFirst().get();
        }
        for (int i = 0; i < 10; i++) {
            Thread.sleep(10000);
            grubDataFromResource();
            horoByDateAndSign = getHoroByDateAndSign(now, sysname);
            if (horoByDateAndSign.size() > 0) {
                return horoByDateAndSign.stream().findFirst().get();
            }
        }
        return new Horo();
    }

    private String getFormattedHoroTextToDate(String text, LocalDate date, HoroscopeEnum horoscopeEnum) {
        return horoscopeEnum.getName() + " на " + date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + "\n" + text;
    }

    @Override
    public void executeCommand(MessageDTO messageDTO, Update update) {
        HoroscopeEnum horoscopeEnum = HoroscopeEnum.byName(messageDTO.getUserText());
        if (Objects.nonNull(horoscopeEnum)) {
            sendHoro(messageDTO.getChatId(), horoscopeEnum);
        }
    }
}
