import SwiftUI

struct PingSettingsView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var settings = PingSettings()
    var body: some View {
        NavigationStack {
            Form {
                Section("Протокол пинга") {
                    ForEach(PingMethod.allCases) { method in
                        Button { settings.method = method } label: {
                            HStack { Text(method.title).foregroundStyle(.primary); Spacer(); if settings.method == method { Image(systemName: "checkmark").foregroundStyle(store.accent) } }
                        }
                    }
                }
                Section {
                    Picker("Режим через прокси", selection: $settings.mode) { ForEach(PingMode.allCases) { Text($0.rawValue.capitalized).tag($0) } }.disabled(!settings.method.viaProxy)
                } footer: {
                    Text("Default — один запрос. Double — лучший из двух запросов с отдельными соединениями. Keepalive — два запроса с повторным использованием соединения, если сервер его поддерживает. Таймаут ограничивает весь тест.")
                }
                Section("Таймаут · \(Int(settings.timeout)) с") { Slider(value: $settings.timeout, in: 5...15, step: 1).tint(store.accent) }
                Section("URL теста") { TextField("https://…", text: $settings.url).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled().disabled(!settings.method.viaProxy) }
                Section("Отображение") { Toggle("Показывать HTTP-статус", isOn: $settings.displayStatus) }
                Section { Text("GET/HEAD проходят через выбранный профиль без переключения VPN. TCP проверяет порт сервера. ICMP использует IPv4 и может блокироваться сетью или системой. Прямые проверки используют текущий сетевой маршрут.").font(.caption).foregroundStyle(.secondary) }
            }.navigationTitle("Настройки пинга").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Сохранить") { do { try settings.validate(); store.snapshot.ping = settings; store.persist(); dismiss() } catch { store.message = error.localizedDescription } } }; ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } } }
        }.onAppear { settings = store.snapshot.ping ?? PingSettings() }
    }
}
