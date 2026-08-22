// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "NetworkPeerCore",
    platforms: [
        .macOS(.v14),
        .iOS(.v17),
    ],
    products: [
        .library(name: "NetworkPeerCore", targets: ["NetworkPeerCore"]),
    ],
    targets: [
        .target(name: "NetworkPeerCore", path: "Sources/NetworkPeerCore"),
        .testTarget(name: "NetworkPeerCoreTests", dependencies: ["NetworkPeerCore"], path: "Tests/NetworkPeerCoreTests"),
    ],
)
