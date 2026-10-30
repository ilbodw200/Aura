# Как получить файл для iPhone

Установочный формат — `.ipa`. **В текущей Windows-сессии IPA ещё не собран:** Xcode и доступ к Mac отсутствуют. Исходники, Xcode-проект и подготовленные скрипты не являются установочным файлом.

## На Mac: подписанный IPA

Нужны Xcode 26+, XcodeGen и учётная запись Apple Developer с доступом к App Groups и Network Extensions. Войдите в Xcode → Settings → Accounts. Для полной сборки нужен также Go.

```bash
brew install xcodegen go
export AURA_TEAM_ID=ABCDEFGHIJ # свой Team ID, не пароль
bash Scripts/build-ipa.sh signed full
```

В конце скрипт напечатает путь к подписанному `Aura.ipa` внутри `build-ipa.*/export/`. Файл создаётся только после успешной компиляции, архивирования и экспорта Xcode; успешный результат скрипта в этой сессии не проверялся.

Для первой проверки интерфейса можно выполнить `bash Scripts/build-ipa.sh signed preview`: такая сборка не содержит VPN-ядра.

Можно запустить `bash "Build IPA.command"`; он запросит Team ID и вариант сборки. Если macOS не разрешает запуск двойным нажатием после переноса с Windows, используйте эту команду из Terminal.

Скрипт по умолчанию использует уникальные ID на основе Team ID, например `app.aura.preview.abcdefghij`, расширение `.tunnel` и группу `group.app.aura.preview.abcdefghij`. Для своих зарегистрированных ID задайте `AURA_BUNDLE_ID` и при необходимости `AURA_APP_GROUP` через настройки проекта. При необходимости измените App Group в Xcode так, чтобы она совпадала в обоих таргетах. Аккаунт Xcode должен иметь право создавать/использовать эти идентификаторы и профили; скрипт запрашивает автоматическое provisioning.

Установка: подключить зарегистрированный iPhone к Mac и использовать Xcode/Apple Configurator. Обычное открытие IPA в приложении «Файлы» не устанавливает приложение. Профиль должен разрешать конкретное устройство и обе возможности: App Groups и Packet Tunnel. При необходимости включите Developer Mode по запросу iOS.

## Без Mac: облачная сборка GitHub

1. Загрузите проект в свой GitHub-репозиторий с папкой `.github/workflows`.
2. Откройте Actions → **Build unsigned iPhone IPA** → Run workflow.
3. Выберите `preview` для интерфейса или `full` для сборки с VPN-ядром.
4. После успешного выполнения скачайте artifact `AURA-…-UNSIGNED-needs-Apple-signing` и распакуйте его.

Внутри будет **скомпилированный, но неподписанный** `.ipa`. Это промежуточный файл, а не готовый установщик. Его нужно подписать своим сертификатом и подходящими provisioning profiles, включая отдельный профиль для расширения. Работоспособность VPN нельзя обещать при подписи, не поддерживающей Network Extension/App Groups.

Этот workflow не загружен на GitHub и не запускался в текущей сессии. Если компиляция выявит ошибки Swift или несовместимость Libbox, сначала нужно исправить их по журналу сборки. Нет автоматического выпуска в App Store или TestFlight.

Справка Apple: [дистрибуция на зарегистрированные устройства](https://developer.apple.com/documentation/xcode/distributing-your-app-to-registered-devices), [экспорт архива](https://help.apple.com/xcode/mac/current/en.lproj/dev23ea8b877.html).
