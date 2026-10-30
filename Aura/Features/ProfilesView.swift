import SwiftUI

struct ProfilesView: View {
    @Environment(AppStore.self) private var store
    let openSheet: (AppSheet) -> Void
    @State private var search = ""
    @State private var favoritesOnly = false
    @State private var sortLatency = false
    @State private var editing: ProxyProfile?
    @State private var editingSubscription: Subscription?
    private var profiles: [ProxyProfile] {
        let profiles = store.snapshot.profiles.filter { (!favoritesOnly || $0.favorite) && (search.isEmpty || $0.name.localizedCaseInsensitiveContains(search) || $0.kind.title.localizedCaseInsensitiveContains(search) || (store.snapshot.appearance.showHost && $0.host.localizedCaseInsensitiveContains(search))) }
        return sortLatency ? profiles.sorted { ($0.latencyMS ?? Int.max) < ($1.latencyMS ?? Int.max) } : profiles
    }
    var body: some View {
        List {
            if store.snapshot.profiles.isEmpty {
                Section {
                    EmptyCard(icon: "square.stack.3d.up", title: "Все ваши подключения.\nВ одном месте.", detail: "Добавьте свою подписку, ссылку конфигурации или QR-код. AURA не предоставляет серверы.")
                        .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
                    Button("Добавить сервер", systemImage: "plus") { openSheet(.importer) }
                    Button("Посмотреть с примерами", systemImage: "sparkles") { store.addExamples() }
                }
            }
            if !store.snapshot.subscriptions.isEmpty {
                Section("Подписки") {
                    ForEach(store.snapshot.subscriptions) { subscription in
                        HStack {
                            VStack(alignment: .leading, spacing: 5) {
                                Label(subscription.name, systemImage: subscription.url.scheme?.lowercased() == "https" ? "lock.fill" : "lock.slash").font(.headline)
                                if let error = subscription.lastError { Text(error).font(.caption).foregroundStyle(.orange).lineLimit(3) }
                                else if subscription.lastWarning != nil { Text("Загружены совместимые записи · есть предупреждения").font(.caption).foregroundStyle(.orange) }
                                else if let date = subscription.updatedAt { Text("Обновлено \(date.formatted(date: .abbreviated, time: .shortened))").font(.caption).foregroundStyle(.secondary) }
                                else { Text(store.busy ? "Загружаем серверы…" : "Ссылка сохранена · обновите список серверов").font(.caption).foregroundStyle(.secondary) }
                            }
                            Spacer()
                            Button { editingSubscription = subscription } label: { Image(systemName: "slider.horizontal.3").frame(width: 44, height: 44) }.buttonStyle(.borderless).accessibilityLabel("Настройки подписки \(subscription.name)")
                            Button { Task { await store.refreshSubscription(subscription.id) } } label: { Image(systemName: "arrow.clockwise").frame(width: 44, height: 44) }.buttonStyle(.borderless).disabled(store.busy).accessibilityLabel("Обновить \(subscription.name)")
                        }.swipeActions {
                            Button("Удалить", role: .destructive) { removeSubscription(subscription.id) }
                        }
                    }
                }
            }
            Section {
                ForEach(profiles) { profile in
                    Button { store.select(profile.id) } label: { ProfileRow(profile: profile) }
                        .buttonStyle(.plain)
                        .swipeActions(edge: .trailing) { Button("Удалить", role: .destructive) { store.remove(profile.id) }; Button("Изменить") { editing = profile }.tint(.blue) }
                        .swipeActions(edge: .leading) { Button { store.favorite(profile.id) } label: { Label("Избранное", systemImage: profile.favorite ? "star.slash" : "star") }.tint(.orange) }
                        .contextMenu {
                            Button("Проверить задержку", systemImage: "waveform.path") { Task { await store.measure(profile) } }
                            Button("Изменить название", systemImage: "pencil") { editing = profile }
                            Button("Избранное", systemImage: "star") { store.favorite(profile.id) }
                        }
                }
            } header: { HStack { Text("Профили"); Spacer(); Text("\(profiles.count)") } } footer: {
                Text("Метод измерения выбирается в настройках пинга. TCP и ICMP не подтверждают прохождение трафика через прокси.")
            }
            if profiles.isEmpty && !store.snapshot.profiles.isEmpty { Text("По этим условиям серверов нет.").foregroundStyle(.secondary) }
        }
        .auraPage().navigationTitle("Серверы")
        .searchable(text: $search, prompt: "Название, протокол, адрес")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Menu { Toggle("Только избранное", isOn: $favoritesOnly); Toggle("Сначала с меньшей задержкой", isOn: $sortLatency) } label: { Image(systemName: "line.3.horizontal.decrease") }.accessibilityLabel("Фильтры серверов")
            }
            ToolbarItem(placement: .topBarTrailing) { Button("Добавить", systemImage: "plus") { openSheet(.importer) } }
        }
        .sheet(item: $editing) { ProfileEditor(profile: $0) }
        .sheet(item: $editingSubscription) { SubscriptionEditor(subscription: $0) }
    }
    private func removeSubscription(_ id: UUID) {
        do {
            try store.requireDisconnected()
            store.snapshot.profiles.removeAll { $0.subscriptionID == id }
            store.snapshot.subscriptions.removeAll { $0.id == id }
            if !store.snapshot.profiles.contains(where: { $0.id == store.snapshot.selectedID }) { store.snapshot.selectedID = store.snapshot.profiles.first?.id }
            store.persist()
        } catch { store.message = error.localizedDescription }
    }
}

