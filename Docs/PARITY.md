# Статус относительно Happ

Цель продукта — функциональная альтернатива Happ с более гибким оформлением. На 30 октября 2026 **цель полного паритета не достигнута**. Полная Release-сборка с Libbox успешно скомпилирована в [GitHub Actions](https://github.com/ilbodw200/Aura/actions/runs/41033240198), сформирован неподписанный IPA для arm64/iOS 17+. Ниже «реализовано» не означает подтверждённую проверку на iPhone.

| Возможность | AURA сейчас | Что осталось |
|---|---|---|
| Нативный Liquid Glass | `glassEffect`, `GlassEffectContainer`, стандартная навигация | Визуальная проверка на iOS 26, Dynamic Type, VoiceOver |
| Оформление | 5 акцентов, 4 фона, темы, радиус, плотность, блоки главного экрана | Фотофоны, импорт тем, наборы иконок |
| VLESS / Reality, VMess, Trojan, SS, SOCKS5, Hysteria2 | Парсеры, генератор конфигурации и скомпилированный Libbox | Тесты каждого протокола на сервере |
| Транспорты | TCP, WS, gRPC, HTTP в генераторе | XHTTP, расширенные параметры актуального Xray |
| Ссылки / файлы / QR | Реализованы | Проверить камеру и большие подписки на устройстве |
| Подписки HTTPS | Предпросмотр, ручное обновление, сохранение прежних серверов при ошибке | Фоновое обновление, fallback URL, метаданные трафика/срока, настройки на подписку |
| Скрытые / зашифрованные подписки Happ | Явно отклоняются | Документированный совместимый формат либо согласованная интеграция провайдера |
| Routing | Домены, суффиксы, keywords, IPv4 CIDR, порядок и тест совпадений | IPv6 CIDR, геофайлы, импорт больших наборов, расширенные условия |
| DNS | Три DoH-профиля и перехват DNS в конфигурации | DNS leak-тесты IPv4/IPv6 и переключения сетей |
| VPN и автоподключение | NEPacketTunnelProvider, скомпилированный Libbox-адаптер, On Demand | Подпись, проверка устойчивости и восстановления |
| Пинг / диагностика | TCP handshake с таймаутом, локальный журнал | UDP/QUIC и URL-тест через прокси; реальные счётчики трафика |
| Резервные копии | AES-GCM, пароль PBKDF2, проверка перед заменой | Миграции схемы, проверка восстановления на втором устройстве |
| Экосистема | Пока одно iOS/iPadOS-приложение | App Intents, виджеты, Live Activity, Control Center, iCloud sync |

Нельзя маркировать готовность протокола по факту наличия парсера. Для каждого протокола должны пройти соединение, DNS, TCP/UDP, IPv6, смена Wi-Fi/сотовой сети, сон/пробуждение и тест памяти расширения. В обычной iOS-сборке нельзя обещать произвольную маршрутизацию по приложениям.

Проверенные исходные ориентиры:

- [Официальное описание Happ в App Store](https://apps.apple.com/us/app/happ-proxy-utility/id6504287215).
- [Документация импорта Happ](https://www.happ.su/main/faq/adding-configuration-subscription).
- [Apple Liquid Glass](https://developer.apple.com/documentation/SwiftUI/Applying-Liquid-Glass-to-custom-views).
- [Apple NEPacketTunnelProvider](https://developer.apple.com/documentation/networkextension/nepackettunnelprovider).
- [sing-box v1.11.15, Libbox API](https://github.com/SagerNet/sing-box/tree/v1.11.15/experimental/libbox).
