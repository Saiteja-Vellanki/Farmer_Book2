LABOUR CALCULATOR - Android App
================================

FEATURES
- Add labourer: Name, Mobile, Place, Hours worked, Cost per hour
- Auto-calculates: Total = Hours x Rate, Balance = Total - Paid
- Green PAID badge when fully paid, red DUE badge with balance amount
- "Mark Paid" button settles the full amount in one tap
- "Call" button opens the dialer with the labourer's number
- Tap a card to edit, long-press to delete
- Top bar shows total labourers, paid count, and total pending amount
- Reminder notification EVERY 6 HOURS listing all unpaid labourers
  with their balance (works even when the app is closed)
- All data saved on the phone - fully offline

HOW TO BUILD THE APK
1. Install Android Studio (free): https://developer.android.com/studio
2. Open Android Studio -> Open -> select this LabourCalculator folder
3. Wait for Gradle sync to finish (first time needs internet)
4. Menu: Build -> Build Bundle(s) / APK(s) -> Build APK(s)
5. APK will be at: app/build/outputs/apk/debug/app-debug.apk
6. Copy that APK to your phone and install
   (allow "Install from unknown sources" when asked)

Or to run directly on your phone:
1. Enable Developer Options + USB Debugging on the phone
2. Connect phone via USB, press the green Run button

NOTES
- On Android 13+ the app asks notification permission on first
  launch - press Allow, otherwise the 6-hour reminder can't show.
- Android may delay periodic work slightly to save battery;
  reminders fire roughly every 6 hours, not to the exact minute.
