import SwiftUI

struct ServerGroupsView: View {
    @Environment(AppStore.self) private var store
    let collapsed: Bool
    var body: some View {
        LazyVStack(spacing: 16) {
            let local = store.snapshot.profiles.filter { $0.subscriptionID == nil }
            if !local.isEmpty { ServerGroupCard(title: "Мои серверы", subscription: nil, profiles: local, collapsed: collapsed) }
            ForEach(store.snapshot.subscriptions) { subscription in
                ServerGroupCard(title: subscription.name, subscription: subscription, profiles: store.snapshot.profiles.filter { $0.subscriptionID == subscription.id }, collapsed: collapsed)
            }
            if store.snapshot.profiles.isEmpty && store.snapshot.subscriptions.isEmpty {
                AuraCard { VStack(alignment: .leading, spacing: 8) { Text("Добавьте первое подключение").font(.headline); Text("Нажмите +, чтобы импортировать подписку, ссылку сервера или QR-код.").font(.subheadline).foregroundStyle(.secondary) }.frame(maxWidth: .infinity, alignment: .leading) }
            }
        }
    }
}

private struct ServerGroupCard: View {
    @Environment(AppStore.self) private var store
    let title: String
    let subscription: Subscription?
    let profiles: [ProxyProfile]
    let collapsed: Bool
    @State private var expanded = true
    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Button { withAnimation { expanded.toggle() } } label: {
                    HStack { Image(systemName: expanded ? "chevron.down" : "chevron.right"); VStack(alignment: .leading, spacing: 4) { Text(title).font(.headline).foregroundStyle(.primary); Text("\(profiles.count) серверов").font(.caption).foregroundStyle(.secondary) }; Spacer(minLength: 0) }
                }.buttonStyle(.plain).accessibilityLabel("\(expanded ? "Свернуть" : "Раскрыть") \(title)")
                if let subscription {
                    Button { Task { await store.refreshSubscription(subscription.id) } } label: { Image(systemName: "arrow.clockwise").frame(width: 36, height: 40) }.disabled(store.busy || store.tunnel.active).accessibilityLabel("Обновить подписку")
                }
                Button { Task { await store.measureGroup(profiles) } } label: { Image(systemName: "speedometer").frame(width: 36, height: 40) }.disabled(store.batchProbing || profiles.isEmpty).accessibilityLabel("Проверить все серверы группы")
            }.padding(16).background(store.accent.opacity(0.12))
            if expanded {
                if let subscription {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack { Label(subscription.url.scheme == "https" ? "HTTPS" : "HTTP", systemImage: subscription.url.scheme == "https" ? "lock.fill" : "lock.open"); Spacer(); if let date = subscription.updatedAt { Text(date.formatted(date: .abbreviated, time: .shortened)) } }.font(.caption2).foregroundStyle(.secondary)
                        if let error = subscription.lastError { Text(error).font(.caption).foregroundStyle(.orange) }
                        if let warning = subscription.lastWarning { Text(warning).font(.caption).foregroundStyle(.orange) }
                    }.padding(14)
                }
                ForEach(profiles) { profile in
                    Divider().opacity(0.35)
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
                        Button { Task { await store.measure(profile) } } label: {
                            VStack(alignment: .trailing, spacing: 4) {
                                if store.probing.contains(profile.id) { ProgressView().tint(store.accent) }
                                else if store.pingErrors[profile.id] != nil { Text("Нет ответа").foregroundStyle(.orange) }
                                else if let ms = profile.latencyMS { Text((store.snapshot.ping?.displayStatus == true && (profile.pingStatus ?? 0) > 0) ? "HTTP \(profile.pingStatus!)" : "\(ms) мс"); Text(profile.pingMethod ?? "TCP").foregroundStyle(.secondary) }
                                else { Image(systemName: "waveform.path") }
                            }.font(.system(.caption, design: .monospaced)).frame(minWidth: 62, minHeight: 44)
                        }.disabled(store.probing.contains(profile.id)).accessibilityLabel("Проверить \(profile.name)")
                    }.padding(store.snapshot.appearance.compact ? 12 : 16).background(store.snapshot.selectedID == profile.id ? store.accent.opacity(0.1) : .clear)
                }
                if profiles.isEmpty { Text("Список пуст. Обновите подписку.").font(.caption).foregroundStyle(.secondary).padding(18) }
            }
        }.auraGlass(radius: store.snapshot.appearance.cornerRadius)
            .onChange(of: collapsed) { withAnimation { expanded = !collapsed } }
    }
}
