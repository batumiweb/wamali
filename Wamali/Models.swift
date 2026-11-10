import Foundation
import SwiftUI
import UIKit

// MARK: - მონაცემები

struct DoseTime: Codable, Hashable, Identifiable {
    var id = UUID()
    var hour: Int
    var minute: Int
    var alarmID = UUID()

    var label: String { String(format: "%02d:%02d", hour, minute) }
}

struct Medication: Codable, Identifiable, Hashable {
    var id = UUID()
    var name: String = ""
    var dose: String = ""          // მაგ. "1 აბი"
    var note: String = ""          // მაგ. "ჭამის შემდეგ"
    var times: [DoseTime] = [DoseTime(hour: 9, minute: 0)]
    var weekdays: Set<Int> = Set(1...7)   // Calendar weekday: 1 = კვირა ... 7 = შაბათი
    var stock: Int? = nil                 // რამდენი აბი დარჩა (nil = არ ითვლება)
    var lowStock: Int = 5
    var photoFile: String? = nil
    var isActive = true

    var timesSummary: String {
        times.sorted { ($0.hour, $0.minute) < ($1.hour, $1.minute) }.map(\.label).joined(separator: ", ")
    }
}

enum DoseAction: String, Codable { case taken, snoozed, skipped }

struct DoseLog: Codable, Identifiable, Hashable {
    var id = UUID()
    var medID: UUID
    var medName: String
    var scheduled: Date
    var date: Date = .now
    var action: DoseAction
}

struct SnoozeInfo: Codable, Hashable {
    var medID: UUID
    var scheduled: Date
    var created: Date = .now
}

struct DoseSlot: Identifiable {
    let med: Medication
    let time: DoseTime
    let date: Date
    var id: String { "\(med.id)-\(time.id)" }
}

struct RingingInfo: Identifiable, Equatable {
    let alarmID: UUID
    let medID: UUID
    let scheduled: Date
    var id: UUID { alarmID }
}

private struct SaveData: Codable {
    var meds: [Medication] = []
    var logs: [DoseLog] = []
    var snoozes: [UUID: SnoozeInfo] = [:]
}

// MARK: - საცავი

@MainActor @Observable
final class MedStore {
    static let shared = MedStore()

    var meds: [Medication] = []
    var logs: [DoseLog] = []
    var snoozes: [UUID: SnoozeInfo] = [:]
    var ringing: RingingInfo? = nil

    private let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    private var dataURL: URL { dir.appendingPathComponent("data.json") }

    init() { load() }

    func load() {
        guard let data = try? Data(contentsOf: dataURL),
              let saved = try? JSONDecoder().decode(SaveData.self, from: data) else { return }
        meds = saved.meds
        logs = saved.logs
        // ძველი "შემახსენე" ჩანაწერების გასუფთავება
        snoozes = saved.snoozes.filter { $0.value.created > Date.now.addingTimeInterval(-2 * 86400) }
    }

    func persist() {
        let saved = SaveData(meds: meds, logs: logs, snoozes: snoozes)
        if let data = try? JSONEncoder().encode(saved) {
            try? data.write(to: dataURL, options: .atomic)
        }
    }

    func med(_ id: UUID) -> Medication? { meds.first { $0.id == id } }

    // MARK: წამლის შენახვა / წაშლა

    func save(_ newMed: Medication, image: UIImage?, imageChanged: Bool) async {
        var m = newMed
        let old = med(m.id)
        if imageChanged {
            if let f = old?.photoFile { try? FileManager.default.removeItem(at: dir.appendingPathComponent(f)) }
            m.photoFile = nil
            if let image, let data = image.resized(to: 900).jpegData(compressionQuality: 0.8) {
                let name = "photo-\(UUID().uuidString).jpg"
                try? data.write(to: dir.appendingPathComponent(name))
                m.photoFile = name
            }
        }
        if let old { AlarmService.cancel(old) }
        // ახალი ID-ები, რომ ძველ მაღვიძარებს არ შეეჯახოს
        m.times = m.times.map { var t = $0; t.alarmID = UUID(); return t }
        if let i = meds.firstIndex(where: { $0.id == m.id }) { meds[i] = m } else { meds.append(m) }
        persist()
        await AlarmService.schedule(m)
    }

    func delete(_ m: Medication) {
        AlarmService.cancel(m)
        if let f = m.photoFile { try? FileManager.default.removeItem(at: dir.appendingPathComponent(f)) }
        meds.removeAll { $0.id == m.id }
        persist()
    }

    func photo(for m: Medication) -> UIImage? {
        guard let f = m.photoFile else { return nil }
        return UIImage(contentsOfFile: dir.appendingPathComponent(f).path)
    }

    // MARK: მაღვიძარა → წამალი

