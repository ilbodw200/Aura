import SwiftUI

struct DashboardView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion
    let openSheet: (AppSheet) -> Void
    let showServers: () -> Void
    var body: some View {
        ScrollView {
            GlassGroup {
            VStack(spacing: 22) {
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 5) {
                        Text("ВАШЕ СОЕДИНЕНИЕ. ВАШИ ПРАВИЛА.").font(.system(size: 9, weight: .medium, design: .monospaced)).tracking(1.7).foregroundStyle(.secondary)
                        Text("Пространство\nбез границ.").font(.system(size: 32, weight: .semibold, design: .rounded)).tracking(-1).fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                    Button { openSheet(.scanner) } label: { Image(systemName: "qrcode.viewfinder").font(.title3).frame(width: 48, height: 48).auraGlass(interactive: true, radius: 18) }.accessibilityLabel("Сканировать QR-код")
                }
                connectionOrb
                GlassGroup {
                    HStack(spacing: 12) {
                        Button { openSheet(.importer) } label: { Label("Добавить", systemImage: "plus").font(.subheadline.weight(.semibold)).frame(maxWidth: .infinity).padding(.vertical, 15).auraGlass(interactive: true, radius: 19) }
                        Button { openSheet(.diagnostics) } label: { Label("Диагностика", systemImage: "waveform.path.ecg").font(.subheadline.weight(.semibold)).frame(maxWidth: .infinity).padding(.vertical, 15).auraGlass(interactive: true, radius: 19) }
                    }
                }
                ForEach(store.snapshot.appearance.widgets.filter { !store.snapshot.appearance.hiddenWidgets.contains($0) }) { widget in
                    switch widget {
                    case .server: serverCard
                    case .routing: routingCard
                    case .favorites: favoritesCard
                    }
                }
                Text("PRIVATE BY DESIGN · MADE FOR YOU").font(.system(size: 9, design: .monospaced)).tracking(2).foregroundStyle(.tertiary).padding(.vertical, 8)
            }.padding(22).padding(.top, 8).frame(maxWidth: 620).frame(maxWidth: .infinity)
            }
        }
        .auraPage()
        .navigationTitle("AURA").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Image(systemName: "sparkle").foregroundStyle(store.accent).accessibilityHidden(true) } }
    }
    private var connectionOrb: some View {
        VStack(spacing: 16) {
            Button { Task { await store.toggleConnection() } } label: {
                ZStack {
                    Circle().fill(store.accent.opacity(0.12)).frame(width: 244, height: 244).blur(radius: 22)
                    ForEach(0..<3) { index in
                        Circle().stroke(store.accent.opacity(0.08 + Double(index) * 0.04), lineWidth: 1)
                            .frame(width: CGFloat(244 - index * 23), height: CGFloat(244 - index * 23))
                    }
                    Circle().fill(RadialGradient(colors: [store.accent.opacity(0.28), store.accent.opacity(0.03)], center: .topLeading, startRadius: 10, endRadius: 180)).frame(width: 170, height: 170)
                    Image(systemName: "power")
                        .font(.system(size: 48, weight: .light)).foregroundStyle(store.accent)
                        .frame(width: 156, height: 156).auraGlass(tint: store.accent.opacity(0.12), interactive: true, radius: 78)
                    if store.busy { ProgressView().offset(y: 54).tint(store.accent) }
                }.frame(height: 245)
                    .scaleEffect(store.tunnel.active ? 1.035 : 1)
                    .animation(systemReduceMotion || store.snapshot.appearance.reduceMotion ? nil : .spring(duration: 0.65), value: store.tunnel.active)
            }.buttonStyle(.plain).disabled(store.busy || store.tunnel.status == .disconnecting)
                .accessibilityLabel(store.tunnel.active ? "Отключить VPN" : "Подключить VPN")
            VStack(spacing: 7) {
                HStack(spacing: 7) { Circle().fill(store.tunnel.status == .connected ? store.accent : .secondary).frame(width: 6, height: 6); Text(store.tunnel.title).font(.title3.weight(.semibold)) }
                if let start = store.tunnel.connectedAt, store.tunnel.status == .connected {
                    Text(start, style: .timer).font(.system(.caption, design: .monospaced)).foregroundStyle(.secondary)
                } else { Text(store.selected == nil ? "Добавьте сервер, чтобы начать" : "Нажмите, чтобы подключиться").font(.caption).foregroundStyle(.secondary) }
            }
        }.padding(.bottom, 4)
    }
    private var serverCard: some View {
        Button(action: showServers) {
            AuraCard {
                VStack(alignment: .leading, spacing: 17) {
                    SectionEyebrow(title: "Активный профиль", trailing: "ВЫБРАТЬ ↗")
                    HStack(spacing: 13) {
                        Image(systemName: "network").font(.title2).foregroundStyle(store.accent).frame(width: 48, height: 48).background(store.accent.opacity(0.1), in: RoundedRectangle(cornerRadius: 16))
                        VStack(alignment: .leading, spacing: 5) {
                            Text(store.selected?.name ?? "Ваш первый сервер").font(.headline).foregroundStyle(.primary)
                            Text(store.selected.map { store.snapshot.appearance.showHost ? $0.subtitle : $0.kind.title } ?? "Импортируйте ссылку или подписку").font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        }
                        Spacer(minLength: 0)
                        Image(systemName: "chevron.right").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        }.buttonStyle(.plain)
    }
    private var routingCard: some View {
        AuraCard {
            VStack(alignment: .leading, spacing: 16) {
                SectionEyebrow(title: "Маршрутизация", trailing: "\(store.snapshot.rules.filter(\.enabled).count) ПРАВИЛ")
                HStack { Image(systemName: "arrow.triangle.branch").foregroundStyle(store.accent); Text(store.snapshot.mode.title).font(.headline); Spacer(); Text(store.snapshot.bypassLAN ? "LAN напрямую" : "LAN через VPN").font(.caption).foregroundStyle(.secondary) }
            }
        }
    }
    private var favoritesCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionEyebrow(title: "Быстрый выбор", trailing: "ИЗБРАННОЕ")
            if store.favorites.isEmpty { Text("Отметьте сервер звёздочкой — он появится здесь.").font(.caption).foregroundStyle(.secondary).padding(.vertical, 7) }
            else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(store.favorites) { profile in
                            Button { store.select(profile.id) } label: {
                                Label(profile.name, systemImage: store.snapshot.selectedID == profile.id ? "checkmark.circle.fill" : "star")
                                    .font(.caption.weight(.medium)).padding(13).auraGlass(tint: store.snapshot.selectedID == profile.id ? store.accent.opacity(0.15) : .clear, interactive: true, radius: 17)
                            }
                        }
                    }.padding(.vertical, 4)
                }
            }
        }
    }
}

#Preview { NavigationStack { DashboardView(openSheet: { _ in }, showServers: {}) }.environment(AppStore()) }
