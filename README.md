This is a Kotlin Multiplatform project targeting Android, Desktop (JVM).

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications. It contains
  several subfolders:
    - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name. For
      example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls. Similarly, if you want
      to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
      folder is the appropriate location.

### Экраны

Навигация простая, без библиотеки навигации: back stack живёт в
[AppNavigator](shared/src/commonMain/kotlin/com/github/tor1ant/govnach/ui/navigation/AppRoute.kt),
а маршруты описаны в `AppRoute`.

1. [HomeScreen](shared/src/commonMain/kotlin/com/github/tor1ant/govnach/ui/HomeScreen.kt) — список досок.
   Клик по доске открывает её треды.
2. [BoardScreen](shared/src/commonMain/kotlin/com/github/tor1ant/govnach/ui/BoardScreen.kt) — треды доски
   в порядке bump. Клик по треду открывает сам тред.
3. [ThreadScreen](shared/src/commonMain/kotlin/com/github/tor1ant/govnach/ui/ThreadScreen.kt) — посты треда
   от ОП-поста до последнего.

Назад — кнопка в `TopAppBar`. На Android back stack ещё подключён к системной кнопке «назад»
в [MainActivity](androidApp/src/main/kotlin/com/github/tor1ant/govnach/MainActivity.kt), поэтому back
возвращает с доски/треда, а не закрывает приложение.

### Апи

Клиент генерируется из [openapi/api.yml](openapi/api.yml) задачей `:shared:openApiGenerate`, руки
поверх него — [Client](shared/src/commonMain/kotlin/com/github/tor1ant/govnach/client/Client.kt).

- Доски: `GET /api/mobile/v2/boards`
- Треды доски: `GET /{board}/catalog.json` — это HTML JSON API двача, официальной частью апи не является.
- Посты треда: `GET /api/mobile/v2/after/{board}/{thread}/{num}`, идём от номера треда и докупаем хвост.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
    - Hot reload: `./gradlew :desktopApp:hotRun --auto`
    - Standard run: `./gradlew :desktopApp:run`

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:desktopAppTest`
- Прогон по реальному апи двача (обращается в сеть, по умолчанию пропускается):
  `GOVNACH_API_TEST=1 ./gradlew :shared:desktopAppTest`

Если в терминале нет `JAVA_HOME`, укажите его в JDK 21, например
`JAVA_HOME=C:\Users\<user>\.jdks\corretto-21` перед вызовом `gradlew`.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…