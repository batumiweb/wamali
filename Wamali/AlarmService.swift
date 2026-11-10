import Foundation
import SwiftUI
import AlarmKit
import AppIntents

struct MedAlarmMeta: AlarmMetadata {
    var medID: String
}

typealias MedAlarmConfig = AlarmManager.AlarmConfiguration<MedAlarmMeta>

/// AlarmKit (iOS 26+) — ნამდვილი მაღვიძარა: ჩუმ რეჟიმსაც გადის და ჩაკეტილ ეკრანზე მთელ ეკრანზე რეკავს.
@MainActor
enum AlarmService {
    static var manager: AlarmManager { AlarmManager.shared }

    static var isAuthorized: Bool { manager.authorizationState == .authorized }

    @discardableResult
    static func requestAuth() async -> Bool {
        switch manager.authorizationState {
        case .authorized: return true
        case .denied: return false
        case .notDetermined:
            let state = try? await manager.requestAuthorization()
            return state == .authorized
        @unknown default: return false
        }
    }

    static func attributes(for med: Medication) -> AlarmAttributes<MedAlarmMeta> {
        let stop = AlarmButton(text: "მივიღე", textColor: .white, systemImageName: "checkmark.circle.fill")
        let snooze = AlarmButton(text: "5 წუთში", textColor: .white, systemImageName: "clock.arrow.circlepath")
        let title = med.dose.isEmpty ? "💊 \(med.name)" : "💊 \(med.name) · \(med.dose)"
        let alert = AlarmPresentation.Alert(
            title: LocalizedStringResource(stringLiteral: title),
            stopButton: stop,
            secondaryButton: snooze,
            secondaryButtonBehavior: .custom
        )
        return AlarmAttributes(
            presentation: AlarmPresentation(alert: alert),
            metadata: MedAlarmMeta(medID: med.id.uuidString),
            tintColor: .teal
        )
    }

    private static func localeWeekday(_ d: Int) -> Locale.Weekday {
        switch d {
        case 1: return .sunday
        case 2: return .monday
        case 3: return .tuesday
        case 4: return .wednesday
        case 5: return .thursday
        case 6: return .friday
        default: return .saturday
        }
    }

    /// წამლის ყველა დროის განმეორებადი მაღვიძარა
    static func schedule(_ med: Medication) async {
        guard med.isActive, !med.weekdays.isEmpty, await requestAuth() else { return }
        let days = Weekdays.order.filter { med.weekdays.contains($0) }.map(localeWeekday)
        for t in med.times {
            let rel = Alarm.Schedule.Relative(
                time: .init(hour: t.hour, minute: t.minute),
                repeats: .weekly(days)
            )
            let config = MedAlarmConfig(
                schedule: .relative(rel),
                attributes: attributes(for: med),
                stopIntent: TakenIntent(alarmID: t.alarmID.uuidString),
                secondaryIntent: SnoozeIntent(alarmID: t.alarmID.uuidString)
            )
            do { _ = try await manager.schedule(id: t.alarmID, configuration: config) }
            catch { print("Alarm schedule error:", error) }
        }
    }

    /// ერთჯერადი მაღვიძარა ("5 წუთში შემახსენე" და ტესტი)
    static func scheduleOneOff(medID: UUID, after seconds: TimeInterval, scheduled: Date) async {
        let store = MedStore.shared
        guard let med = store.med(medID), await requestAuth() else { return }
        let id = UUID()
        store.snoozes[id] = SnoozeInfo(medID: medID, scheduled: scheduled)
        store.persist()
        let config = MedAlarmConfig(
            schedule: .fixed(Date.now.addingTimeInterval(seconds)),
            attributes: attributes(for: med),
            stopIntent: TakenIntent(alarmID: id.uuidString),
            secondaryIntent: SnoozeIntent(alarmID: id.uuidString)
        )
        do { _ = try await manager.schedule(id: id, configuration: config) }
        catch { print("One-off alarm error:", error) }
    }

    static func cancel(_ med: Medication) {
        for t in med.times { try? manager.cancel(id: t.alarmID) }
    }

    static func stop(_ id: UUID) {
        try? manager.stop(id: id)
    }

    /// გაშვებისას: ზედმეტი მაღვიძარების წაშლა და დაკარგულების აღდგენა
    static func syncAll() async {
        guard await requestAuth() else { return }
        let store = MedStore.shared
        let existing = Set(((try? manager.alarms) ?? []).map(\.id))
        var known = Set(store.snoozes.keys)
        for m in store.meds where m.isActive { m.times.forEach { known.insert($0.alarmID) } }
        for id in existing where !known.contains(id) { try? manager.cancel(id: id) }
        for m in store.meds where m.isActive && m.times.contains(where: { !existing.contains($0.alarmID) }) {
            cancel(m)
            await schedule(m)
        }
    }
}

// MARK: - ჩაკეტილი ეკრანის ღილაკები

struct TakenIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "მივიღე"
    static let openAppWhenRun = false

    @Parameter(title: "Alarm ID") var alarmID: String

    init() {}
    init(alarmID: String) { self.alarmID = alarmID }

    @MainActor
    func perform() async throws -> some IntentResult {
        if let id = UUID(uuidString: alarmID) { MedStore.shared.taken(alarmID: id) }
        return .result()
    }
}

struct SnoozeIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "5 წუთში შემახსენე"
    static let openAppWhenRun = false

    @Parameter(title: "Alarm ID") var alarmID: String

    init() {}
    init(alarmID: String) { self.alarmID = alarmID }

    @MainActor
    func perform() async throws -> some IntentResult {
        if let id = UUID(uuidString: alarmID) { await MedStore.shared.snooze(alarmID: id) }
        return .result()
    }
}
