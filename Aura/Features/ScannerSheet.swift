import SwiftUI
import VisionKit

struct ScannerSheet: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    var completion: ((String) -> Void)?
    @State private var captured: String?
    @State private var error: String?
    var body: some View {
        NavigationStack {
            Group {
                if let captured {
                    Form {
                        Section("QR-код считан") { Text("Проверьте конфигурацию перед добавлением."); Text(captured).font(.system(.caption, design: .monospaced)).textSelection(.enabled) }
                        Section {
                            Button("Использовать конфигурацию") {
                                if let completion { completion(captured); dismiss() }
                                else {
                                    do { try store.add(ProfileParser.parse(captured)); dismiss() } catch { self.error = error.localizedDescription }
                                }
                            }
                            Button("Сканировать ещё раз") { self.captured = nil }
                        }
                        if let error { Text(error).foregroundStyle(.red) }
                    }
                } else if DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
                    QRScanner { captured = $0 } onError: { error = $0 }
                        .overlay(alignment: .bottom) { Text(error ?? "Наведите камеру на QR-код конфигурации").font(.subheadline).padding().background(.regularMaterial, in: Capsule()).padding() }
                } else {
                    ContentUnavailableView("Камера недоступна", systemImage: "camera", description: Text("Разрешите доступ к камере в настройках iOS или вставьте ссылку вручную. В симуляторе используйте текстовый импорт."))
                }
            }.navigationTitle("Сканировать QR").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Закрыть") { dismiss() } } }
        }
    }
}

private struct QRScanner: UIViewControllerRepresentable {
    let onCode: (String) -> Void
    let onError: (String) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(onCode: onCode, onError: onError) }
    func makeUIViewController(context: Context) -> DataScannerViewController {
        let scanner = DataScannerViewController(recognizedDataTypes: [.barcode(symbologies: [.qr])], qualityLevel: .balanced, recognizesMultipleItems: false, isHighFrameRateTrackingEnabled: false, isGuidanceEnabled: true, isHighlightingEnabled: true)
        scanner.delegate = context.coordinator
        do { try scanner.startScanning() } catch { DispatchQueue.main.async { onError("Не удалось запустить камеру. Проверьте разрешение в настройках iOS.") } }
        return scanner
    }
    func updateUIViewController(_ uiViewController: DataScannerViewController, context: Context) {}
    static func dismantleUIViewController(_ uiViewController: DataScannerViewController, coordinator: Coordinator) { uiViewController.stopScanning() }
    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        let onCode: (String) -> Void
        let onError: (String) -> Void
        private var delivered = false
        init(onCode: @escaping (String) -> Void, onError: @escaping (String) -> Void) { self.onCode = onCode; self.onError = onError }
        func dataScanner(_ dataScanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            guard !delivered else { return }
            for case .barcode(let code) in addedItems { if let payload = code.payloadStringValue { delivered = true; dataScanner.stopScanning(); onCode(payload); return } }
        }
        func dataScanner(_ dataScanner: DataScannerViewController, becameUnavailableWithError error: DataScannerViewController.ScanningUnavailable) { onError("Камера стала недоступна. Закройте сканер и повторите попытку.") }
    }
}
