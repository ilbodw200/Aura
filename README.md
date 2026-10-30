# AURA — Android и iOS

[Скачать AURA 0.1.1 в GitHub Releases](https://github.com/ilbodw200/Aura/releases/tag/v0.1.1).

- `AURA-Android-0.1.1.apk` — готовый подписанный APK для Android 8.0+, armeabi-v7a / arm64-v8a / x86_64.
- `AURA-iOS-0.1.1-unsigned.ipa` — iOS 17+, нативный Liquid Glass на iOS 26+. Подпишите приложение и расширение сертификатом, разрешающим Network Extension.
- `SHA256SUMS.txt` — контрольные суммы файлов.

## Исправление App Group и HWID

В 0.1.1 **App Group больше не нужна**: конфигурация передаётся через системную конфигурацию VPN. Фактический ID расширения определяется по встроенному `AuraTunnel.appex`, включая изменение ID при сторонней подписи. Разрешение Network Extension в подписи всё равно обязательно.

Для существующей подписки: **Серверы → настройки подписки → Отправлять HWID → сохранить → обновить подписку**. HWID AURA можно скопировать в настройках приложения. Это идентификатор AURA, а не серийный номер телефона и не HWID Happ. Провайдеру может понадобиться новая привязка устройства. По умолчанию отправка выключена.

Подробности: [HWID и подпись](Docs/HWID-AND-SIGNING.md), [Android](Android/README.md).

## Возможности

VLESS/Reality, VMess, Trojan, Shadowsocks, SOCKS5, Hysteria2; импорт ссылок, файлов, QR, HTTPS-подписок; ручное обновление; избранное и TCP-задержка; правила доменов, суффиксов, keywords и IPv4/CIDR с direct/proxy/block; DoH, обход LAN, зашифрованное хранилище и резервные копии. В Android доступно исключение приложений из VPN.

Оформление: пять акцентов, четыре фона, светлая/тёмная/системная тема, радиус, компактность, скрытие адресов и порядок блоков. iOS использует SwiftUI Liquid Glass; Android — нативные представления и стеклянные карточки.

## Сборка

**iOS:** Xcode 26+, XcodeGen. `bash Scripts/bootstrap-core.sh` собирает Libbox и генерирует `Aura.xcodeproj`. `AURA_TEAM_ID=ABCDEFGHIJ bash Scripts/build-ipa.sh signed full` архивирует подписанный IPA на Mac. Workflow `Build unsigned iPhone IPA` выполняет облачную сборку без подписи. Общие тесты: `swift test` на macOS.

**Android:** JDK 17, SDK 35, NDK 28.0.13004108, Go 1.24.2. `bash Scripts/bootstrap-android-core.sh`, затем `cd Android && ./gradlew testReleaseUnitTest assembleDebug`. Для release нужны `AURA_SIGNING_STORE` и `AURA_SIGNING_PASSWORD`. Постоянный ключ подписи хранится в зашифрованных GitHub Actions Secrets; исходники не содержат ключей и паролей.

## Проверено и ограничения

iOS: полная сборка с ядром, тесты конфигурации и HWID-запросов, запуск в симуляторе. Android: JVM-тесты, проверка подписи, два теста Android 15 с загрузкой native-ядра и конфигурациями всех шести протоколов; подписанный release APK установлен и запущен в эмуляторе, снимок экрана проверен.

Реальное VPN-соединение на физических телефонах с вашими серверами и подписью требует отдельной проверки. Камера QR также требует проверки на устройстве. Это developer preview, полный паритет с Happ не достигнут: XHTTP и фирменные зашифрованные ссылки Happ пока не поддерживаются. Серверы и подписки не предоставляются.

Ядро зафиксировано на sing-box v1.11.15. Перед производственным выпуском необходимы обновление ядра, аудит зависимостей и проверки устойчивости. Лицензия — GPL-3.0-or-later; см. LICENSE и THIRD_PARTY_NOTICES.md.
