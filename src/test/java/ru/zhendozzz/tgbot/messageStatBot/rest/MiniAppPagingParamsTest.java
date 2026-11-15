package ru.zhendozzz.tgbot.messageStatBot.rest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Параметры {@code page}/{@code count} приходят из URL Mini App и раньше попадали
 * в запрос как есть: {@code ?count=50000} поднимал десятки тысяч строк лога в память.
 */
class MiniAppPagingParamsTest {

    @Test
    void countWithoutValueUsesDefault() {
        assertEquals(30, MiniAppController.parseCount(null));
        assertEquals(30, MiniAppController.parseCount(""));
    }

    @Test
    void countIsLimitedFromAbove() {
        assertEquals(100, MiniAppController.parseCount("50000"));
        assertEquals(100, MiniAppController.parseCount("101"));
        assertEquals(100, MiniAppController.parseCount(String.valueOf(Integer.MAX_VALUE)));
    }

    @Test
    void countKeepsReasonableValue() {
        assertEquals(50, MiniAppController.parseCount("50"));
        assertEquals(50, MiniAppController.parseCount(" 50 "));
        assertEquals(1, MiniAppController.parseCount("1"));
    }

    @Test
    void brokenCountFallsBackToDefault() {
        assertEquals(30, MiniAppController.parseCount("abc"));
        assertEquals(30, MiniAppController.parseCount("0"));
        assertEquals(30, MiniAppController.parseCount("-10"));
        assertEquals(30, MiniAppController.parseCount("999999999999"));
    }

    @Test
    void pageCannotBeNegative() {
        assertEquals(0, MiniAppController.parsePage(null));
        assertEquals(0, MiniAppController.parsePage("-1"));
        assertEquals(0, MiniAppController.parsePage("abc"));
    }

    @Test
    void pageIsLimitedFromAbove() {
        assertEquals(100_000, MiniAppController.parsePage("100000000"));
        assertEquals(7, MiniAppController.parsePage("7"));
    }
}
