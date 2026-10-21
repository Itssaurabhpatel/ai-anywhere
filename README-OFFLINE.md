# Complete Offline Guide (No Internet Needed)

is folder ko aap kisi bhi computer me bina internet ke bilkul aasani se chala sakte hain!

---

## What is Pre-Bundled in this Folder:
1. **`.gradle-offline\`**: Complete offline cache containing Gradle 8.2, Kotlin plugins, Android SDK Build-Tools 33, Platform 34, AndroidX libraries, and Google Services.
2. **`node_modules\`**: Complete Node.js dependencies pre-installed for the backend.
3. **Offline Build Scripts**: `build-offline.bat` to build the Android app without internet.

---

## Step 1: Run Android App Offline

### Option A: Using `build-offline.bat` (Command Line)
1. Open PowerShell or Command Prompt.
2. Navigate to the `android` directory:
   ```cmd
   cd "path\to\ant\android"
   ```
3. Set your `JAVA_HOME` to JDK 17 (or JDK 18 / JDK 21):
   ```cmd
   set "JAVA_HOME=C:\Program Files\Java\jdk-17"
   ```
4. Run the offline build command:
   ```cmd
   build-offline.bat
   ```
5. **Output APK File**:
   `app\build\outputs\apk\debug\app-debug.apk`

---

### Option B: In Android Studio

1. Open **Android Studio**.
2. Go to **File ➔ Settings ➔ Build, Execution, Deployment ➔ Build Tools ➔ Gradle**.
3. Under **Gradle JDK**, select **JDK 17** (e.g. `C:\Program Files\Java\jdk-17`).
4. Click **Apply** and **OK**.
5. Click **Sync Project with Gradle Files**.
6. Press **Run** (`Shift + F10`) to launch on device/emulator.

---

## Step 2: Run Backend Server Offline

1. Open PowerShell or Command Prompt.
2. Navigate to the `backend` directory:
   ```cmd
   cd "path\to\ant\backend"
   ```
3. Start the server (Node.js dependencies are already inside `node_modules`):
   ```cmd
   npm start
   ```
   *or if npm path issue occurs on Windows:*
   ```cmd
   & "C:\Program Files\nodejs\npm.cmd" start
   ```
4. The backend server will run on `http://localhost:8080` in **Mock AI Mode**.
