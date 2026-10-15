package ru.zhendozzz.tgbot.messageStatBot.util.enums;

public enum FileTypes {
    PHOTO("photo"),
    DOCUMENT("document"),
    STICKER("sticker"),
    VIDEO_NOTE("video_note"),
    VIDEO("video"),
    VOICE("voice"),
    ANIMATION("animation"),
    AUDIO("audio"),
    ;

    private final String fileType;
    FileTypes(String fileType) {
         this.fileType = fileType;
    }
    public String getFileType() {
        return fileType;
    }
}
