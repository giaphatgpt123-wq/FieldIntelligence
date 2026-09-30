import SwiftUI

@main
struct HoiDungMoDungApp: App {
    @StateObject private var store = LocalStore()
    var body: some Scene {
        WindowGroup { RootView().environmentObject(store) }
    }
}
