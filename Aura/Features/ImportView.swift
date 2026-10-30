import SwiftUI
import UniformTypeIdentifiers

struct ImportView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var input = ""
    @State private var name = "Моя подписка"
    @State private var error: String?
    @State private var preview: [ProxyProfile] = []
    @State private var scanner = false
    @State private var fileImporter = false
    @State private var isLoading = false
    @State private var sendHWID = false
    private var subscriptionURL: URL? { let text = input.trimmingCharacters(in: .whitespacesAndNewlines); guard !text.contains("\n"), let url = URL(string: text), url.scheme == "https" else { return nil }; return url }
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Одно место для всех ваших серверов.").font(.title2.bold()).padding(.vertical, 10)
                    TextEditor(text: $input).frame(minHeight: 140).font(.system(.footnote, design: .monospaced))
                        .autocorrectionDisabled().textInputAutocapitalization(.never).accessibilityLabel("Ссылка конфигурации или HTTPS-подписки")
                        .onChange(of: input) { preview = []; error = nil }
                } header: { Text("Ссылка или список конфигураций") } footer: { Text("VLESS / Reality · VMess · Trojan · Shadowsocks · SOCKS5 · Hysteria2. Собственные зашифрованные форматы Happ пока не поддерживаются.") }
                Section {
                    Button("Вставить из буфера", systemImage: "doc.on.clipboard") { input = UIPasteboard.general.string ?? "" }
                    Button("Сканировать QR-код", systemImage: "qrcode.viewfinder") { scanner = true }
                    Button("Открыть текстовый файл", systemImage: "doc.text") { fileImporter = true }
                }
                if subscriptionURL != nil {
                    Section("Подписка") {
                        TextField("Название подписки", text: $name)
                        Toggle("Отправлять HWID провайдеру", isOn: $sendHWID)
                            .onChange(of: sendHWID) { preview = [] }
                        Text("Передаёт HWID AURA, модель и версию iOS только серверу этой подписки. Может требоваться для регистрации устройства у провайдера.").font(.caption).foregroundStyle(.secondary)
                        Text("После проверки AURA загрузит список серверов по этой ссылке. Адрес хранится в зашифрованном хранилище.").font(.caption).foregroundStyle(.secondary)
                    }
                }
                if let error { Section { Text(error).foregroundStyle(.red) } }
                if !preview.isEmpty {
                    Section("Будет добавлено: \(preview.count)") { ForEach(preview) { profile in VStack(alignment: .leading) { Text(profile.name); Text(profile.kind.title).font(.caption).foregroundStyle(.secondary) } } }
                    Section { Button("Добавить профили") { commit() }.fontWeight(.semibold) }
                } else {
                    Section { Button { Task { await inspect() } } label: { HStack { Text(isLoading ? "Проверяем…" : "Проверить конфигурацию"); Spacer(); if isLoading { ProgressView() } } }.disabled(input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isLoading) }
                }
            }.navigationTitle("Добавить подключение").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Закрыть") { dismiss() } } }
                .sheet(isPresented: $scanner) { ScannerSheet { input = $0 } }
                .fileImporter(isPresented: $fileImporter, allowedContentTypes: [.plainText], allowsMultipleSelection: false) { result in
                    do {
                        guard let url = try result.get().first else { return }
                        let accessed = url.startAccessingSecurityScopedResource(); defer { if accessed { url.stopAccessingSecurityScopedResource() } }
                        let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
                        guard size <= 2_000_000 else { throw AuraError.message("Файл превышает 2 МБ.") }
                        input = try String(contentsOf: url, encoding: .utf8)
                    } catch { self.error = error.localizedDescription }
                }
                .onAppear { sendHWID = store.snapshot.sendHWID ?? false; if let link = PendingImport.link { input = link; PendingImport.link = nil } }
                .onReceive(NotificationCenter.default.publisher(for: .auraImportLink)) { notification in if let link = notification.object as? String { input = link } }
        }
    }
    private func inspect() async {
        isLoading = true; error = nil; defer { isLoading = false }
        let candidate = input
        let candidateHWID = sendHWID
        do {
            try store.requireDisconnected()
            let profiles: [ProxyProfile]
            if let url = subscriptionURL { profiles = try await SubscriptionService.fetch(url, metadata: candidateHWID ? try DeviceIdentity.metadata() : nil) }
            else { profiles = try ProfileParser.parse(candidate) }
            guard candidate == input, candidateHWID == sendHWID else { return }
            preview = profiles
        } catch { self.error = subscriptionURL != nil ? "Не удалось проверить подписку. Проверьте HTTPS-адрес и поддерживаемые форматы конфигураций." : error.localizedDescription }
    }
    private func commit() {
        do {
            try store.requireDisconnected()
            var profiles = preview
            var subscription: Subscription?
            if let url = subscriptionURL {
                guard !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw AuraError.message("Введите название подписки.") }
                let new = Subscription(name: name, url: url, updatedAt: .now, sendHWID: sendHWID)
                subscription = new
                for index in profiles.indices { profiles[index].subscriptionID = new.id }
            }
            try store.add(profiles)
            if let subscription { store.snapshot.subscriptions.append(subscription); store.persist() }
            dismiss()
        } catch { self.error = error.localizedDescription }
    }
}
