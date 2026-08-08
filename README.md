# LetoVPN Config Checker

Android-приложение для проверки бесплатных VLESS / VMess / Trojan конфигов.

**Скачать:** [Releases](https://github.com/adop1344-bot/LetoVPN-Config-Checker/releases) · [Actions (Artifacts)](https://github.com/adop1344-bot/LetoVPN-Config-Checker/actions)

Telegram: [@letovpn_free](https://t.me/letovpn_free)

---

## Возможности

- Загрузка конфигов из [LetoVPN sources.txt](https://github.com/adop1344-bot/LetoVPN_free) и своих URL
- Включение / выключение каждого источника
- 6 методов проверки:
  - **Неточная (TCP)** — быстрый TCP-коннект
  - **Средняя (TCP+DNS)** — DNS + TCP
  - **Точная (Via Proxy GET)** — TCP + HTTP HEAD
  - **Суперточная (Deep)** — DNS + 2×TCP + HTTP
  - **Xray (ядро)** — скачивает Xray-core, проверяет VLESS через SOCKS
  - **Максимум (Xray Speed)** — Xray + реальная загрузка ~100 KB
- Темы: тёмная / светлая / пользовательская / динамическая (Android 12+)
- Многопоточность до 100 потоков
- `0` конфигов в слайдере = проверить все
- Стоп после N рабочих
- Фильтр «Только VLESS»
- Копирование каждого конфига / топ-10 / base64-подписка
- Сохранение рабочих в TXT
- **Постоянная подпись APK** — обновления ставятся поверх, без удаления

---

## Установка

1. Скачай `app-release.apk` из [Releases](https://github.com/adop1344-bot/LetoVPN-Config-Checker/releases)
2. Разреши установку из неизвестных источников
3. Установи

Новые версии (с той же подписью) можно ставить **поверх** старых — данные и настройки сохранятся.

---

## Сборка

GitHub Actions при каждом push в `main`:
1. Собирает **signed release APK**
2. Загружает в Artifacts
3. Создаёт GitHub Release с APK и changelog

Локально:
```bash
# keystore уже в keystore/letovpn.keystore.b64
base64 -d keystore/letovpn.keystore.b64 > keystore/letovpn.keystore
./gradlew assembleRelease
```

---

## Changelog

См. [Releases](https://github.com/adop1344-bot/LetoVPN-Config-Checker/releases) или полный список в описании релиза.

### 1.6.1
- Постоянная подпись APK (обновления без удаления)
- Авто-релиз в GitHub Releases

### 1.6
- Переключатели источников
- Xray Speed
- Стоп после N / только VLESS / экспорт подписки

### 1.5
- Xray-core, автосохранение настроек, новый UI

### 1.4
- Реальные методы TCP / DNS / Proxy GET / Deep, темы, свои источники

---

Сделано через Grok + GitHub.