struct ProfileRow: View {
    @Environment(AppStore.self) private var store
    let profile: ProxyProfile
    var body: some View {
        HStack(spacing: 13) {
            Image(systemName: store.snapshot.selectedID == profile.id ? "checkmark.circle.fill" : "globe.europe.africa")
                .font(.title2).foregroundStyle(store.snapshot.selectedID == profile.id ? store.accent : .secondary).frame(width: 32)
            VStack(alignment: .leading, spacing: 5) {
                HStack { Text(profile.name).font(.subheadline.weight(.semibold)); if profile.favorite { Image(systemName: "star.fill").font(.caption2).foregroundStyle(.orange) } }
                Text(store.snapshot.appearance.showHost ? profile.subtitle : profile.kind.title).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer(minLength: 4)
            if let ms = profile.latencyMS { Text("\(ms) ms").font(.system(.caption, design: .monospaced)).foregroundStyle(store.accent) }
        }.padding(.vertical, store.snapshot.appearance.compact ? 3 : 9).contentShape(Rectangle())
    }
}

struct ProfileEditor: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State var profile: ProxyProfile
    @State private var error: String?
    var body: some View {
        NavigationStack {
            Form {
                Section("Название") { TextField("Мой сервер", text: $profile.name); Toggle("В избранном", isOn: $profile.favorite) }
                Section("Параметры") { LabeledContent("Протокол", value: profile.kind.title); LabeledContent("Сервер", value: store.snapshot.appearance.showHost ? profile.host : "Скрыт"); LabeledContent("Порт", value: String(profile.port)) }
                Section { Text("Для изменения ключей и транспорта импортируйте новую ссылку от провайдера.").font(.caption).foregroundStyle(.secondary) }
                if let error { Text(error).foregroundStyle(.red) }
            }.navigationTitle("Профиль").toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Сохранить") {
                    do { try store.requireDisconnected(); guard !profile.name.trimmingCharacters(in: .whitespaces).isEmpty else { throw AuraError.message("Введите название.") }; if let index = store.snapshot.profiles.firstIndex(where: { $0.id == profile.id }) { store.snapshot.profiles[index] = profile; store.persist() }; dismiss() } catch { self.error = error.localizedDescription }
                } }
            }
        }
    }
}
