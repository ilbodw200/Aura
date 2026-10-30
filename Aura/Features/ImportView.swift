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
    @State private var allowHTTP = false
    @State private var verifiedURL: URL?
    @State private var warnings: [String] = []
    private var subscriptionURL: URL? { let text = input.trimmingCharacters(in: .whitespacesAndNewlines); guard !text.contains("\n"), let url = URL(string: text), ["https", "http"].contains(url.scheme?.lowercased() ?? ""), url.host?.isEmpty == false else { return nil }; return url }
    private var secureSubscription: Bool { subscriptionURL?.scheme?.lowercased() == "https" }
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Одно место для всех ваших серверов.").font(.title2.bold()).padding(.vertical, 10)
                    TextEditor(text: $input).frame(minHeight: 140).font(.system(.footnote, design: .monospaced))
                        .autocorrectionDisabled().textInputAutocapitalization(.never).accessibilityLabel("Ссылка конфигурации или HTTPS-подписки")
                        .onChange(of: input) { preview = []; warnings = []; verifiedURL = nil; allowHTTP = false; error = nil }
                } header: { Text("Ссылка или список конфигураций") } footer: { Text("VLESS / Reality · VMess · Trojan · Shadowsocks · SOCKS5 · Hysteria2. Собственные зашифрованные форматы Happ пока не поддерживаются.") }
                Section {
                    Button("Вставить из буфера", systemImage: "doc.on.clipboard") { input = UIPasteboard.general.string ?? "" }
                    Button("Сканировать QR-код", systemImage: "qrcode.viewfinder") { scanner = true }
                    Button("Открыть текстовый файл", systemImage: "doc.text") { fileImporter = true }
                }
                if subscriptionURL != nil {
                    Section("Подписка") {
                        TextField("Название подписки", text: $name)
                        Label(secureSubscription ? "HTTPS · защищённое соединение" : "HTTP · без шифрования", systemImage: secureSubscription ? "lock.fill" : "lock.slash")
                            .foregroundStyle(secureSubscription ? .green : .orange)
                        if secureSubscription {
                            Text("Можно добавить сразу. Сертификат и формат ответа проверяются при загрузке серверов; предварительная проверка не обязательна.").font(.caption).foregroundStyle(.secondary)
                            Button("Добавить подписку") { commit() }.fontWeight(.semibold).disabled(isLoading)
                        } else {
                            Text("HTTP передаёт ссылку и конфигурации без шифрования. Для сохранения нужно принять предупреждение и проверить доступность и формат. Успешная проверка не добавляет шифрование.").font(.caption).foregroundStyle(.orange)
                            Toggle("Разрешаю загрузку по HTTP для этой подписки", isOn: $allowHTTP)
                                .onChange(of: allowHTTP) { preview = []; verifiedURL = nil }
                        }
                        Toggle("Отправлять HWID провайдеру", isOn: $sendHWID)
                            .onChange(of: sendHWID) { preview = []; verifiedURL = nil }
                            .disabled(!secureSubscription)
                        Text("Передаёт HWID AURA, модель и версию iOS только серверу этой подписки. Может требоваться для регистрации устройства у провайдера.").font(.caption).foregroundStyle(.secondary)
                        Text(secureSubscription ? "После добавления список серверов обновится автоматически. Ссылка остаётся сохранённой даже при ошибке загрузки." : "HWID и данные устройства по HTTP не отправляются.").font(.caption).foregroundStyle(.secondary)
                    }
                }
                if let error { Section { Text(error).foregroundStyle(.red) } }
                if !warnings.isEmpty { Section("Не все записи поддерживаются") { ForEach(Array(warnings.prefix(5).enumerated()), id: \.offset) { _, text in Text(text).font(.caption).foregroundStyle(.orange) } } }
                if !preview.isEmpty {
                    Section("Будет добавлено: \(preview.count)") { ForEach(preview) { profile in VStack(alignment: .leading) { Text(profile.name); Text(profile.kind.title).font(.caption).foregroundStyle(.secondary) } } }
                    if !secureSubscription { Section { Button(subscriptionURL == nil ? "Добавить профили" : "Сохранить проверенную HTTP-подписку") { commit() }.fontWeight(.semibold) } }
                } else {
                    Section { Button { Task { await inspect() } } label: { HStack { Text(isLoading ? "Загружаем…" : secureSubscription ? "Проверить серверы сейчас (необязательно)" : subscriptionURL != nil ? "Проверить доступность и формат" : "Проверить конфигурацию"); Spacer(); if isLoading { ProgressView() } } }.disabled(input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isLoading || (subscriptionURL != nil && !secureSubscription && !allowHTTP)) }
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
        let candidateHTTP = allowHTTP
        do {
            try store.requireDisconnected()
            let profiles: [ProxyProfile]
            if let url = subscriptionURL {
                let result = try await SubscriptionService.fetch(url, metadata: secureSubscription && candidateHWID ? try DeviceIdentity.metadata() : nil, allowHTTP: candidateHTTP)
                guard candidate == input, candidateHWID == sendHWID, candidateHTTP == allowHTTP else { return }
                profiles = result.profiles; warnings = result.warnings; verifiedURL = url
            }
            else { profiles = try ProfileParser.parse(candidate) }
            guard candidate == input, candidateHWID == sendHWID else { return }
            preview = profiles
        } catch { self.error = subscriptionURL != nil ? SubscriptionService.message(for: error) : error.localizedDescription }
    }
    private func commit() {
        do {
            try store.requireDisconnected()
            let profiles = preview
            if let url = subscriptionURL {
                let contents = preview.isEmpty ? nil : SubscriptionContents(profiles: preview, warnings: warnings)
                let id = try store.addSubscription(name: name, url: url, sendHWID: sendHWID, allowHTTP: allowHTTP, verifiedURL: verifiedURL, contents: contents)
                NotificationCenter.default.post(name: .auraSubscriptionAdded, object: nil)
                dismiss()
                if contents == nil { Task { await store.refreshSubscription(id, reportError: false) } }
                return
            }
            try store.add(profiles)
            dismiss()
        } catch { self.error = error.localizedDescription }
    }
}
