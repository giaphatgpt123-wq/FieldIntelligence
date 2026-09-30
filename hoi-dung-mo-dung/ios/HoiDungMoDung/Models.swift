import Foundation

struct Catalog: Codable { let version: String; let fields: [String]; let functions: [CatalogFunction] }
struct CatalogFunction: Codable, Identifiable {
    let id: String; let field: String; let service: String; let group: String; let title: String
}
struct PrivateContact: Codable, Identifiable {
    var id = UUID(); var nameOrUnit: String; var role: String; var phone: String
}
struct PersonalItem: Codable, Identifiable {
    var id = UUID(); var title: String; var content: String
}
