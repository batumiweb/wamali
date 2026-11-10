import SwiftUI
import PhotosUI
import UIKit

// MARK: - პატარა ფოტო

struct MedThumb: View {
    let med: Medication
    var size: CGFloat = 48
    var body: some View {
        Group {
            if let img = MedStore.shared.photo(for: med) {
                Image(uiImage: img).resizable().scaledToFill()
            } else {
                Image(systemName: "pills.fill")
                    .font(.system(size: size * 0.45))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.teal.gradient)
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.25))
    }
}

// MARK: - დღეს

struct TodayView: View {
    @Bindable var store = MedStore.shared
    @State private var authorized = AlarmService.isAuthorized

    var body: some View {
        NavigationStack {
            List {
                if !authorized {
                    Section {
                        VStack(alignment: .leading, spacing: 8) {
                            Label("მაღვიძარა გამორთულია", systemImage: "alarm.waves.left.and.right")
                                .font(.headline).foregroundStyle(.red)
                            Text("რომ დარეკოს, ნება დართე: პარამეტრები → წამალი → მაღვიძარები.")
                                .font(.subheadline)
                            Button("პარამეტრების გახსნა") {
                                if let url = URL(string: UIApplication.openSettingsURLString) {
                                    UIApplication.shared.open(url)
                                }
                            }
                            .buttonStyle(.borderedProminent)
                        }
                    }
                }

                if !store.lowStockMeds.isEmpty {
                    Section("მარაგი თავდება") {
                        ForEach(store.lowStockMeds) { m in
                            Label("\(m.name) — დარჩა \(m.stock ?? 0)", systemImage: "exclamationmark.triangle.fill")
                                .foregroundStyle(.orange)
                        }
                    }
                }

                Section {
                    let slots = store.slots(on: .now)
                    if slots.isEmpty {
                        ContentUnavailableView("დღეს წამალი არ გაქვს",
                                               systemImage: "checkmark.seal",
                                               description: Text("დაამატე „წამლები“ ჩანართში"))
                    }
                    ForEach(slots) { slot in SlotRow(slot: slot) }
                } header: {
                    Text(Date.now.formatted(.dateTime.weekday(.wide).day().month(.wide)))
                }
            }
            .navigationTitle("დღეს")
            .task { authorized = await AlarmService.requestAuth() }
        }
    }
}

struct SlotRow: View {
    let slot: DoseSlot
    @Bindable var store = MedStore.shared

    var body: some View {
        let status = store.status(medID: slot.med.id, scheduled: slot.date)
        let isPast = slot.date < .now
        HStack(spacing: 12) {
            MedThumb(med: slot.med)
            VStack(alignment: .leading, spacing: 2) {
                Text(slot.med.name).font(.headline)
                Text([slot.time.label, slot.med.dose, slot.med.note].filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer()
            switch status {
            case .some(.taken):
                Image(systemName: "checkmark.circle.fill").font(.title2).foregroundStyle(.green)
                    .onLongPressGesture { store.undo(medID: slot.med.id, scheduled: slot.date) }
            case .some(.skipped):
                Image(systemName: "xmark.circle.fill").font(.title2).foregroundStyle(.gray)
                    .onLongPressGesture { store.undo(medID: slot.med.id, scheduled: slot.date) }
            default:
                Button {
                    store.record(.taken, medID: slot.med.id, scheduled: slot.date)
                } label: {
                    Text("მივიღე").font(.subheadline.bold())
                }
                .buttonStyle(.borderedProminent)
                .tint(isPast ? .orange : .teal)
            }
        }
        .padding(.vertical, 4)
    }
}

// MARK: - წამლები

struct MedsView: View {
    @Bindable var store = MedStore.shared
    @State private var editing: Medication?

    var body: some View {
        NavigationStack {
            List {
                if store.meds.isEmpty {
                    ContentUnavailableView("წამალი ჯერ არ არის", systemImage: "pills",
                                           description: Text("დააჭირე + ღილაკს"))
                }
                ForEach(store.meds) { m in
                    Button { editing = m } label: {
                        HStack(spacing: 12) {
                            MedThumb(med: m)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(m.name).font(.headline).foregroundStyle(.primary)
                                Text(m.timesSummary).font(.subheadline).foregroundStyle(.secondary)
                                if let s = m.stock {
                                    Text("მარაგი: \(s)").font(.caption)
                                        .foregroundStyle(s <= m.lowStock ? .orange : .secondary)
                                }
                            }
                            Spacer()
                            if !m.isActive { Text("გამორთული").font(.caption).foregroundStyle(.secondary) }
                        }
                    }
                }
                .onDelete { idx in idx.map { store.meds[$0] }.forEach(store.delete) }
            }
            .navigationTitle("წამლები")
            .toolbar {
                Button { editing = Medication() } label: { Image(systemName: "plus") }
            }
            .sheet(item: $editing) { m in
                MedEditView(med: m, isNew: store.med(m.id) == nil)
            }
        }
    }
}

struct MedEditView: View {
    @Environment(\.dismiss) private var dismiss
    @State var med: Medication
    let isNew: Bool

