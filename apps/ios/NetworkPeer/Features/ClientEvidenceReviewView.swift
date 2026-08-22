import Foundation
import SwiftUI

struct ClientEvidenceReviewView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.openURL) private var openURL
    let jobID: String
    let subtasks: [JobSubtask]

    @State private var evidence: [ClientEvidenceReview] = []
    @State private var error: String?
    @State private var loading = true

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Evidence review")
                    .font(.largeTitle.weight(.bold))
                    .accessibilityAddTraits(.isHeader)
                Text("Review is read-only. Evidence download links are short-lived, API-issued targets and are never saved by the app.")
                    .foregroundStyle(NetworkPeerTheme.muted)
                if loading {
                    ProgressView("Loading confirmed evidence")
                        .frame(maxWidth: .infinity)
                }
                if let error {
                    NoticeCard(message: error, color: NetworkPeerTheme.danger)
                }
                if !loading && error == nil && evidence.isEmpty {
                    EmptyState(title: "No confirmed evidence", message: "Confirmed worker evidence will appear here when it is available for client review.")
                }
                ForEach(evidence) { item in
                    evidenceCard(item)
                }
            }
            .padding(16)
        }
        .navigationTitle("Evidence")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    @ViewBuilder
    private func evidenceCard(_ item: ClientEvidenceReview) -> some View {
        NetworkPeerCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(subtaskTitle(for: item.subtaskID))
                            .font(.headline)
                        Text(item.mediaType.rawValue.capitalized)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(NetworkPeerTheme.indigo)
                    }
                    Spacer()
                    Text(item.status.rawValue)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(NetworkPeerTheme.success)
                }
                Label(mediaLabel(for: item.mediaType), systemImage: mediaIcon(for: item.mediaType))
                    .font(.subheadline)
                    .foregroundStyle(NetworkPeerTheme.muted)
                if let fileSizeBytes = item.fileSizeBytes {
                    Text("Size: \(ByteCountFormatter.string(fromByteCount: fileSizeBytes, countStyle: .file))")
                        .font(.footnote)
                        .foregroundStyle(NetworkPeerTheme.muted)
                }
                Text("Captured: \(item.capturedAt)")
                    .font(.footnote)
                    .foregroundStyle(NetworkPeerTheme.muted)
                Button {
                    guard item.download.isHTTPS else {
                        error = "Evidence links must use HTTPS. Refresh the review and try again."
                        return
                    }
                    openURL(item.download.url)
                } label: {
                    Label("Open secure evidence", systemImage: "arrow.up.forward.app")
                }
                .buttonStyle(.bordered)
                .accessibilityHint("Opens a short-lived evidence URL issued by NetworkPeer")
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func load() async {
        guard let api = model.api else { return }
        loading = true
        error = nil
        defer { loading = false }
        do {
            evidence = try await api.clientEvidence(jobID: jobID).evidence
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func subtaskTitle(for id: String) -> String {
        subtasks.first(where: { $0.id == id })?.title ?? "Checklist evidence"
    }

    private func mediaLabel(for type: MediaType) -> String {
        switch type {
        case .image: "Image evidence"
        case .video: "Video evidence"
        case .audio: "Audio evidence"
        case .document: "Document evidence"
        }
    }

    private func mediaIcon(for type: MediaType) -> String {
        switch type {
        case .image: "photo"
        case .video: "video"
        case .audio: "waveform"
        case .document: "doc"
        }
    }
}
