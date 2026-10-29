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
   в порядке bump. Клик по треду открывает сам тред; кнопка «+ новый тред» в шапке ведёт на экран
   создания треда (видна, если доска разрешает постинг).
3. [NewThreadScreen](shared/src/commonMain/kotlin/com/github/tor1ant/govnach/ui/NewThreadScreen.kt) —
   создание ОП-поста: тот же композер, только без номера треда. После успеха экран заменяется на свежий
   тред (`AppNavigator.replaceWithThread`), чтобы «назад» не возвращал в форму создания.
4. [ThreadScreen](shared/src/commonMain/kotlin/com/github/tor1ant/govnach/ui/ThreadScreen.kt) — посты треда
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
- Отправка поста: `POST /user/posting`, см. ниже про ключи приложения.
- Код ошибки двача в апи оставлен `integer`: строгий enum ронял бы разбор ответа на незнакомом коде,
  поэтому подписи кодов держатся в функции `errorCodeMessage`. По тому же принципу тип файла — строка.

### Медиа в треде

Посты треда показывают медиа, а не только текст:

- Картинки (jpg/png/gif/webp/bmp/apng) — превью по `FileModel.thumbnail`, клик открывает полноэкранный
  просмотр исходника по `FileModel.path` с зумом (колесо/пинч) и паном. GIF/webp играются как статичный
  первый кадр — декодер один кадр и умеет.
- Видео (mp4/webm) — превью с меткой и длительностью; клик открывает плеер: на Android inline через
  media3/ExoPlayer ([VideoPlayer.android.kt](shared/src/androidMain/kotlin/com/github/tor1ant/govnach/ui/media/VideoPlayer.android.kt)),
  на desktop — системный плеер/браузер
  ([VideoPlayer.desktopApp.kt](shared/src/desktopAppMain/kotlin/com/github/tor1ant/govnach/ui/media/VideoPlayer.desktopApp.kt)),
  потому что video-рендеринга в swing-окне compose desktop нет.
- Аудио, стикеры и прочие файлы — кнопка «открыть в системе».
- nsfw-файлы размыты до тапа.

Загрузка картинок: `MediaImageLoader` качает байты отдельным ktor-клиентом с okhttp disk-кэшем
(AppModule) и декодирует в `ImageBitmap`, битмапы держит в LRU-кэше. Пути к файлам собирает
`MediaPaths` из `FileModel.path`/`thumbnail` — они относительные, base совпадает с `ApiClient.BASE_URL`.

### Отправка ответов

Композер общий для ответа в тред и для создания треда:
[PostingComposer](shared/src/commonMain/kotlin/com/github/tor1ant/govnach/ui/PostingComposer.kt) —
комментарий, поля «Имя» и «Тема» если доска их разрешает (`enable_names`/`enable_subject`), и строка
«Файл (путь)». Клик по номеру поста вставляет кросспост `>>num`. Композер неактивен на закрытых тредах
и на досках с запрещённым постингом.

- Капча решается эмодзи-капчей, ключи приложения не нужны: `GET /api/captcha/emoji/id?board=…` даёт
  `id`, `GET /api/captcha/emoji/show?id=…` — картинку с нужными emoji и клавиатуру вариантов, а
  `POST /api/captcha/emoji/click` ({`captchaTokenID`, `emojiNumber`}) обновляет клавиатуру. После
  последнего emoji в ответе приходит `success` — это и есть `emoji_captcha_id` для поста.
  Диалог: [EmojiCaptchaDialog](shared/src/commonMain/kotlin/com/github/tor1ant/govnach/ui/EmojiCaptchaDialog.kt),
  логика — в `ThreadViewModel`/`NewThreadViewModel`.
- Отправка: `POST /user/posting` с `captcha_type=emoji_captcha` и `emoji_captcha_id`.
  Если в окружении заданы ключи приложения, можно идти через `appid`: `app_response_id` из
  `GET /api/captcha/app/id/{public_key}` (живёт 180 с) и
  `app_response = sha256(app_response_id + '|' + private_key)`. Ключи выдаёт admin@2ch.hk.
- Важно: двач отдаёт `success` после четвёртого клика независимо от того, верные ли emoji выбраны, —
  правильность проверяется уже при отправке поста и приходит как `-5 «Капча невалидна»`. Поэтому
  выбирать нужно именно то, что нарисовано на картинке, иначе пост не пройдёт.
- Файл идёт полем `file[]`: путь пишем прямо в композере — системного диалога выбора файла в Compose
  нет, поэтому байты читаются в момент отправки, а имя ставится в `Content-Disposition`. Имя поля
  задаём сами: сгенерированный клиент отдаёт FormPart как есть.
- Черновик проверяется локально до запроса — по настройкам доски: длина комментария (`max_comment`,
  символы), лимит файла (`max_files_size` в КБ, на `/b/` 40960 = 40 МБ), разрешённые типы файлов и
  обязательный файл для нового треда. Правила собирает `postingRules`, проверяет `validatePosting`.
- Ошибки двача показываются снэкбаром: если сам двач не прислал текст, к коду добавляется подпись из
  `errorCodeMessage`. После успешной отправки тред перезагружается без спиннера.

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
- Desktop tests: `./gradlew :shared:desktopAppTest` — в них есть офлайн проверка черновика поста по
  настройкам доски ([PostingValidationTest](shared/src/desktopAppTest/kotlin/com/github/tor1ant/govnach/PostingValidationTest.kt)).
- Прогон по реальному апи двача (обращается в сеть, по умолчанию пропускается):
  `GOVNACH_API_TEST=1 ./gradlew :shared:desktopAppTest`
- Живая проверка эмодзи-капчи (id → картинка → клики, без отправки поста):
  `GOVNACH_CAPTCHA_TEST=1 ./gradlew :shared:desktopAppTest --tests "*EmojiCaptchaSmokeTest*"`
  ([EmojiCaptchaSmokeTest](shared/src/desktopAppTest/kotlin/com/github/tor1ant/govnach/EmojiCaptchaSmokeTest.kt)).

Если в терминале нет `JAVA_HOME`, укажите его в JDK 21, например
`JAVA_HOME=C:\Users\<user>\.jdks\corretto-21` перед вызовом `gradlew`.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…