    @State private var image: UIImage?
    @State private var imageChanged = false
    @State private var pickerItem: PhotosPickerItem?
    @State private var showCamera = false
    @State private var trackStock = false
    @State private var stockValue = 30
    @State private var saving = false
    @State private var testSent = false

    init(med: Medication, isNew: Bool) {
        _med = State(initialValue: med)
        self.isNew = isNew
        _image = State(initialValue: MedStore.shared.photo(for: med))
        _trackStock = State(initialValue: med.stock != nil)
        _stockValue = State(initialValue: med.stock ?? 30)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack {
                        Spacer()
                        ZStack {
                            if let image {
                                Image(uiImage: image).resizable().scaledToFill()
                            } else {
                                Image(systemName: "camera.fill").font(.largeTitle).foregroundStyle(.teal)
                            }
                        }
                        .frame(width: 140, height: 140)
                        .background(Color.teal.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 28))
                        Spacer()
                    }
                    HStack {
                        Button { showCamera = true } label: { Label("გადაღება", systemImage: "camera") }
                            .buttonStyle(.bordered)
                        Spacer()
                        PhotosPicker(selection: $pickerItem, matching: .images) {
                            Label("გალერეა", systemImage: "photo")
                        }
                        .buttonStyle(.bordered)
                    }
                } footer: { Text("გადაუღე წამლის კოლოფს ან აბს — დარეკვისას ამ ფოტოს დაინახავ.") }

                Section("წამალი") {
                    TextField("სახელი (მაგ. ასპირინი)", text: $med.name)
                    TextField("დოზა (მაგ. 1 აბი)", text: $med.dose)
                    TextField("შენიშვნა (მაგ. ჭამის შემდეგ)", text: $med.note)
                }

                Section("დროები") {
                    ForEach($med.times) { $t in
                        DatePicker("დრო", selection: Binding(
                            get: { MedStore.todayAt(t) },
                            set: { d in
                                let c = Calendar.current.dateComponents([.hour, .minute], from: d)
                                t.hour = c.hour ?? 9
                                t.minute = c.minute ?? 0
                            }), displayedComponents: .hourAndMinute)
                    }
                    .onDelete { med.times.remove(atOffsets: $0) }
                    Button {
                        let last = med.times.last
                        med.times.append(DoseTime(hour: min(23, (last?.hour ?? 8) + 4), minute: last?.minute ?? 0))
                    } label: { Label("დროის დამატება", systemImage: "plus.circle.fill") }
                }

                Section("დღეები") {
                    HStack(spacing: 6) {
                        ForEach(Weekdays.order, id: \.self) { d in
                            let on = med.weekdays.contains(d)
                            Text(Weekdays.short(d))
                                .font(.caption.bold())
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(on ? Color.teal : Color.gray.opacity(0.2), in: Capsule())
                                .foregroundStyle(on ? .white : .primary)
                                .onTapGesture {
                                    if on { med.weekdays.remove(d) } else { med.weekdays.insert(d) }
                                }
                        }
                    }
                }

                Section {
                    Toggle("აბების დათვლა", isOn: $trackStock)
                    if trackStock {
                        Stepper("დარჩენილია: \(stockValue)", value: $stockValue, in: 0...999)
                        Stepper("გამაფრთხილე, როცა დარჩება \(med.lowStock)", value: $med.lowStock, in: 0...100)
                    }
                } footer: { Text("„მივიღე“-ზე ყოველ ჯერზე ერთი აკლდება.") }

