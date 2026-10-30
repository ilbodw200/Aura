// swift-tools-version: 5.9
import PackageDescription

// Tests the actual shared implementation on macOS without waiting for an iOS
// XCTest host to boot. The app itself is still compiled and launched on iOS.
let package = Package(
    name: "AuraCoreChecks",
    platforms: [.macOS(.v14)],
    targets: [
        .target(name: "Aura", path: ".", exclude: [
            "Aura/Features", "Aura/Design", "Aura/Assets.xcassets",
            "Aura/AppStore.swift", "Aura/AuraApp.swift", "Aura/RootView.swift",
            "Aura/Services/LatencyService.swift", "Aura/Services/SubscriptionService.swift",
            "Aura/Services/TunnelController.swift", "Aura.xcodeproj", "Build IPA.command",
            "Config", "Docs", "Scripts", "Tests", "Tunnel", "LICENSE", "README.md",
            "THIRD_PARTY_NOTICES.md", "project.yml", "project-core.yml"
        ], sources: ["Shared", "Aura/Services/SecureStore.swift"]),
        .testTarget(name: "AuraTests", dependencies: ["Aura"], path: "Tests")
    ]
)
