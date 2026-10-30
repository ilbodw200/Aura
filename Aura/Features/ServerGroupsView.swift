import SwiftUI

struct ServerGroupsView: View {
    @Environment(AppStore.self) private var store
    @Binding var collapsedGroups: Set<String>
    var body: some View {
        let local = store.snapshot.profiles.filter { $0.subscriptionID == nil }
        if !local.isEmpty { group(id: "local", title: "Мои серверы", subscription: nil, profiles: local) }
        ForEach(store.snapshot.subscriptions) { subscription in
            group(id: subscription.id.uuidString, title: subscription.name, subscription: subscription, profiles: store.snapshot.profiles.filter { $0.subscriptionID == subscription.id })
        }
        if store.snapshot.profiles.isEmpty && store.snapshot.subscriptions.isEmpty {
            AuraCard {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Добавьте первое подключение").font(.headline)
                    Text("Нажмите +, чтобы импортировать подписку, ссылку сервера или QR-код.").font(.subheadline).foregroundStyle(.secondary)
                }
            }.listRowBackground(Color.clear).listRowSeparator(.hidden)
        }
    }
    private func group(id: String, title: String, subscription: Subscription?, profiles: [ProxyProfile]) -> some View {
        Section {
            if !collapsedGroups.contains(id) {
                if let subscription {
                    ServerSubscriptionInfo(subscription: subscription)
                        .listRowBackground(Color.clear).listRowSeparator(.hidden)
                }
                ForEach(profiles) { profile in ServerProfileRow(profile: profile) }
                if profiles.isEmpty { Text("Список пуст. Обновите подписку.").font(.caption).foregroundStyle(.secondary).listRowBackground(Color.clear) }
            }
        } header: {
            HStack(spacing: 12) {
                Button {
                    if collapsedGroups.contains(id) { collapsedGroups.remove(id) }
                    else { collapsedGroups.insert(id) }
                } label: {
                    HStack {
                        Image(systemName: collapsedGroups.contains(id) ? "chevron.right" : "chevron.down")
                        VStack(alignment: .leading, spacing: 4) { Text(title).font(.headline); Text("\(profiles.count) серверов").font(.caption).foregroundStyle(.secondary) }
                        Spacer(minLength: 0)
                    }.foregroundStyle(.primary)
                }.accessibilityLabel("\(collapsedGroups.contains(id) ? "Раскрыть" : "Свернуть") \(title)")
                if let subscription {
                    Button { Task { await store.refreshSubscription(subscription.id) } } label: { Image(systemName: "arrow.clockwise").frame(width: 36, height: 44) }
                        .disabled(store.busy || store.tunnel.active).accessibilityLabel("Обновить подписку")
                }
                Button { Task { await store.measureGroup(profiles) } } label: { Image(systemName: "speedometer").frame(width: 36, height: 44) }
                    .disabled(store.batchProbing || profiles.isEmpty).accessibilityLabel("Проверить все серверы группы")
            }.buttonStyle(.plain).textCase(nil).padding(12).background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18))
        }.id(id)
    }
}

private struct ServerSubscriptionInfo: View {
    let subscription: Subscription
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let usage = subscription.usage {
                if let summary = usage.summary { Label(summary, systemImage: "chart.bar.fill").font(.caption) }
                if let date = usage.expire { Text("Истекает: \(date.formatted(date: .abbreviated, time: .omitted))").font(.caption).foregroundStyle(date < .now ? .orange : .secondary) }
            }
            HStack {
                Label(subscription.url.scheme == "https" ? "HTTPS" : "HTTP", systemImage: subscription.url.scheme == "https" ? "lock.fill" : "lock.open")
                Spacer()
                if let date = subscription.updatedAt { Text(date.formatted(date: .abbreviated, time: .shortened)) }
            }.font(.caption2).foregroundStyle(.secondary)
            if let error = subscription.lastError { Text(error).font(.caption).foregroundStyle(.orange) }
            if let warning = subscription.lastWarning { Text(warning).font(.caption).foregroundStyle(.orange) }
        }
    }
}

private struct ServerProfileRow: View {
    @Environment(AppStore.self) private var store
    let profile: ProxyProfile
    var body: some View {
                    HStack(spacing: 12) {
                        Button { store.select(profile.id) } label: {
                            HStack(spacing: 12) {
                                Image(systemName: store.snapshot.selectedID == profile.id ? "checkmark.circle.fill" : "globe").font(.title2).foregroundStyle(store.accent)
                                VStack(alignment: .leading, spacing: 5) {
                                    Text(profile.name).font(.headline).foregroundStyle(.primary).lineLimit(2)
                                    Text("\(profile.kind.title) | \((profile.query["type"] ?? "tcp").uppercased())\(profile.query["security"] == "reality" ? " | Reality" : "")").font(.caption).foregroundStyle(.secondary)
                                    if store.snapshot.appearance.showHost { Text("\(profile.host):\(profile.port)").font(.caption2).foregroundStyle(.tertiary).lineLimit(1) }
                                }
                                Spacer(minLength: 0)
                            }.contentShape(Rectangle())
                        }.buttonStyle(.plain)
                            .accessibilityIdentifier("server.select.\(profile.id.uuidString)")
                            .accessibilityValue(store.snapshot.selectedID == profile.id ? "Выбран" : "Не выбран")
                        Button { Task { await store.measure(profile) } } label: {
                            VStack(alignment: .trailing, spacing: 4) {
                                if store.probing.contains(profile.id) { ProgressView().tint(store.accent) }
                                else if store.pingErrors[profile.id] != nil { Text("Нет ответа").foregroundStyle(.orange) }
                                else if let ms = profile.latencyMS { Text((store.snapshot.ping?.displayStatus == true && (profile.pingStatus ?? 0) > 0) ? "HTTP \(profile.pingStatus!)" : "\(ms) мс"); Text(profile.pingMethod ?? "TCP").foregroundStyle(.secondary) }
                                else { Image(systemName: "waveform.path") }
                            }.font(.system(.caption, design: .monospaced)).frame(minWidth: 62, minHeight: 44)
                        }.buttonStyle(.plain).disabled(store.probing.contains(profile.id)).accessibilityLabel("Проверить \(profile.name)")
                    }.padding(store.snapshot.appearance.compact ? 12 : 16)
                        .background(store.snapshot.selectedID == profile.id ? store.accent.opacity(0.12) : Color.primary.opacity(0.035), in: RoundedRectangle(cornerRadius: 16))
                        .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                        .listRowSeparator(.hidden).listRowBackground(Color.clear)
    }
}
