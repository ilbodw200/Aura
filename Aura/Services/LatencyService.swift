import Foundation
import Network

enum LatencyService {
    /// TCP handshake to the server, not an end-to-end proxy speed test.
    static func measure(host: String, port: Int) async throws -> Int {
        guard let endpointPort = NWEndpoint.Port(rawValue: UInt16(clamping: port)) else { throw AuraError.message("Неверный порт.") }
        let connection = NWConnection(host: NWEndpoint.Host(host), port: endpointPort, using: .tcp)
        let queue = DispatchQueue(label: "app.aura.latency.\(UUID().uuidString)")
        let start = ContinuousClock.now
        return try await withCheckedThrowingContinuation { continuation in
            var finished = false // Accessed only on the serial queue below.
            func finish(_ result: Result<Int, Error>) {
                guard !finished else { return }; finished = true
                connection.stateUpdateHandler = nil
                connection.cancel()
                continuation.resume(with: result)
            }
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    let elapsed = start.duration(to: .now).components
                    finish(.success(max(1, Int(elapsed.seconds * 1000 + elapsed.attoseconds / 1_000_000_000_000_000))))
                case .failed(let error): finish(.failure(error))
                case .cancelled: finish(.failure(CancellationError()))
                default: break
                }
            }
            connection.start(queue: queue)
            queue.asyncAfter(deadline: .now() + 5) { finish(.failure(AuraError.message("Сервер не ответил за 5 секунд."))) }
        }
    }
}
