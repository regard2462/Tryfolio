import SwiftUI
import UIKit
import TryfolioKit

// Bildschirm aus Compose Multiplatform (MainViewController.kt im Modul ios/).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// Compose richtet sich selbst nach Notch, Home-Leiste und Tastatur.
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
