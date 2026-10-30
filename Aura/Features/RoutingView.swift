import SwiftUI

struct RoutingView: View {
    @Environment(AppStore.self) private var store
    @State private var editor: RuleDraft?
    @State private var testHost = ""
    @State private var result = ""
    var body: some View {
        @Bindable var store = store
        List {
            Section {
                Picker("Режим", selection: $store.snapshot.mode) { ForEach(RoutingMode.allCases) { Text($0.title).tag($0) } }.pickerStyle(.segmented)
                Toggle("Локальная сеть напрямую", isOn: $store.snapshot.bypassLAN)
            } footer: { Text("Правила применяются сверху вниз. После первого совпадения проверка заканчивается. Изменения вступают в силу при следующем подключении.") }.disabled(store.tunnel.active)
            Section {
                ForEach($store.snapshot.rules) { $rule in
                    HStack {
                        Toggle(isOn: $rule.enabled) { VStack(alignment: .leading, spacing: 5) { Text(rule.value).font(.subheadline.weight(.medium)); Text("\(rule.kind.title) → \(rule.action.title)").font(.caption).foregroundStyle(.secondary) } }
                    }.contextMenu { Button("Изменить", systemImage: "pencil") { editor = RuleDraft(rule: rule) } }
                }
                .onDelete { store.snapshot.rules.remove(atOffsets: $0) }
                .onMove { store.snapshot.rules.move(fromOffsets: $0, toOffset: $1) }
                Button("Добавить правило", systemImage: "plus") { editor = RuleDraft() }
            } header: { Text("Порядок обработки") } footer: { Text("Удерживайте правило для редактирования. Кнопка «Править» меняет порядок.") }.disabled(store.tunnel.active)
            Section("Проверить маршрут") {
                TextField("example.com или 192.168.1.1", text: $testHost).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                Button("Проверить правило", systemImage: "arrow.triangle.branch") { testRoute() }.disabled(testHost.isEmpty)
                if !result.isEmpty { Text(result).font(.subheadline).foregroundStyle(store.accent) }
            }
            Section {
                Label("Обычное iOS-приложение не может выбирать отдельные приложения для VPN. Здесь доступны правила доменов и IP.", systemImage: "info.circle").font(.caption).foregroundStyle(.secondary)
            }
        }.auraPage().navigationTitle("Маршрутизация")
            .toolbar { ToolbarItem(placement: .topBarTrailing) { EditButton().disabled(store.tunnel.active) } }
            .sheet(item: $editor) { RuleEditor(draft: $0) }
            .onDisappear { store.persist() }
            .onChange(of: store.snapshot.mode) { store.persist(); result = "" }
            .onChange(of: store.snapshot.rules) { store.persist(); result = "" }
            .onChange(of: testHost) { result = "" }
    }
    private func testRoute() {
        let host = testHost.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if store.snapshot.mode == .smart, let rule = store.snapshot.rules.first(where: { $0.enabled && $0.matches(host) }) { result = "\(rule.action.title) · совпадение с \(rule.value)" }
        else if store.snapshot.bypassLAN && ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8", "169.254.0.0/16"].contains(where: { RoutingRule.containsIPv4(host, in: $0) }) { result = "Напрямую · локальная сеть" }
        else { result = (store.snapshot.mode == .direct ? "Напрямую" : "Прокси") + " · маршрут по умолчанию" }
    }
}

struct RuleDraft: Identifiable { var id = UUID(); var rule = RoutingRule(kind: .suffix, value: "", action: .proxy) }
struct RuleEditor: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State var draft: RuleDraft
    @State private var error: String?
    var body: some View {
        NavigationStack {
            Form {
                Picker("Условие", selection: $draft.rule.kind) { ForEach(RuleKind.allCases) { Text($0.title).tag($0) } }
                TextField(draft.rule.kind == .cidr ? "192.168.0.0/16" : "example.com", text: $draft.rule.value).textInputAutocapitalization(.never).autocorrectionDisabled()
                Picker("Действие", selection: $draft.rule.action) { ForEach(RouteAction.allCases) { Text($0.title).tag($0) } }
                if let error { Text(error).foregroundStyle(.red) }
            }.navigationTitle("Правило").navigationBarTitleDisplayMode(.inline).toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Сохранить") {
                    do {
                        try store.requireDisconnected()
                        draft.rule.value = draft.rule.value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                        try RoutingRule.validate(draft.rule.value, kind: draft.rule.kind)
                        if let index = store.snapshot.rules.firstIndex(where: { $0.id == draft.rule.id }) { store.snapshot.rules[index] = draft.rule }
                        else { store.snapshot.rules.append(draft.rule) }
                        store.persist(); dismiss()
                    } catch { self.error = error.localizedDescription }
                } }
            }
        }
    }
}
