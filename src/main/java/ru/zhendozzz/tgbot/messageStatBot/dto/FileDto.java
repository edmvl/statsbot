package ru.zhendozzz.tgbot.messageStatBot.dto;

import ru.zhendozzz.tgbot.messageStatBot.util.enums.FileTypes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class FileDto {
    private FileTypes fileType;
    private String fileId;
    private String fileName;

    public String getFileNameForSave() {
        return (Objects.nonNull(fileName) ? fileName : fileId) + getExt(getFileType());
    }

    private String getExt(FileTypes fileType) {
        switch (fileType) {
            case AUDIO -> {
                return ".mp3";
            }
            case VIDEO, VIDEO_NOTE -> {
                return ".mp4";
            }
            case PHOTO -> {
                return ".jpeg";
            }
            case VOICE -> {
                return ".wav";
            }
            case STICKER -> {
                return ".webp";
            }
            case DOCUMENT -> {
                return "";
            }
            case ANIMATION -> {
                return ".gif";
            }
        }
        return null;
    }
}
