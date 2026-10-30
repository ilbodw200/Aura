import Foundation

struct SubscriptionUsage: Codable {
    var upload: Int64?
    var download: Int64?
    var total: Int64?
    var expire: Date?
    static func parse(_ header: String?) -> Self? {
        guard let header, header.count <= 4096 else { return nil }
        var values: [String: Int64] = [:]
        for part in header.split(separator: ";") {
            let pair = part.split(separator: "=", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces).lowercased() }
            if pair.count == 2, let value = Int64(pair[1]), value >= 0 { values[pair[0]] = value }
        }
        guard !values.isEmpty else { return nil }
        return Self(upload: values["upload"], download: values["download"], total: values["total"], expire: values["expire"].flatMap { $0 > 0 ? Date(timeIntervalSince1970: Double($0)) : nil })
    }
    var used: Int64? {
        guard let upload, let download else { return nil }
        let sum = upload.addingReportingOverflow(download)
        return sum.overflow ? nil : sum.partialValue
    }
    var summary: String? {
        guard let used else { return nil }
        let usedText = ByteCountFormatter.string(fromByteCount: used, countStyle: .file)
        guard let total else { return usedText }
        return usedText + " / " + (total == 0 ? "∞" : ByteCountFormatter.string(fromByteCount: total, countStyle: .file))
    }
}
