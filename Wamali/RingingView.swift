import SwiftUI
import UIKit

/// სრული ეკრანი, როცა მაღვიძარა რეკავს და აპი ღიაა (ან გახსნი რეკვის დროს)
struct RingingView: View {
    let info: RingingInfo
    @Bindable var store = MedStore.shared
    @State private var pulse = false

    var body: some View {
        let med = store.med(info.medID)
        ZStack {
            LinearGradient(colors: [Color.teal, Color(red: 0.05, green: 0.25, blue: 0.3)],
                           startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()

            VStack(spacing: 20) {
                Text(info.scheduled.formatted(date: .omitted, time: .shortened))
                    .font(.system(size: 64, weight: .thin, design: .rounded))
                    .foregroundStyle(.white)
                    .padding(.top, 30)

                Text("წამლის დროა")
                    .font(.title3.weight(.medium))
                    .foregroundStyle(.white.opacity(0.85))

                ZStack {
                    Circle().fill(.white.opacity(0.15))
                        .frame(width: 270, height: 270)
                        .scaleEffect(pulse ? 1.12 : 0.95)
                        .opacity(pulse ? 0.2 : 0.7)
                    if let med {
                        MedThumb(med: med, size: 220)
                            .overlay(RoundedRectangle(cornerRadius: 55).stroke(.white, lineWidth: 4))
                            .shadow(radius: 20)
                    }
                }
                .padding(.vertical, 10)

                VStack(spacing: 6) {
                    Text(med?.name ?? "წამალი")
                        .font(.system(size: 38, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                    if let dose = med?.dose, !dose.isEmpty {
                        Text(dose).font(.title2).foregroundStyle(.white.opacity(0.9))
                    }
                    if let note = med?.note, !note.isEmpty {
                        Label(note, systemImage: "info.circle")
                            .font(.headline).foregroundStyle(.white.opacity(0.85))
                    }
                }
                .padding(.horizontal)

                Spacer()

                VStack(spacing: 14) {
                    Button {
                        store.taken(alarmID: info.alarmID)
                    } label: {
                        Label("მივიღე", systemImage: "checkmark.circle.fill")
                            .font(.title2.bold())
                            .frame(maxWidth: .infinity, minHeight: 64)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.green)

                    HStack(spacing: 14) {
                        Button {
                            Task { await store.snooze(alarmID: info.alarmID) }
                        } label: {
                            Label("5 წუთში", systemImage: "clock.arrow.circlepath")
                                .font(.headline)
                                .frame(maxWidth: .infinity, minHeight: 54)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.orange)

                        Button {
                            store.skip(alarmID: info.alarmID)
                        } label: {
                            Label("გამოტოვება", systemImage: "xmark")
                                .font(.headline)
                                .frame(maxWidth: .infinity, minHeight: 54)
                        }
                        .buttonStyle(.bordered)
                        .tint(.white)
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 30)
            }
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 1).repeatForever(autoreverses: true)) { pulse = true }
            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
        }
    }
}
