import Foundation
import NetworkExtension
#if canImport(Libbox)
import Libbox
#endif

final class PacketTunnelProvider: NEPacketTunnelProvider {
    #if canImport(Libbox)
    private var service: LibboxBoxService?
    private lazy var platform = BoxPlatform(provider: self)
    #endif
    private let worker = DispatchQueue(label: "app.aura.tunnel.lifecycle")

    override func startTunnel(options: [String: NSObject]?, completionHandler: @escaping (Error?) -> Void) {
        worker.async { [self] in
            #if canImport(Libbox)
            do {
                let directory = try SharedContainer.directory()
                let config = try String(contentsOf: directory.appendingPathComponent("active.json"), encoding: .utf8)
                let setup = LibboxSetupOptions()
                setup.basePath = directory.path
                setup.workingPath = directory.appendingPathComponent("work").path
                setup.tempPath = directory.appendingPathComponent("temp").path
                var error: NSError?
                LibboxSetup(setup, &error)
                if let error { throw error }
                LibboxSetMemoryLimit(true)
                service = LibboxNewService(config, platform, &error)
                if let error { throw error }
                guard let service else { throw AuraError.message("Ядро не создало сервис.") }
                try service.start()
                completionHandler(nil)
            } catch {
                try? service?.close(); service = nil
                platform.closeMonitor()
                completionHandler(error)
            }
            #else
            completionHandler(AuraError.message("Эта сборка AURA содержит интерфейс без VPN-ядра. Соберите Libbox через Scripts/bootstrap-core.sh и используйте project-core.yml."))
            #endif
        }
    }
    override func stopTunnel(with reason: NEProviderStopReason, completionHandler: @escaping () -> Void) {
        worker.async { [self] in
            #if canImport(Libbox)
            try? service?.close(); service = nil; platform.closeMonitor()
            #endif
            completionHandler()
        }
    }
}
