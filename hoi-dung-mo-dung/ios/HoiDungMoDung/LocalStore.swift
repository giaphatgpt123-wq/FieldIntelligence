import Foundation

@MainActor
final class LocalStore: ObservableObject {
    @Published var catalog = Catalog(version: "0", fields: [], functions: [])
    @Published var contacts: [PrivateContact] = [] { didSet { saveContacts() } }
    @Published var personal: [PersonalItem] = [] { didSet { savePersonal() } }

    private let contactsKey = "private_contacts_v1"
    private let personalKey = "personal_vault_v1"

    init() { loadCatalog(); loadPrivateData() }

    private func loadCatalog() {
        guard let url = Bundle.main.url(forResource: "knowledge_catalog", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode(Catalog.self, from: data) else { return }
        catalog = decoded
    }
    private func loadPrivateData() {
        if let d = UserDefaults.standard.data(forKey: contactsKey), let v = try? JSONDecoder().decode([PrivateContact].self, from: d) { contacts = v }
        if let d = UserDefaults.standard.data(forKey: personalKey), let v = try? JSONDecoder().decode([PersonalItem].self, from: d) { personal = v }
    }
    private func saveContacts() { if let d = try? JSONEncoder().encode(contacts) { UserDefaults.standard.set(d, forKey: contactsKey) } }
    private func savePersonal() { if let d = try? JSONEncoder().encode(personal) { UserDefaults.standard.set(d, forKey: personalKey) } }
}
