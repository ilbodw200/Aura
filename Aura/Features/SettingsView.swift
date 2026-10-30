import SwiftUI

struct SettingsView: View {
    @Environment(AppStore.self) private var store
    let openSheet: (AppSheet) -> Void
    @State private var hwid = ""
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
            Section("Устройство и HWID") {
                Toggle("Отправлять HWID по умолчанию", isOn: Binding(get: { store.snapshot.sendHWID ?? false }, set: { store.snapshot.sendHWID = $0; store.persist() }))
                if !hwid.isEmpty {
                    LabeledContent("HWID AURA") { Text(hwid).font(.system(.caption, design: .monospaced)).textSelection(.enabled) }
                    Button("Скопировать HWID", systemImage: "doc.on.doc") { UIPasteboard.general.string = hwid }
                    ShareLink(item: hwid) { Label("Поделиться HWID", systemImage: "square.and.arrow.up") }
                }
                Text("Это устойчивый идентификатор AURA в Связке ключей, а не серийный номер телефона. При включении сервер выбранной подписки получает HWID, модель и версию iOS. Для существующей подписки откройте её настройки во вкладке «Серверы» и обновите список.").font(.caption).foregroundStyle(.secondary)
            }
            Section("Инструменты") {
                Button("Настройки пинга", systemImage: "speedometer") { openSheet(.ping) }
                Button("Диагностика", systemImage: "waveform.path.ecg") { openSheet(.diagnostics) }
                Button("Зашифрованная резервная копия", systemImage: "externaldrive.badge.shield.half.filled") { openSheet(.backup) }
            }
            Section("О приложении") {
                LabeledContent("AURA", value: "1.0.1")
                LabeledContent("Интерфейс", value: "SwiftUI · Liquid Glass")
                Text("VPN-ядро входит в полную сборку. App Group для подключения не требуется, но подпись приложения и расширения должна разрешать Network Extension. Полное соответствие Happ пока не достигнуто.").font(.caption).foregroundStyle(.secondary)
                Link("Исходный код sing-box и лицензия", destination: URL(string: "https://github.com/SagerNet/sing-box")!)
            }
        }.auraPage().navigationTitle("Настройки").onDisappear { store.persist() }
            .task { do { hwid = try DeviceIdentity.identifier() } catch { store.message = error.localizedDescription } }
    }
}
