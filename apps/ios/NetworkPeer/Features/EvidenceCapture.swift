import AVFoundation
import PhotosUI
import SwiftUI
import UniformTypeIdentifiers
import UIKit

enum EvidenceCaptureError: LocalizedError {
    case cameraUnavailable
    case cameraPermissionDenied
    case unreadableMedia

    var errorDescription: String? {
        switch self {
        case .cameraUnavailable: "Camera capture is unavailable on this device."
        case .cameraPermissionDenied: "Camera access is required to capture evidence. Enable it in Settings and try again."
        case .unreadableMedia: "The captured evidence could not be prepared for upload."
        }
    }
}

@MainActor
func requestCameraPermission() async -> Bool {
    switch AVCaptureDevice.authorizationStatus(for: .video) {
    case .authorized:
        return true
    case .notDetermined:
        return await withCheckedContinuation { continuation in
            AVCaptureDevice.requestAccess(for: .video) { granted in
                continuation.resume(returning: granted)
            }
        }
    default:
        return false
    }
}

struct CameraEvidencePicker: UIViewControllerRepresentable {
    let completion: (Result<URL, Error>) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.mediaTypes = [UTType.image.identifier, UTType.movie.identifier]
        picker.videoQuality = .typeMedium
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_: UIImagePickerController, context _: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(completion: completion)
    }

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let completion: (Result<URL, Error>) -> Void

        init(completion: @escaping (Result<URL, Error>) -> Void) {
            self.completion = completion
        }

        func imagePickerControllerDidCancel(_: UIImagePickerController) {
            completion(.failure(CancellationError()))
        }

        func imagePickerController(
            _: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any],
        ) {
            do {
                if let image = info[.originalImage] as? UIImage,
                   let data = image.jpegData(compressionQuality: 0.9) {
                    completion(.success(try writeTemporary(data: data, extensionName: "jpg")))
                    return
                }
                if let sourceURL = info[.mediaURL] as? URL {
                    let extensionName = sourceURL.pathExtension.isEmpty ? "mov" : sourceURL.pathExtension
                    let destination = FileManager.default.temporaryDirectory
                        .appendingPathComponent("networkpeer-camera-\(UUID().uuidString)")
                        .appendingPathExtension(extensionName)
                    try FileManager.default.copyItem(at: sourceURL, to: destination)
                    completion(.success(destination))
                    return
                }
                completion(.failure(EvidenceCaptureError.unreadableMedia))
            } catch {
                completion(.failure(error))
            }
        }

        private func writeTemporary(data: Data, extensionName: String) throws -> URL {
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("networkpeer-camera-\(UUID().uuidString)")
                .appendingPathExtension(extensionName)
            try data.write(to: url, options: .atomic)
            return url
        }
    }
}

@MainActor
func preparePhotoEvidence(_ item: PhotosPickerItem) async throws -> URL {
    guard let data = try await item.loadTransferable(type: Data.self),
          let image = UIImage(data: data),
          let jpeg = image.jpegData(compressionQuality: 0.9) else {
        throw EvidenceCaptureError.unreadableMedia
    }
    let url = FileManager.default.temporaryDirectory
        .appendingPathComponent("networkpeer-photo-\(UUID().uuidString)")
        .appendingPathExtension("jpg")
    try jpeg.write(to: url, options: .atomic)
    return url
}

struct PhotoEvidencePicker: View {
    @Environment(\.dismiss) private var dismiss
    let completion: (Result<URL, Error>) -> Void

    @State private var item: PhotosPickerItem?
    @State private var isLoading = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Image(systemName: "photo.on.rectangle.angled")
                    .font(.system(size: 42))
                    .foregroundStyle(NetworkPeerTheme.indigo)
                Text("Choose photo evidence")
                    .font(.title3.weight(.bold))
                Text("Photos are converted to JPEG before the app calculates the exact upload checksum.")
                    .font(.subheadline)
                    .foregroundStyle(NetworkPeerTheme.muted)
                    .multilineTextAlignment(.center)
                PhotosPicker(selection: $item, matching: .images) {
                    Label(isLoading ? "Preparing photo" : "Choose photo", systemImage: "photo")
                }
                .buttonStyle(.borderedProminent)
                .tint(NetworkPeerTheme.indigo)
                .disabled(isLoading)
                if isLoading { ProgressView() }
            }
            .padding(28)
            .navigationTitle("Photo evidence")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .disabled(isLoading)
                }
            }
            .onChange(of: item) { _, item in
                guard let item else { return }
                Task { await prepare(item) }
            }
        }
    }

    private func prepare(_ item: PhotosPickerItem) async {
        isLoading = true
        defer { isLoading = false }
        do {
            let url = try await preparePhotoEvidence(item)
            completion(.success(url))
            dismiss()
        } catch {
            completion(.failure(error))
        }
    }
}
