import SwiftUI

struct AuraBackdrop: View {
    @Environment(AppStore.self) private var store
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                (scheme == .dark ? Color(red: 0.025, green: 0.045, blue: 0.07) : Color(red: 0.94, green: 0.96, blue: 0.97))
                if store.snapshot.appearance.backdrop != "minimal" {
                    Ellipse().fill(store.accent.opacity(scheme == .dark ? 0.19 : 0.16))
                        .frame(width: geometry.size.width * 1.3, height: 320)
                        .blur(radius: 85).offset(x: 100, y: -geometry.size.height * 0.35)
                    Ellipse().fill(secondary.opacity(scheme == .dark ? 0.2 : 0.12))
                        .frame(width: 320, height: 460).blur(radius: 95)
                        .offset(x: -140, y: geometry.size.height * 0.1)
                }
            }
        }.ignoresSafeArea().allowsHitTesting(false)
    }
    private var secondary: Color {
        switch store.snapshot.appearance.backdrop { case "dusk": .pink; case "ocean": .cyan; default: .indigo }
    }
}

struct AuraCard<Content: View>: View {
    @Environment(AppStore.self) private var store
    @Environment(\.colorScheme) private var scheme
    @ViewBuilder var content: Content
    var body: some View {
        content.padding(store.snapshot.appearance.compact ? 16 : 21)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(scheme == .dark ? Color.white.opacity(0.045) : Color.white.opacity(0.75), in: RoundedRectangle(cornerRadius: store.snapshot.appearance.cornerRadius))
            .overlay { RoundedRectangle(cornerRadius: store.snapshot.appearance.cornerRadius).strokeBorder(.primary.opacity(0.07), lineWidth: 1) }
    }
}

struct GlassGroup<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        if #available(iOS 26.0, *) { GlassEffectContainer(spacing: 16) { content } }
        else { content }
    }
}

extension View {
    @ViewBuilder func auraGlass(tint: Color = .clear, interactive: Bool = false, radius: CGFloat = 24) -> some View {
        if #available(iOS 26.0, *) {
            self.glassEffect(.regular.tint(tint).interactive(interactive), in: .rect(cornerRadius: radius))
        } else {
            self.background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: radius))
                .overlay { RoundedRectangle(cornerRadius: radius).strokeBorder(.white.opacity(0.12)) }
        }
    }
    func auraPage() -> some View { scrollContentBackground(.hidden).background { AuraBackdrop() } }
}

struct SectionEyebrow: View {
    let title: String
    var trailing: String = ""
    var body: some View {
        HStack { Text(title.uppercased()).tracking(2); Spacer(); Text(trailing) }
            .font(.system(size: 10, weight: .semibold, design: .monospaced)).foregroundStyle(.secondary)
    }
}

struct EmptyCard: View {
    let icon: String
    let title: String
    let detail: String
    var body: some View {
        AuraCard {
            VStack(alignment: .leading, spacing: 12) {
                Image(systemName: icon).font(.largeTitle).foregroundStyle(.tint)
                Text(title).font(.title3.bold())
                Text(detail).font(.subheadline).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            }.padding(.vertical, 8)
        }
    }
}
