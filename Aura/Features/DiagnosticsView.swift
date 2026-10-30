import SwiftUI

struct DiagnosticsView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var checking = false
    @State private var configResult = ""
    var body: some View {
        NavigationStack {
            List {
                Section("Состояние") {
                    LabeledContent("VPN", value: store.tunnel.title)
                    LabeledContent("Профилей", value: "\(store.snapshot.profiles.count)")
                    LabeledContent("Активных правил", value: "\(store.snapshot.rules.filter(\.enabled).count)")
                    if let selected = store.selected { LabeledContent("Протокол", value: selected.kind.title) }
                }
                Section("Проверки") {
                    Button(checking ? "Проверяем…" : "Измерить TCP-задержку", systemImage: "waveform.path") {
                        guard let profile = store.selected else { return }
                        checking = true
                        Task { await store.measure(profile); checking = false }
                    }.disabled(checking || store.selected == nil)
                    if let selected = store.selected, let ms = selected.latencyMS {
                        LabeledContent("Последний результат", value: "\(ms) ms")
                        if let date = selected.latencyCheckedAt { Text(date.formatted()).font(.caption).foregroundStyle(.secondary) }
                    }
                    Text("TCP-проверка обращается к адресу сервера. Для Hysteria2 нужен отдельный UDP-тест; проверка не подтверждает работу VPN.").font(.caption).foregroundStyle(.secondary)
                    Button("Проверить конфигурацию туннеля", systemImage: "checkmark.shield") {
                        do { guard let profile = store.selected else { throw AuraError.message("Сначала выберите сервер.") }; _ = try ConfigBuilder.build(profile: profile, snapshot: store.snapshot); configResult = "Конфигурация JSON сформирована. Проверка ядром и тест соединения ещё необходимы." }
                        catch { configResult = error.localizedDescription }
                    }
                    if !configResult.isEmpty { Text(configResult).font(.caption) }
                }
                Section("Журнал приложения · без ключей и адресов") {
                    if store.events.isEmpty { Text("Событий пока нет.").foregroundStyle(.secondary) }
                    ForEach(Array(store.events.enumerated()), id: \.offset) { _, event in Text(event).font(.system(.caption, design: .monospaced)).textSelection(.enabled) }
                    if !store.events.isEmpty { ShareLink(item: store.events.joined(separator: "\n")) { Label("Экспортировать журнал", systemImage: "square.and.arrow.up") }; Button("Очистить журнал") { store.events = [] } }
                }
            }.navigationTitle("Диагностика").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Готово") { dismiss() } } }
        }
    }
}