    func resolve(alarmID: UUID) -> RingingInfo? {
        for m in meds {
            if let t = m.times.first(where: { $0.alarmID == alarmID }) {
                return RingingInfo(alarmID: alarmID, medID: m.id, scheduled: Self.todayAt(t))
            }
        }
        if let s = snoozes[alarmID] {
            return RingingInfo(alarmID: alarmID, medID: s.medID, scheduled: s.scheduled)
        }
        return nil
    }

    static func todayAt(_ t: DoseTime) -> Date {
        Calendar.current.date(bySettingHour: t.hour, minute: t.minute, second: 0, of: .now) ?? .now
    }

    // MARK: მოქმედებები (ეკრანიდანაც და ჩაკეტილი ეკრანის ღილაკებიდანაც)

    func taken(alarmID: UUID) {
        guard let info = resolve(alarmID: alarmID) else { return }
        record(.taken, medID: info.medID, scheduled: info.scheduled)
        AlarmService.stop(alarmID)
        clearRinging(alarmID)
    }

    func snooze(alarmID: UUID, minutes: Int = 5) async {
        guard let info = resolve(alarmID: alarmID) else { return }
        record(.snoozed, medID: info.medID, scheduled: info.scheduled)
        AlarmService.stop(alarmID)
        clearRinging(alarmID)
        await AlarmService.scheduleOneOff(medID: info.medID, after: TimeInterval(minutes * 60), scheduled: info.scheduled)
    }

    func skip(alarmID: UUID) {
        guard let info = resolve(alarmID: alarmID) else { return }
        record(.skipped, medID: info.medID, scheduled: info.scheduled)
        AlarmService.stop(alarmID)
        clearRinging(alarmID)
    }

    private func clearRinging(_ id: UUID) {
        if ringing?.alarmID == id { ringing = nil }
    }

    func record(_ action: DoseAction, medID: UUID, scheduled: Date) {
        guard let i = meds.firstIndex(where: { $0.id == medID }) else { return }
        // თუ ამ დოზაზე უკვე მივიღე/გამოვტოვე ეწერა — აღარ ვამატებთ
        if action != .snoozed, status(medID: medID, scheduled: scheduled) != nil { return }
        logs.append(DoseLog(medID: medID, medName: meds[i].name, scheduled: scheduled, action: action))
        if action == .taken, let s = meds[i].stock { meds[i].stock = max(0, s - 1) }
        persist()
    }

    func undo(medID: UUID, scheduled: Date) {
        guard let idx = logs.lastIndex(where: { $0.medID == medID && abs($0.scheduled.timeIntervalSince(scheduled)) < 60 && $0.action != .snoozed }) else { return }
        if logs[idx].action == .taken, let i = meds.firstIndex(where: { $0.id == medID }), let s = meds[i].stock {
            meds[i].stock = s + 1
        }
        logs.remove(at: idx)
        persist()
    }

    // MARK: დღევანდელი გრაფიკი და სტატისტიკა

    func status(medID: UUID, scheduled: Date) -> DoseAction? {
        logs.last { $0.medID == medID && abs($0.scheduled.timeIntervalSince(scheduled)) < 60 && $0.action != .snoozed }?.action
    }

    func slots(on day: Date) -> [DoseSlot] {
        let cal = Calendar.current
        let wd = cal.component(.weekday, from: day)
        var result: [DoseSlot] = []
        for m in meds where m.isActive && m.weekdays.contains(wd) {
            for t in m.times {
                if let d = cal.date(bySettingHour: t.hour, minute: t.minute, second: 0, of: day) {
                    result.append(DoseSlot(med: m, time: t, date: d))
                }
            }
        }
        return result.sorted { $0.date < $1.date }
    }

    var lowStockMeds: [Medication] {
        meds.filter { m in m.isActive && (m.stock.map { $0 <= m.lowStock } ?? false) }
    }

    /// ბოლო 7 დღის შესრულება (%)
    func adherence(days: Int = 7) -> (taken: Int, due: Int) {
        let cal = Calendar.current
        var taken = 0, due = 0
        for offset in 0..<days {
            guard let day = cal.date(byAdding: .day, value: -offset, to: .now) else { continue }
            for s in slots(on: day) where s.date < .now {
                due += 1
                if status(medID: s.med.id, scheduled: s.date) == .taken { taken += 1 }
            }
        }
        return (taken, due)
    }
}

extension UIImage {
    func resized(to maxSide: CGFloat) -> UIImage {
        let scale = min(1, maxSide / max(size.width, size.height))
        let newSize = CGSize(width: size.width * scale, height: size.height * scale)
        return UIGraphicsImageRenderer(size: newSize).image { _ in draw(in: CGRect(origin: .zero, size: newSize)) }
    }
}

enum Weekdays {
    /// ორშაბათიდან კვირამდე (Calendar weekday ნომრები)
    static let order = [2, 3, 4, 5, 6, 7, 1]
    static func short(_ d: Int) -> String {
        ["", "კვ", "ორ", "სა", "ოთ", "ხუ", "პა", "შა"][d]
    }
}
