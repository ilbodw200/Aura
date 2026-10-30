import SwiftUI

struct AppearanceView: View {
    @Environment(AppStore.self) private var store
    private let accents: [(String, Color, String)] = [("mint", Color(red: 0.43, green: 0.94, blue: 0.77), "Мята"), ("blue", .blue, "Лазурь"), ("violet", .purple, "Аметист"), ("rose", .pink, "Роза"), ("amber", .orange, "Янтарь")]
    var body: some View {
        @Bindable var store = store
        List {
            Section {
                GlassGroup {
                VStack(alignment: .leading, spacing: 18) {
                    SectionEyebrow(title: "Ваш стиль", trailing: "LIVE PREVIEW")
                    HStack(spacing: 16) {
                        Image(systemName: "power").font(.system(size: 31, weight: .light)).foregroundStyle(store.accent).frame(width: 80, height: 80).auraGlass(tint: store.accent.opacity(0.16), interactive: false, radius: 40)
                        VStack(alignment: .leading, spacing: 7) { Text("Всё в вашем ритме.").font(.title3.bold()); Text("Оформление меняется сразу").font(.caption).foregroundStyle(.secondary) }
                    }
                }.padding(.vertical, 12)
                }
            }.listRowBackground(Color.clear)
            Section("Акцент") {
                HStack {
                    ForEach(accents, id: \.0) { id, color, title in
                        Button { store.snapshot.appearance.accent = id } label: {
                            Circle().fill(color.gradient).frame(width: 42, height: 42).overlay { if store.snapshot.appearance.accent == id { Image(systemName: "checkmark").font(.headline.bold()).foregroundStyle(.black) } }.frame(maxWidth: .infinity, minHeight: 50)
                        }.buttonStyle(.plain).accessibilityLabel(title).accessibilityAddTraits(store.snapshot.appearance.accent == id ? .isSelected : [])
                    }
                }
            }
            Section("Атмосфера") {
                Picker("Фон", selection: $store.snapshot.appearance.backdrop) { Text("Северное сияние").tag("aurora"); Text("Океан").tag("ocean"); Text("Сумерки").tag("dusk"); Text("Минимализм").tag("minimal") }
                Picker("Тема", selection: $store.snapshot.appearance.colorScheme) { Text("Тёмная").tag("dark"); Text("Светлая").tag("light"); Text("Как в системе").tag("system") }
                VStack(alignment: .leading) { HStack { Text("Скругление карточек"); Spacer(); Text("\(Int(store.snapshot.appearance.cornerRadius))").monospacedDigit().foregroundStyle(.secondary) }; Slider(value: $store.snapshot.appearance.cornerRadius, in: 12...36, step: 2).accessibilityLabel("Скругление карточек") }
                Toggle("Компактные карточки", isOn: $store.snapshot.appearance.compact)
                Toggle("Уменьшить движение", isOn: $store.snapshot.appearance.reduceMotion)
                Toggle("Показывать адреса серверов", isOn: $store.snapshot.appearance.showHost)
            }
            Section {
                ForEach(store.snapshot.appearance.widgets) { widget in
                    Toggle(widget.title, isOn: Binding(get: { !store.snapshot.appearance.hiddenWidgets.contains(widget) }, set: { visible in
                        if visible { store.snapshot.appearance.hiddenWidgets.removeAll { $0 == widget } }
                        else if !store.snapshot.appearance.hiddenWidgets.contains(widget) { store.snapshot.appearance.hiddenWidgets.append(widget) }
                    }))
                }.onMove { store.snapshot.appearance.widgets.move(fromOffsets: $0, toOffset: $1) }
            } header: { Text("Блоки главного экрана") } footer: { Text("Скрывайте ненужные блоки и меняйте порядок через «Править». На iOS 26 элементы управления используют настоящий Liquid Glass. На iOS 17–18 — системные материалы.") }
            Section { Button("Восстановить оформление") { store.snapshot.appearance = Appearance() } }
        }.auraPage().navigationTitle("Студия оформления")
            .toolbar { ToolbarItem(placement: .topBarTrailing) { EditButton() } }
    }
}
