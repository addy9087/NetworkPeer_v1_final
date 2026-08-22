import Foundation
import SwiftUI

struct InboxView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss

    @State private var nextCursor: String?
    @State private var error: String?
    @State private var isLoading = false
    @State private var isLoadingMore = false
    @State private var isMarkingRead = false

    var body: some View {
        NavigationStack {
            Group {
                if model.inboxItems.isEmpty, isLoading {
                    ProgressView("Loading inbox")
                } else if model.inboxItems.isEmpty {
                    ContentUnavailableView(
                        "No updates yet",
                        systemImage: "tray",
                        description: Text("Job and wallet updates will appear here."),
                    )
                } else {
                    List(model.inboxItems) { item in
                        Button {
                            Task { await open(item) }
                        } label: {
                            InboxRow(item: item)
                        }
                        .buttonStyle(.plain)
                        .disabled(isMarkingRead)
                        .accessibilityHint(item.readAt == nil ? "Marks this update as read and opens its job when available" : "Opens this update")
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Inbox")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button(isMarkingRead ? "Updating" : "Read all") {
                        Task { await markAllRead() }
                    }
                    .disabled(isMarkingRead || model.inboxItems.allSatisfy { $0.readAt != nil })
                    .accessibilityLabel("Mark all inbox updates as read")
                }
            }
            .safeAreaInset(edge: .bottom) {
                VStack(alignment: .leading, spacing: 8) {
                    if let error {
                        Text(error)
                            .foregroundStyle(NetworkPeerTheme.warning)
                    }
                    if let nextCursor {
                        Button(isLoadingMore ? "Loading more" : "Load more") {
                            Task { await loadMore(beforeCursor: nextCursor) }
                        }
                        .buttonStyle(.bordered)
                        .disabled(isLoadingMore)
                    }
                    Text("Read state is confirmed by NetworkPeer when online. Cached inbox state remains available for offline fallback.")
                        .foregroundStyle(NetworkPeerTheme.muted)
                }
                .font(.footnote)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(.bar)
            }
            .task { await refresh() }
        }
    }

    private func refresh() async {
        isLoading = true
        error = nil
        defer { isLoading = false }
        do {
            let page = try await model.loadInbox()
            nextCursor = page.hasMore ? page.nextCursor : nil
        } catch {
            error = "Showing cached inbox: \(error.localizedDescription)"
        }
    }

    private func loadMore(beforeCursor: String) async {
        isLoadingMore = true
        defer { isLoadingMore = false }
        do {
            let page = try await model.loadInbox(beforeCursor: beforeCursor)
            nextCursor = page.hasMore ? page.nextCursor : nil
        } catch {
            self.error = "More inbox updates could not be loaded: \(error.localizedDescription)"
        }
    }

    private func open(_ item: InboxItem) async {
        isMarkingRead = true
        defer { isMarkingRead = false }
        do {
            try await model.markInboxReadRemotely(item)
        } catch {
            self.error = "Marked read locally. Reopen the item when online to confirm it with NetworkPeer: \(error.localizedDescription)"
        }
        let jobID = item.data["job_id"]?.stringValue ?? item.data["jobId"]?.stringValue
        if let jobID, !jobID.isEmpty {
            model.deepLinks.open(.job(jobID))
            dismiss()
        }
    }

    private func markAllRead() async {
        isMarkingRead = true
        defer { isMarkingRead = false }
        do {
            _ = try await model.markAllInboxReadRemotely()
        } catch {
            self.error = "Marked read locally. Use Read all again when online to confirm it with NetworkPeer: \(error.localizedDescription)"
        }
    }
}

private struct InboxRow: View {
    let item: InboxItem

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: item.readAt == nil ? "circle.fill" : "circle")
                .font(.caption)
                .foregroundStyle(item.readAt == nil ? NetworkPeerTheme.indigo : .clear)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(item.title)
                    .font(.headline)
                    .foregroundStyle(NetworkPeerTheme.slate)
                Text(item.body)
                    .font(.subheadline)
                    .foregroundStyle(NetworkPeerTheme.muted)
                    .lineLimit(3)
                Text(item.createdAt.formatted(date: .abbreviated, time: .shortened))
                    .font(.caption)
                    .foregroundStyle(NetworkPeerTheme.muted)
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(item.readAt == nil ? "Unread. " : "")\(item.title). \(item.body)")
    }
}
