import SwiftUI

struct SubscriptionEditor: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State var subscription: Subscription
    @State private var error: String?
    @State private var hwid = ""
    var body: some View {
        NavigationStack {
            Form {
                Section("Подписка") { TextField("Название", text: $subscription.name) }
                Section {
                    Label(subscription.url.scheme == "https" ? "HTTPS · TLS при загрузке" : "HTTP · разрешён после дополнительной проверки", systemImage: subscription.url.scheme == "https" ? "lock.fill" : "lock.slash")
                    if let error = subscription.lastError { Text(error).foregroundStyle(.orange) }
                    if let warning = subscription.lastWarning { Text(warning).font(.caption).foregroundStyle(.orange) }
                }
                Section("Привязка устройства") {
                    Picker("Отправка HWID", selection: Binding(get: { subscription.sendHWID.map { $0 ? 1 : 2 } ?? 0 }, set: { subscription.sendHWID = $0 == 0 ? nil : $0 == 1 })) {
                        Text("Как в настройках приложения").tag(0)
                        Text("Отправлять HWID").tag(1)
                        Text("Не отправлять").tag(2)
                    }.disabled(subscription.url.scheme != "https")
                    if !hwid.isEmpty {
                        Text(hwid).font(.system(.caption, design: .monospaced)).textSelection(.enabled)
                        Button("Скопировать HWID") { UIPasteboard.general.string = hwid }
                    }
                    Text("После сохранения нажмите кнопку обновления подписки. Сервер получит X-HWID и сведения об устройстве, только если отправка включена. HWID AURA отличается от идентификатора другого клиента: провайдеру может понадобиться новая привязка.").font(.caption).foregroundStyle(.secondary)
                }
                if let error { Text(error).foregroundStyle(.red) }
            }.navigationTitle("Настройки подписки").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) { Button("Сохранить") {
                        guard !subscription.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { error = "Введите название."; return }
                        if let index = store.snapshot.subscriptions.firstIndex(where: { $0.id == subscription.id }) { store.snapshot.subscriptions[index] = subscription; store.persist() }
                        dismiss()
                    } }
                }
                .task { do { hwid = try DeviceIdentity.identifier() } catch { self.error = error.localizedDescription } }
        }
    }
}