                Section {
                    Toggle("შეხსენებები ჩართულია", isOn: $med.isActive)
                    if !isNew {
                        Button {
                            Task {
                                await AlarmService.scheduleOneOff(medID: med.id, after: 10, scheduled: .now.addingTimeInterval(10))
                                testSent = true
                            }
                        } label: { Label(testSent ? "10 წამში დარეკავს — ჩაკეტე ტელეფონი" : "ტესტი: დარეკე 10 წამში",
                                         systemImage: "bell.and.waves.left.and.right") }
                    }
                }

                if !isNew {
                    Section {
                        Button("წამლის წაშლა", role: .destructive) {
                            MedStore.shared.delete(med)
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle(isNew ? "ახალი წამალი" : med.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("გაუქმება") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("შენახვა") {
                        saving = true
                        med.stock = trackStock ? stockValue : nil
                        med.name = med.name.trimmingCharacters(in: .whitespaces)
                        Task {
                            await MedStore.shared.save(med, image: image, imageChanged: imageChanged)
                            dismiss()
                        }
                    }
                    .disabled(med.name.trimmingCharacters(in: .whitespaces).isEmpty || med.times.isEmpty || saving)
                }
            }
            .onChange(of: pickerItem) { _, item in
                Task {
                    if let data = try? await item?.loadTransferable(type: Data.self), let img = UIImage(data: data) {
                        image = img
                        imageChanged = true
                    }
                }
            }
            .fullScreenCover(isPresented: $showCamera) {
                CameraPicker { img in
                    image = img
                    imageChanged = true
                }
                .ignoresSafeArea()
            }
        }
    }
}

struct CameraPicker: UIViewControllerRepresentable {
    var onPick: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let p = UIImagePickerController()
        p.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera) ? .camera : .photoLibrary
        p.delegate = context.coordinator
        return p
    }
    func updateUIViewController(_ vc: UIImagePickerController, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: CameraPicker
        init(_ parent: CameraPicker) { self.parent = parent }
        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let img = info[.originalImage] as? UIImage { parent.onPick(img) }
            parent.dismiss()
        }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { parent.dismiss() }
    }
}

// MARK: - ისტორია

struct HistoryView: View {
    @Bindable var store = MedStore.shared

    var body: some View {
        NavigationStack {
            List {
                let a = store.adherence()
                Section("ბოლო 7 დღე") {
                    HStack {
                        Gauge(value: a.due == 0 ? 1 : Double(a.taken) / Double(a.due)) {
                            EmptyView()
                        } currentValueLabel: {
                            Text(a.due == 0 ? "—" : "\(Int(Double(a.taken) / Double(a.due) * 100))%")
                        }
                        .gaugeStyle(.accessoryCircularCapacity)
                        .tint(.teal)
                        .scaleEffect(1.3)
                        .padding(8)
                        VStack(alignment: .leading) {
                            Text("მიღებულია \(a.taken) / \(a.due)").font(.headline)
                            Text("დროულად მიღებული დოზები").font(.subheadline).foregroundStyle(.secondary)
                        }
                        .padding(.leading, 12)
                    }
                }
                let grouped = Dictionary(grouping: store.logs.suffix(300)) { Calendar.current.startOfDay(for: $0.date) }
                ForEach(grouped.keys.sorted(by: >), id: \.self) { day in
                    Section(day.formatted(.dateTime.weekday(.wide).day().month())) {
                        ForEach(grouped[day]!.sorted { $0.date > $1.date }) { log in
                            HStack {
                                Image(systemName: icon(log.action)).foregroundStyle(color(log.action))
                                VStack(alignment: .leading) {
                                    Text(log.medName).font(.body)
                                    Text("გრაფიკით \(log.scheduled.formatted(date: .omitted, time: .shortened))")
                                        .font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Text(log.date.formatted(date: .omitted, time: .shortened))
                                    .font(.subheadline).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("ისტორია")
        }
    }

    func icon(_ a: DoseAction) -> String {
        switch a { case .taken: "checkmark.circle.fill"; case .snoozed: "clock.fill"; case .skipped: "xmark.circle.fill" }
    }
    func color(_ a: DoseAction) -> Color {
        switch a { case .taken: .green; case .snoozed: .orange; case .skipped: .gray }
    }
}
