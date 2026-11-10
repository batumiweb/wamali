# წამალი — შეხსენების აპი (iOS 26+)

## რას აკეთებს
- წამალს უწერ სახელს, დოზას, შენიშვნას (მაგ. „ჭამის შემდეგ“), **ფოტოს** (კამერა/გალერეა), დროებს და კვირის დღეებს.
- დროზე **რეკავს ნამდვილი მაღვიძარასავით** (Apple AlarmKit): ჩუმ რეჟიმსა და „Focus“-ს გადის, ჩაკეტილ ეკრანზე მთელ ეკრანზე ჩნდება ღილაკებით **„მივიღე“** და **„5 წუთში“**.
- აპი თუ ღიაა რეკვისას → სრული ეკრანი **წამლის ფოტოთი** და ღილაკებით: მივიღე / 5 წუთში / გამოტოვება.
- **დღეს** ჩანართი: დღევანდელი გრაფიკი, რა მიიღე ✓ და რა გამოგრჩა (ნარინჯისფერი). ხანგრძლივი დაჭერა ✓-ზე აუქმებს.
- **აბების დათვლა**: „მივიღე“ ყოველ ჯერზე ერთს აკლებს, მარაგის დაბლა ჩამოსვლისას გაფრთხილება.
- **ისტორია**: ბოლო 7 დღის შესრულების % და ჟურნალი.
- **ტესტის ღილაკი** წამლის რედაქტირებაში: 10 წამში დარეკავს.

> შენიშვნა: ჩაკეტილი ეკრანის სისტემურ მაღვიძარაზე Apple ფოტოს ჩასმის საშუალებას არ იძლევა — იქ ჩანს სახელი და დოზა. ფოტო ჩანს აპის სრულ ეკრანზე.

## რა გჭირდება
1. **Apple Developer Program** ანგარიში ($99/წელი) — TestFlight-ისთვის აუცილებელია.
2. App Store Connect-ში → **Apps → + New App**, Bundle ID: `ge.gela.wamali` (ან შენი; მაშინ `project.yml`-შიც შეცვალე).
3. iPhone **iOS 26** ან უფრო ახალი.

## ვარიანტი A — Mac-ით
```bash
brew install xcodegen
cd Wamali
xcodegen generate
open Wamali.xcodeproj
```
Xcode 26 → Signing & Capabilities → აირჩიე შენი Team → **Product → Archive** → **Distribute App → TestFlight & App Store** → Upload.

## ვარიანტი B — Mac-ის გარეშე (GitHub Actions)
1. ეს საქაღალდე ატვირთე GitHub-ზე (private რეპო).
2. App Store Connect → **Users and Access → Integrations → App Store Connect API** → შექმენი გასაღები **Admin** როლით, ჩამოტვირთე `.p8`.
3. GitHub რეპო → Settings → Secrets and variables → Actions → დაამატე:
   - `ASC_KEY_ID` — გასაღების ID
   - `ASC_ISSUER_ID` — Issuer ID (იმავე გვერდზე ზემოთ)
   - `ASC_KEY_P8` — `.p8` ფაილის მთელი შიგთავსი
   - `TEAM_ID` — developer.apple.com → Membership → Team ID
4. Actions → **TestFlight** → **Run workflow**. ~10–15 წუთში build TestFlight-ში გამოჩნდება.

## TestFlight-ში
App Store Connect → შენი აპი → TestFlight → დაამატე თავი Internal Testers-ში → iPhone-ზე TestFlight აპიდან დააინსტალირე.
პირველ გახსნაზე დაეთანხმე **მაღვიძარების** ნებართვას.

---

# Android (APK)

კოდი `android/` საქაღალდეშია (Kotlin + Jetpack Compose, Android 8.1+).

- GitHub-ზე ყოველი ცვლილებისას **Actions → Android APK** ავტომატურად აწყობს APK-ს.
- ჩამოსატვირთი ბმული: რეპოს **Releases → latest → wamali.apk**.
- ტელეფონზე: გახსენი ფაილი → „უცნობი წყაროებიდან დაყენების“ ნება → Install.
- პირველ გახსნაზე წითელ ბარათებზე დააჭირე „ჩართვა“ (შეტყობინებები, სრული ეკრანი, ზუსტი დრო, ბატარეა).
