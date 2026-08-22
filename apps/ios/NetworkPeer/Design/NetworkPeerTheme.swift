import SwiftUI

enum NetworkPeerTheme {
    static let indigo = Color(red: 0.31, green: 0.27, blue: 0.90)
    static let teal = Color(red: 0.08, green: 0.72, blue: 0.64)
    static let slate = Color(red: 0.06, green: 0.09, blue: 0.16)
    static let muted = Color(red: 0.39, green: 0.45, blue: 0.55)
    static let surface = Color(red: 0.97, green: 0.98, blue: 1.00)
    static let success = Color(red: 0.09, green: 0.64, blue: 0.29)
    static let warning = Color(red: 0.85, green: 0.43, blue: 0.04)
    static let danger = Color(red: 0.86, green: 0.15, blue: 0.15)
    static let cardRadius: CGFloat = 20
}

struct NetworkPeerCard<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.background, in: RoundedRectangle(cornerRadius: NetworkPeerTheme.cardRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: NetworkPeerTheme.cardRadius, style: .continuous)
                    .stroke(.gray.opacity(0.13), lineWidth: 1)
            }
            .shadow(color: .black.opacity(0.06), radius: 16, y: 7)
    }
}

struct StatusPill: View {
    let status: JobStatus

    private var color: Color {
        switch status {
        case .completed, .approved: NetworkPeerTheme.success
        case .cancelled, .disputed: NetworkPeerTheme.danger
        case .atLocation, .inProgress: NetworkPeerTheme.warning
        default: NetworkPeerTheme.indigo
        }
    }

    var body: some View {
        Text(status.rawValue.replacingOccurrences(of: "_", with: " "))
            .font(.caption.weight(.semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(color.opacity(0.12), in: Capsule())
    }
}

struct NoticeCard: View {
    let message: String
    let color: Color

    var body: some View {
        Text(message)
            .font(.footnote)
            .foregroundStyle(color)
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(color.opacity(0.10), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

struct BrandHeader: View {
    var compact = false

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "briefcase.fill")
                .font(compact ? .body : .title3)
                .foregroundStyle(.white)
                .frame(width: compact ? 30 : 38, height: compact ? 30 : 38)
                .background(NetworkPeerTheme.indigo, in: RoundedRectangle(cornerRadius: compact ? 10 : 13, style: .continuous))
            Text("NetworkPeer")
                .font(compact ? .headline : .title2.weight(.bold))
                .foregroundStyle(NetworkPeerTheme.slate)
        }
    }
}
