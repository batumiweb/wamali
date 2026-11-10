import SwiftUI
import AlarmKit

@main
struct WamaliApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .tint(.teal)
        }
    }
}

struct ContentView: View {
    @Bindable var store = MedStore.shared
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        TabView {
            Tab("დღეს", systemImage: "sun.max.fill") { TodayView() }
            Tab("წამლები", systemImage: "pills.fill") { MedsView() }
            Tab("ისტორია", systemImage: "chart.bar.fill") { HistoryView() }
        }
        .fullScreenCover(item: $store.ringing) { info in
            RingingView(info: info)
        }
        .task {
            await AlarmService.syncAll()
        }
        .task {
            // როცა აპი ღიაა და მაღვიძარა რეკავს — ფოტოიანი სრული ეკრანი
            for await alarms in AlarmManager.shared.alarmUpdates {
                if let a = alarms.first(where: { $0.state == .alerting }),
                   let info = store.resolve(alarmID: a.id) {
                    if store.ringing?.alarmID != a.id { store.ringing = info }
                } else if let r = store.ringing,
                          !alarms.contains(where: { $0.id == r.alarmID && $0.state == .alerting }) {
                    store.ringing = nil
                }
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { store.load() }
        }
    }
}
