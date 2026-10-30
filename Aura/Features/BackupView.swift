import SwiftUI
import UniformTypeIdentifiers

struct AuraBackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.data] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

struct BackupView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var password = ""
    @State private var document: AuraBackupDocument?
    @State private var exporting = false
    @State private var importing = false
    @State private var restoreCandidate: AppSnapshot?
    @State private var error: String?
    @State private var working = false
    var body: some View {
        NavigationStack {
            Form {
                Section("Пароль копии") {
                    SecureField("Минимум 12 символов", text: $password).textContentType(.newPassword)
                    Text("Копия включает ключи серверов, подписки, правила и оформление. AES-256-GCM, PBKDF2-SHA256, 210 000 итераций. Пароль не сохраняется; восстановить его нельзя.").font(.caption).foregroundStyle(.secondary)
                }
                Section {
                    Button("Сохранить зашифрованную копию", systemImage: "square.and.arrow.up") { createBackup() }.disabled(working || password.count < 12)
                    Button("Восстановить из файла", systemImage: "arrow.down.doc") { importing = true }.disabled(working || password.isEmpty || store.tunnel.active)
                    if working { ProgressView("Обрабатываем копию…") }
                }
                if let error { Text(error).font(.caption).foregroundStyle(.red) }
            }.navigationTitle("Резервная копия").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Закрыть") { dismiss() } } }
                .fileExporter(isPresented: $exporting, document: document, contentType: .data, defaultFilename: "AURA.aurabackup") { result in
                    if case .failure = result { error = "Не удалось сохранить файл." }
                    document = nil
                }
                .fileImporter(isPresented: $importing, allowedContentTypes: [.data], allowsMultipleSelection: false) { result in
                    do {
                        guard let url = try result.get().first else { return }
                        let accessed = url.startAccessingSecurityScopedResource(); defer { if accessed { url.stopAccessingSecurityScopedResource() } }
                        let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
                        guard size <= 8_000_000 else { throw AuraError.message("Копия превышает 8 МБ.") }
                        let data = try Data(contentsOf: url)
                        let secret = password
                        working = true
                        Task {
                            defer { working = false }
                            do { restoreCandidate = try await Task.detached(priority: .userInitiated) { try BackupService.restore(data, password: secret) }.value }
                            catch { self.error = "Не удалось открыть копию. Проверьте пароль и файл." }
                        }
                    } catch { self.error = "Не удалось прочитать файл копии." }
                }
                .confirmationDialog("Заменить текущие данные?", isPresented: Binding(get: { restoreCandidate != nil }, set: { if !$0 { restoreCandidate = nil } }), titleVisibility: .visible) {
                    Button("Восстановить \(restoreCandidate?.profiles.count ?? 0) профилей", role: .destructive) {
                        do { try store.requireDisconnected(); if let restored = restoreCandidate { store.snapshot = restored; store.persist(); restoreCandidate = nil; dismiss() } } catch { self.error = error.localizedDescription }
                    }
                    Button("Отмена", role: .cancel) { restoreCandidate = nil }
                } message: { Text("Профили, подписки, правила и оформление будут заменены содержимым копии.") }
                .onDisappear { password = ""; document = nil }
        }
    }
    private func createBackup() {
        let snapshot = store.snapshot
        let secret = password
        working = true; error = nil
        Task {
            defer { working = false }
            do {
                let data = try await Task.detached(priority: .userInitiated) { try BackupService.export(snapshot, password: secret) }.value
                document = AuraBackupDocument(data: data); exporting = true
            } catch { self.error = "Не удалось создать зашифрованную копию." }
        }
    }
}
