import SwiftUI

struct RootView: View {
    var body: some View {
        TabView {
            SearchView().tabItem { Label("Tra cứu", systemImage: "magnifyingglass") }
            ResultView().tabItem { Label("Kết quả", systemImage: "checkmark.circle") }
            PersonalView().tabItem { Label("Cá nhân", systemImage: "person.crop.circle") }
            AppManagerView().tabItem { Label("Ứng dụng", systemImage: "square.grid.2x2") }
            ContactsView().tabItem { Label("Danh bạ", systemImage: "phone") }
            SettingsView().tabItem { Label("Cài đặt", systemImage: "gearshape") }
        }
    }
}

struct SearchView: View {
    @EnvironmentObject var store: LocalStore
    @State private var query = ""
    var matches: [CatalogFunction] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return [] }
        return Array(store.catalog.functions.filter { f in
            [f.title,f.service,f.group,f.field].joined(separator:" ").lowercased().contains(q)
        }.prefix(8))
    }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Bạn muốn làm gì?").font(.largeTitle.bold())
                    TextField("Nhập nội dung cần tra cứu…", text: $query).textFieldStyle(.roundedBorder)
                    if query.isEmpty { Text("Nhập câu hỏi hoặc việc cần thực hiện.").foregroundStyle(.secondary) }
                    ForEach(matches) { item in
                        VStack(alignment:.leading, spacing:6) {
                            Text(item.title).font(.headline)
                            Text("\(item.field) · \(item.service)").font(.subheadline).foregroundStyle(.secondary)
                        }.padding().frame(maxWidth:.infinity,alignment:.leading).background(.thinMaterial,in:RoundedRectangle(cornerRadius:18))
                    }
                }.padding()
            }.navigationTitle("Hỏi đúng – Mở đúng")
        }
    }
}

struct ResultView: View {
    var body: some View { NavigationStack { ContentUnavailableView("Kết quả", systemImage: "checkmark.circle", description: Text("Kết quả truy vấn sẽ được hiển thị ngắn gọn, đúng trọng tâm và có căn cứ khi dữ liệu local đã được xác minh.")) .navigationTitle("Kết quả") } }
}

struct PersonalView: View {
    @EnvironmentObject var store: LocalStore
    @State private var title=""; @State private var content=""
    var body: some View {
        NavigationStack { List {
            Section("Thêm mục cá nhân") {
                TextField("Tiêu đề",text:$title); TextField("Nội dung",text:$content)
                Button("Lưu trên thiết bị") { guard !title.isEmpty else{return}; store.personal.append(.init(title:title,content:content)); title=""; content="" }
            }
            Section("Dữ liệu cá nhân") { ForEach(store.personal) { p in VStack(alignment:.leading){Text(p.title).bold();Text(p.content).foregroundStyle(.secondary)} } }
        }.navigationTitle("Cá nhân") }
    }
}

struct ContactsView: View {
    @EnvironmentObject var store: LocalStore
    @Environment(\.openURL) var openURL
    @State private var name=""; @State private var role=""; @State private var phone=""
    var body: some View {
        NavigationStack { List {
            Section("Thêm liên hệ") {
                TextField("Họ và tên (Đơn vị)",text:$name); TextField("Chức vụ",text:$role); TextField("Số điện thoại",text:$phone).keyboardType(.phonePad)
                Button("Lưu") { guard !name.isEmpty,!phone.isEmpty else{return}; store.contacts.append(.init(nameOrUnit:name,role:role,phone:phone)); name="";role="";phone="" }
            }
            Section("Danh bạ") { ForEach(store.contacts) { c in
                HStack { VStack(alignment:.leading){Text(c.nameOrUnit).bold(); if !c.role.isEmpty{Text(c.role).foregroundStyle(.secondary)};Text(c.phone)}; Spacer(); Button("Gọi") { let s=c.phone.filter{"0123456789+".contains($0)}; if let u=URL(string:"tel:\(s)"){openURL(u)} } }
            }.onDelete { store.contacts.remove(atOffsets:$0) } }
        }.navigationTitle("Danh bạ") }
    }
}

struct AppManagerView: View {
    @EnvironmentObject var store: LocalStore
    var body: some View { NavigationStack { List { Section("Lĩnh vực") { ForEach(store.catalog.fields,id:\.self){ Text($0) } }; Section("Catalog"){Text("\(store.catalog.functions.count) chức năng lõi")} }.navigationTitle("Quản lý ứng dụng") } }
}

struct SettingsView: View {
    @EnvironmentObject var store: LocalStore
    var body: some View { NavigationStack { List { Section("Quyền riêng tư") { Label("Không có backend, analytics hoặc telemetry",systemImage:"lock.shield"); Text("Dữ liệu Cá nhân và Danh bạ chỉ lưu trên thiết bị.") }; Section("Thư viện ẩn"){Text("Phiên bản: \(store.catalog.version)");Text("\(store.catalog.fields.count) lĩnh vực · \(store.catalog.functions.count) chức năng")} }.navigationTitle("Cài đặt") } }
}
