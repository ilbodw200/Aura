import SwiftUI

struct SettingsView: View {
    @Environment(AppStore.self) private var store
    let openSheet: (AppSheet) -> Void
    var body: some View {
        @Bindable var store = store
        Form {
            Section("Подключение") {
                Toggle("Подключаться по требованию", isOn: $store.snapshot.onDemand)
                Text("iOS будет запускать VPN при сетевой активности после первого успешного подключения. Кнопка отключения выключает это правило до следующего подключения.").font(.caption).foregroundStyle(.secondary)
                Picker("DNS через туннель", selection: $store.snapshot.dns) {
                    Text("Cloudflare · DoH").tag("https://1.1.1.1/dns-query")
                    Text("Google · DoH").tag("https://8.8.8.8/dns-query")
                    Text("Quad9 · DoH").tag("https://9.9.9.9/dns-query")
                }
            }.disabled(store.tunnel.active)
            Section("Конфиденциальность") {
                Toggle("Скрывать в переключателе приложений", isOn: $store.snapshot.hideInSwitcher)
                Label("Конфигурации хранятся в зашифрованном файле. Ключ — в системной Связке ключей.", systemImage: "lock.shield").font(.caption).foregroundStyle(.secondary)
                Label("В приложении нет аналитики и рекламных SDK. Провайдер подписки и сервер DNS получают запросы, необходимые для работы.", systemImage: "eye.slash").font(.caption).foregroundStyle(.secondary)
            }
            Section("Инструменты") {
                Button("Диагностика", systemImage: "waveform.path.ecg") { openSheet(.diagnostics) }
                Button("Зашифрованная резервная копия", systemImage: "externaldrive.badge.shield.half.filled") { openSheet(.backup) }
            }
            Section("О приложении") {
                LabeledContent("AURA", value: "0.1 · Developer Preview")
                LabeledContent("Интерфейс", value: "SwiftUI · Liquid Glass")
                Text("Это версия в разработке. Перед использованием VPN требуется собрать ядро sing-box и проверить соединение на устройстве. Полное соответствие Happ пока не достигнуто.").font(.caption).foregroundStyle(.secondary)
                Link("Исходный код sing-box и лицензия", destination: URL(string: "https://github.com/SagerNet/sing-box")!)
            }
        }.auraPage().navigationTitle("Настройки").onDisappear { store.persist() }
    }
}
