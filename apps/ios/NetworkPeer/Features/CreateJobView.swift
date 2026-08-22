import CoreLocation
import Foundation
import SwiftUI

private struct ChecklistDraft: Identifiable, Codable, Equatable {
    let id = UUID()
    var title = ""
    var description = ""
    var isRequired = true
}

struct CreateJobView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @StateObject private var locationManager = DeviceLocationManager()

    let onCreated: (Job) -> Void

    @State private var title = ""
    @State private var description = ""
    @State private var category = ""
    @State private var budget = ""
    @State private var currency = "USD"
    @State private var address = ""
    @State private var latitude = ""
    @State private var longitude = ""
    @State private var hasSchedule = false
    @State private var scheduledAt = Date()
    @State private var publicTitle = ""
    @State private var publicDescription = ""
    @State private var checklist = [ChecklistDraft()]
    @State private var idempotencyKey = UUID().uuidString
    @State private var submittedFingerprint: String?
    @State private var error: String?
    @State private var isLocating = false
    @State private var isSubmitting = false
    @State private var didRestoreDraft = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Create a job")
                        .font(.largeTitle.weight(.bold))
                        .accessibilityAddTraits(.isHeader)
                    Text("Your full address is shared only after an eligible worker is assigned. Use the public fields for privacy-safe nearby discovery.")
                        .foregroundStyle(NetworkPeerTheme.muted)

                    NetworkPeerCard {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Job details").font(.title3.weight(.bold))
                            TextField("Title", text: $title)
                                .textContentType(.none)
                                .textFieldStyle(.roundedBorder)
                            TextField("Description", text: $description, axis: .vertical)
                                .lineLimit(3 ... 6)
                                .textFieldStyle(.roundedBorder)
                            TextField("Category", text: $category)
                                .textFieldStyle(.roundedBorder)
                            HStack {
                                TextField("Budget", text: $budget)
                                    .keyboardType(.decimalPad)
                                    .textFieldStyle(.roundedBorder)
                                    .accessibilityLabel("Budget in major currency units")
                                TextField("Currency", text: $currency)
                                    .textInputAutocapitalization(.characters)
                                    .autocorrectionDisabled()
                                    .textFieldStyle(.roundedBorder)
                                    .frame(width: 86)
                                    .accessibilityLabel("Currency code")
                            }
                            Toggle("Schedule this job", isOn: $hasSchedule)
                            if hasSchedule {
                                DatePicker("Scheduled time", selection: $scheduledAt, displayedComponents: [.date, .hourAndMinute])
                            }
                        }
                    }

                    NetworkPeerCard {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Location and address").font(.title3.weight(.bold))
                            Text("A precise GeoJSON point is required. The API remains authoritative for eligibility and address visibility.")
                                .font(.footnote)
                                .foregroundStyle(NetworkPeerTheme.muted)
                            Button {
                                Task { await useCurrentLocation() }
                            } label: {
                                Label(isLocating ? "Finding location" : "Use current location", systemImage: "location.fill")
                            }
                            .buttonStyle(.bordered)
                            .disabled(isLocating || isSubmitting)
                            .accessibilityHint("Fills latitude and longitude from this device")
                            HStack {
                                TextField("Latitude", text: $latitude)
                                    .keyboardType(.numbersAndPunctuation)
                                    .textFieldStyle(.roundedBorder)
                                TextField("Longitude", text: $longitude)
                                    .keyboardType(.numbersAndPunctuation)
                                    .textFieldStyle(.roundedBorder)
                            }
                            TextField("Full service address", text: $address, axis: .vertical)
                                .lineLimit(2 ... 4)
                                .textFieldStyle(.roundedBorder)
                        }
                    }

                    NetworkPeerCard {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Privacy-safe discovery").font(.title3.weight(.bold))
                            Text("Optional public copy is shown to nearby workers before acceptance. Leave it blank only if the server can safely derive public copy.")
                                .font(.footnote)
                                .foregroundStyle(NetworkPeerTheme.muted)
                            TextField("Public title (optional)", text: $publicTitle)
                                .textFieldStyle(.roundedBorder)
                            TextField("Public description (optional)", text: $publicDescription, axis: .vertical)
                                .lineLimit(2 ... 4)
                                .textFieldStyle(.roundedBorder)
                        }
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Text("Evidence checklist").font(.title3.weight(.bold))
                            Spacer()
                            Button {
                                checklist.append(ChecklistDraft())
                            } label: {
                                Label("Add item", systemImage: "plus")
                            }
                            .buttonStyle(.bordered)
                            .disabled(isSubmitting)
                        }
                        Text("Required items must be confirmed by the worker before they can submit work.")
                            .font(.footnote)
                            .foregroundStyle(NetworkPeerTheme.muted)
                        ForEach(checklist.indices, id: \.self) { index in
                            NetworkPeerCard {
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack {
                                        Text("Item \(index + 1)").font(.headline)
                                        Spacer()
                                        if checklist.count > 1 {
                                            Button("Remove", role: .destructive) {
                                                checklist.remove(at: index)
                                            }
                                            .font(.footnote)
                                            .disabled(isSubmitting)
                                        }
                                    }
                                    TextField("Checklist title", text: $checklist[index].title)
                                        .textFieldStyle(.roundedBorder)
                                    TextField("Instructions (optional)", text: $checklist[index].description, axis: .vertical)
                                        .lineLimit(2 ... 4)
                                        .textFieldStyle(.roundedBorder)
                                    Toggle("Required evidence", isOn: $checklist[index].isRequired)
                                }
                            }
                        }
                    }

                    if let error {
                        NoticeCard(message: error, color: NetworkPeerTheme.danger)
                    }
                    Button {
                        Task { await createJob() }
                    } label: {
                        HStack {
                            Spacer()
                            if isSubmitting { ProgressView().tint(.white) }
                            Text(isSubmitting ? "Creating job" : "Create job")
                            Spacer()
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(NetworkPeerTheme.indigo)
                    .disabled(isSubmitting)
                    .accessibilityHint("Creates the job with a retry-safe idempotency key")
                }
                .padding(16)
            }
            .navigationTitle("New job")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .disabled(isSubmitting)
                }
                ToolbarItem(placement: .primaryAction) {
                    Button("Discard draft", role: .destructive) {
                        model.discardPendingClientJob()
                        dismiss()
                    }
                    .disabled(isSubmitting)
                }
            }
            .task { restorePendingJob() }
        }
    }

    private func useCurrentLocation() async {
        isLocating = true
        error = nil
        defer { isLocating = false }
        do {
            let coordinate = try await locationManager.requestCurrentCoordinate()
            latitude = String(format: "%.6f", coordinate.latitude)
            longitude = String(format: "%.6f", coordinate.longitude)
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func createJob() async {
        guard let api = model.api else { return }
        isSubmitting = true
        error = nil
        defer { isSubmitting = false }

        do {
            let cents = try budgetCents(from: budget)
            guard let latitude = Double(latitude), let longitude = Double(longitude) else {
                throw NetworkPeerAPIError.validation("Enter a valid latitude and longitude, or use current location.")
            }
            let fingerprint = try requestFingerprint(
                cents: cents,
                latitude: latitude,
                longitude: longitude,
            )
            if submittedFingerprint != fingerprint {
                idempotencyKey = UUID().uuidString
                submittedFingerprint = fingerprint
            }
            let request = makeRequest(cents: cents, latitude: latitude, longitude: longitude)
            try CreateJobValidator.validate(request)
            model.savePendingClientJob(request)
            let job = try await api.createClientJob(request)
            model.discardPendingClientJob()
            onCreated(job)
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func makeRequest(cents: Int64, latitude: Double, longitude: Double) -> CreateJobRequest {
        CreateJobRequest(
            title: trimmed(title),
            description: trimmed(description),
            category: trimmed(category),
            budgetCents: cents,
            currency: trimmed(currency).uppercased(),
            location: Point(longitude: longitude, latitude: latitude),
            address: optional(address),
            scheduledAt: hasSchedule ? ISO8601DateFormatter().string(from: scheduledAt) : nil,
            publicTitle: optional(publicTitle),
            publicDescription: optional(publicDescription),
            idempotencyKey: idempotencyKey,
            subtasks: checklist.map {
                CreateSubtaskRequest(
                    title: trimmed($0.title),
                    description: optional($0.description),
                    isRequired: $0.isRequired,
                )
            },
        )
    }

    private func requestFingerprint(cents: Int64, latitude: Double, longitude: Double) throws -> String {
        struct Fingerprint: Codable {
            let title: String
            let description: String
            let category: String
            let cents: Int64
            let currency: String
            let latitude: Double
            let longitude: Double
            let address: String?
            let scheduledAt: String?
            let publicTitle: String?
            let publicDescription: String?
            let checklist: [ChecklistDraft]
        }
        let value = Fingerprint(
            title: trimmed(title),
            description: trimmed(description),
            category: trimmed(category),
            cents: cents,
            currency: trimmed(currency).uppercased(),
            latitude: latitude,
            longitude: longitude,
            address: optional(address),
            scheduledAt: hasSchedule ? ISO8601DateFormatter().string(from: scheduledAt) : nil,
            publicTitle: optional(publicTitle),
            publicDescription: optional(publicDescription),
            checklist: checklist,
        )
        return try JSONEncoder().encode(value).base64EncodedString()
    }

    private func budgetCents(from input: String) throws -> Int64 {
        let normalized = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let decimal = Decimal(string: normalized), decimal > 0 else {
            throw NetworkPeerAPIError.validation("Enter a budget greater than zero.")
        }
        let cents = decimal * 100
        let number = NSDecimalNumber(decimal: cents)
        guard number != .notANumber,
              number.rounding(accordingToBehavior: nil).decimalValue == cents,
              number.int64Value > 0 else {
            throw NetworkPeerAPIError.validation("Budget may include at most two decimal places.")
        }
        return number.int64Value
    }

    private func restorePendingJob() {
        guard !didRestoreDraft, let request = model.pendingClientJob() else { return }
        didRestoreDraft = true
        title = request.title
        description = request.description
        category = request.category
        budget = String(format: "%.2f", Double(request.budgetCents) / 100)
        currency = request.currency
        address = request.address ?? ""
        if request.location.coordinates.count == 2 {
            longitude = String(format: "%.6f", request.location.coordinates[0])
            latitude = String(format: "%.6f", request.location.coordinates[1])
        }
        hasSchedule = request.scheduledAt != nil
        if let scheduledAt = request.scheduledAt,
           let date = ISO8601DateFormatter().date(from: scheduledAt) {
            self.scheduledAt = date
        }
        publicTitle = request.publicTitle ?? ""
        publicDescription = request.publicDescription ?? ""
        checklist = request.subtasks.map { subtask in
            var draft = ChecklistDraft()
            draft.title = subtask.title
            draft.description = subtask.description ?? ""
            draft.isRequired = subtask.isRequired
            return draft
        }
        idempotencyKey = request.idempotencyKey
        if let latitude = Double(latitude), let longitude = Double(longitude) {
            submittedFingerprint = try? requestFingerprint(cents: request.budgetCents, latitude: latitude, longitude: longitude)
        }
    }

    private func trimmed(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func optional(_ value: String) -> String? {
        let value = trimmed(value)
        return value.isEmpty ? nil : value
    }
}
