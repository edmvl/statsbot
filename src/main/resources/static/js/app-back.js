// Общие UI-хелперы мини-приложения: кнопка "Назад", спиннер загрузки страниц,
// показ админских пунктов меню (супер-админ по Telegram id, админ чата — по ответу сервера).
(function () {
    'use strict';

    // === Определяем собственный <script>, чтобы прочитать адрес возврата из data-back ===
    var currentScript = document.currentScript;
    if (!currentScript) {
        var scripts = document.getElementsByTagName('script');
        for (var i = scripts.length - 1; i >= 0; i--) {
            if ((scripts[i].src || '').indexOf('app-back.js') !== -1) {
                currentScript = scripts[i];
                break;
            }
        }
    }
    var fallbackUrl = currentScript ? currentScript.getAttribute('data-back') : null;

    function appBack() {
        try {
            if (window.history.length > 1
                && document.referrer
                && document.referrer.indexOf(window.location.origin) === 0) {
                window.history.back();
                return;
            }
        } catch (e) {
            // ignore
        }
        if (fallbackUrl) {
            window.location.href = fallbackUrl;
            return;
        }
        if (window.Telegram && window.Telegram.WebApp && typeof window.Telegram.WebApp.close === 'function') {
            window.Telegram.WebApp.close();
        } else {
            window.location.href = '/app';
        }
    }

    function injectStyles() {
        if (document.getElementById('app-back-styles')) {
            return;
        }
        var style = document.createElement('style');
        style.id = 'app-back-styles';
        // !important — чтобы элементы переживали принудительные темы страниц.
        style.textContent =
            '.app-back-btn{display:inline-flex!important;align-items:center!important;gap:6px!important;'
            + 'margin:0 0 14px 0!important;padding:8px 14px!important;'
            + 'border:1px solid rgba(127,127,127,.35)!important;border-radius:10px!important;'
            + 'background:rgba(127,127,127,.12)!important;color:inherit!important;'
            + 'font-family:inherit!important;font-size:14px!important;font-weight:600!important;'
            + 'line-height:1.2!important;cursor:pointer!important;-webkit-tap-highlight-color:transparent!important}'
            + '.app-back-btn:hover{background:rgba(127,127,127,.22)!important}'
            + '.app-back-btn:active{transform:scale(.97)!important}'
            + '#app-spinner{position:fixed!important;inset:0!important;z-index:2147483000!important;'
            + 'display:none;align-items:center!important;justify-content:center!important;'
            + 'background:rgba(127,127,127,.25)!important;backdrop-filter:blur(2px)!important;'
            + '-webkit-backdrop-filter:blur(2px)!important}'
            + '#app-spinner.app-spinner-visible{display:flex!important}'
            + '#app-spinner .app-spinner-ring{width:44px!important;height:44px!important;'
            + 'border-radius:50%!important;border:4px solid rgba(127,127,127,.35)!important;'
            + 'border-top-color:#1f6feb!important;animation:app-spin .8s linear infinite!important}'
            + '@keyframes app-spin{to{transform:rotate(360deg)}}';
        (document.head || document.documentElement).appendChild(style);
    }

    function injectBackButton() {
        if (!document.body || document.getElementById('app-back-btn')) {
            return;
        }
        var button = document.createElement('button');
        button.id = 'app-back-btn';
        button.type = 'button';
        button.className = 'app-back-btn';
        button.setAttribute('aria-label', 'Назад');
        button.textContent = '\u2190 Назад';
        button.addEventListener('click', appBack);
        document.body.insertBefore(button, document.body.firstChild);
    }

    function injectSpinner() {
        if (!document.body || document.getElementById('app-spinner')) {
            return;
        }
        var overlay = document.createElement('div');
        overlay.id = 'app-spinner';
        overlay.setAttribute('aria-hidden', 'true');
        var ring = document.createElement('div');
        ring.className = 'app-spinner-ring';
        overlay.appendChild(ring);
        document.body.appendChild(overlay);
    }

    function showSpinner() {
        var overlay = document.getElementById('app-spinner');
        if (overlay) {
            overlay.classList.add('app-spinner-visible');
        }
    }

    function hideSpinner() {
        var overlay = document.getElementById('app-spinner');
        if (overlay) {
            overlay.classList.remove('app-spinner-visible');
        }
    }

    // Спиннер на время перехода на другую страницу.
    function isInternalNavigableLink(anchor) {
        if (!anchor || anchor.tagName !== 'A') {
            return false;
        }
        if (anchor.hasAttribute('download')) {
            return false;
        }
        var target = anchor.getAttribute('target');
        if (target && target !== '_self') {
            return false;
        }
        var href = anchor.getAttribute('href') || '';
        if (!href || href.charAt(0) === '#' || /^(javascript|mailto|tel|data):/i.test(href)) {
            return false;
        }
        try {
            var url = new URL(anchor.href, window.location.href);
            if (url.origin !== window.location.origin) {
                return false;
            }
            if (url.pathname === window.location.pathname
                && url.search === window.location.search
                && url.hash) {
                return false;
            }
        } catch (e) {
            return false;
        }
        return true;
    }

    function setupNavigationSpinner() {
        document.addEventListener('click', function (event) {
            if (event.defaultPrevented || event.button !== 0
                || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
                return;
            }
            var anchor = event.target;
            while (anchor && anchor.tagName !== 'A') {
                anchor = anchor.parentElement;
            }
            if (isInternalNavigableLink(anchor)) {
                showSpinner();
            }
        }, true);

        window.addEventListener('beforeunload', showSpinner);
        window.addEventListener('pagehide', showSpinner);
        window.addEventListener('pageshow', hideSpinner);
        window.addEventListener('load', hideSpinner);
        document.addEventListener('DOMContentLoaded', hideSpinner);
    }

    // === Элементы "только для супер-админа" ===
    function telegramInitData() {
        var data = '';
        try {
            if (window.Telegram && window.Telegram.WebApp && window.Telegram.WebApp.initData) {
                data = window.Telegram.WebApp.initData;
            }
        } catch (e) {
            // ignore
        }
        if (!data) {
            try {
                if (location.hash) {
                    data = new URLSearchParams(location.hash.replace(/^#/, '')).get('tgWebAppData') || '';
                }
            } catch (e) {
                // ignore
            }
        }
        return data;
    }

    function telegramUserId() {
        try {
            var unsafe = window.Telegram && window.Telegram.WebApp && window.Telegram.WebApp.initDataUnsafe;
            var id = unsafe && unsafe.user && unsafe.user.id;
            if (id) {
                return String(id);
            }
        } catch (e) {
            // ignore
        }
        var raw = telegramInitData();
        if (!raw) {
            return null;
        }
        try {
            var userJson = new URLSearchParams(raw).get('user');
            if (!userJson) {
                return null;
            }
            var user = JSON.parse(userJson);
            return user && user.id ? String(user.id) : null;
        } catch (e) {
            return null;
        }
    }

    function showAdminSection() {
        var sections = document.querySelectorAll('[data-admin-section]');
        for (var i = 0; i < sections.length; i++) {
            sections[i].removeAttribute('hidden');
            if (sections[i].style) {
                sections[i].style.display = '';
            }
        }
    }

    function showSuperAdminOnlyElements() {
        var elements = document.querySelectorAll('[data-super-admin-only]');
        for (var i = 0; i < elements.length; i++) {
            elements[i].removeAttribute('hidden');
            if (elements[i].style) {
                elements[i].style.display = '';
            }
        }
    }

    function showSuperAdminElements() {
        showAdminSection();
        var elements = document.querySelectorAll('[data-super-admin-id]');
        for (var i = 0; i < elements.length; i++) {
            elements[i].removeAttribute('hidden');
            if (elements[i].style) {
                elements[i].style.display = '';
            }
        }
        showSuperAdminOnlyElements();
    }

    // Страница могла быть отрендерена без cookie — спрашиваем сервер ещё раз,
    // передавая подписанный initData заголовком.
    var chatAdminRequested = false;

    function revealChatAdminElements() {
        if (chatAdminRequested || !document.querySelector('[data-admin-section]')) {
            return;
        }
        var initData = telegramInitData();
        if (!initData) {
            return;
        }
        chatAdminRequested = true;
        try {
            var xhr = new XMLHttpRequest();
            xhr.open('GET', '/app/access', true);
            xhr.setRequestHeader('X-Telegram-Init-Data', initData);
            xhr.onload = function () {
                if (xhr.status < 200 || xhr.status >= 300) {
                    return;
                }
                try {
                    var access = JSON.parse(xhr.responseText);
                    if (access && access.chatAdmin) {
                        showAdminSection();
                    }
                    if (access && access.superAdmin) {
                        showSuperAdminElements();
                    }
                } catch (e) {
                    // ignore
                }
            };
            xhr.send();
        } catch (e) {
            // ignore
        }
    }

    // true — если id пользователя уже известен (решение принято).
    function revealSuperAdminElements() {
        var userId = telegramUserId();
        if (!userId) {
            return false;
        }
        var elements = document.querySelectorAll('[data-super-admin-id]');
        for (var i = 0; i < elements.length; i++) {
            if (elements[i].getAttribute('data-super-admin-id') === userId) {
                showSuperAdminElements();
                return true;
            }
        }
        revealChatAdminElements();
        return true;
    }

    function watchSuperAdminElements() {
        if (!document.querySelector('[data-super-admin-id]')) {
            return;
        }
        if (revealSuperAdminElements()) {
            return;
        }
        // Данные Telegram могут прийти чуть позже — короткие повторные проверки.
        var attempts = 0;
        var timer = setInterval(function () {
            attempts++;
            if (revealSuperAdminElements() || attempts >= 25) {
                clearInterval(timer);
            }
        }, 200);
    }

    // === Подпись Telegram initData для серверных запросов ===
    // Сервер доверяет только подписанному initData, поэтому кладём его в cookie
    // (path=/) — тогда её несут и переходы, и формы, и ajax. Первый заход в сессии
    // перезагружаем один раз, чтобы серверные страницы сразу увидели пользователя.
    function installAuthCookie() {
        if (document.cookie && document.cookie.indexOf('tg_webapp_auth=') !== -1) {
            return true;
        }
        var data = telegramInitData();
        if (!data) {
            return false;
        }
        try {
            var secure = window.location.protocol === 'https:' ? '; secure' : '';
            document.cookie = 'tg_webapp_auth=' + data + '; path=/; samesite=lax' + secure;
        } catch (e) {
            return true;
        }
        // Cookie могла не сохраниться (например, ограничения WebView) — тогда
        // не перезагружаем страницу бесконечно.
        if (!document.cookie || document.cookie.indexOf('tg_webapp_auth=') === -1) {
            return true;
        }
        try {
            var url = new URL(window.location.href);
            if (!url.searchParams.has('_reloaded')) {
                url.searchParams.set('_reloaded', '1');
                window.location.replace(url.toString());
            }
        } catch (e) {
            // ignore
        }
        return true;
    }

    // Данные Telegram могут появиться чуть позже загрузки скрипта — повторяем попытки.
    function watchAuthCookie() {
        if (installAuthCookie()) {
            return;
        }
        var attempts = 0;
        var timer = setInterval(function () {
            attempts++;
            if (installAuthCookie() || attempts >= 10) {
                clearInterval(timer);
            }
        }, 300);
    }

    // === Соответствие цветовой схемы Telegram ===
    // Библиотека кладёт в :root свои переменные, а color-scheme выставляем сами —
    // иначе нативные поля (дата/время) рисуются в системной схеме и выбиваются.
    function applyColorScheme() {
        try {
            var scheme = window.Telegram && window.Telegram.WebApp ? window.Telegram.WebApp.colorScheme : null;
            if (scheme === 'dark' || scheme === 'light') {
                document.documentElement.style.colorScheme = scheme;
            }
        } catch (e) {
            // ignore
        }
    }

    function setup() {
        installStylesAndNavigation();
        watchAuthCookie();
        applyColorScheme();
    }

    function installStylesAndNavigation() {
        injectStyles();
        // Кнопка "Назад" нужна только там, где задан адрес возврата (data-back).
        if (fallbackUrl) {
            injectBackButton();
        }
        injectSpinner();
        setupNavigationSpinner();
        watchSuperAdminElements();

        if (window.Telegram && window.Telegram.WebApp) {
            var tg = window.Telegram.WebApp;
            try {
                if (typeof tg.expand === 'function') {
                    tg.expand();
                }
                if (fallbackUrl && tg.BackButton) {
                    tg.BackButton.show();
                    tg.BackButton.onClick(appBack);
                }
            } catch (e) {
                // ignore
            }
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', setup);
    } else {
        setup();
    }
})();
