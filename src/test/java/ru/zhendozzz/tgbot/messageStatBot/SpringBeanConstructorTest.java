package ru.zhendozzz.tgbot.messageStatBot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Страховка от поломки старта приложения: если у бина несколько конструкторов
 * и ни один не помечен {@code @Autowired}, Spring не может выбрать, падает на
 * {@code NoSuchMethodException} и сервис не поднимается (так прод упал на dbd251c).
 */
class SpringBeanConstructorTest {

    @Test
    void everyBeanHasUnambiguousConstructor() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        Set<BeanDefinition> candidates = scanner.findCandidateComponents("ru.zhendozzz.tgbot.messageStatBot");

        assertFalse(candidates.isEmpty(), "не нашли ни одного бина — сломан скан classpath");

        List<String> broken = new ArrayList<>();
        for (BeanDefinition candidate : candidates) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            Constructor<?>[] constructors = type.getDeclaredConstructors();
            boolean singleConstructor = constructors.length == 1;
            boolean autowiredPresent =
                    Arrays.stream(constructors).anyMatch(c -> c.isAnnotationPresent(Autowired.class));
            if (!singleConstructor && !autowiredPresent) {
                broken.add(type.getSimpleName() + " (конструкторов: " + constructors.length + ")");
            }
        }

        assertEquals(List.of(), broken, "Spring не сможет создать эти бины");
    }
}
