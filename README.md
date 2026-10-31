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

В треде композер скрыт, чтобы не закрывать ленту: он выезжает по кнопке «ответить в тред» в шапке
или по клику на номер поста (тогда сразу с фокусом и кросспостом), а сворачивается крестиком
и системным «назад». Набранный текст при сворачивании не теряется, а «назад» сначала закрывает
композер и только потом выходит из треда.

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

### Доски «для взрослых»

Доски категории «Взрослым» (`/sex/`, `/fag/`, `/fur/` и остальные из этой категории) в апи
ничем не отличаются от обычных: `enable_posting=true`, те же `max_comment` и `max_files_size`.
Признак ровно один — `category` в `/api/mobile/v2/boards`, его проверяет `Board.isAdult()`
([AdultContent.kt](shared/src/commonMain/kotlin/com/github/tor1ant/govnach/client/AdultContent.kt)).

- Гейт — подтверждение возраста «Мне есть 18»: до него взрослые доски не показываются в списке
  (вместо них — плашка «Скрыто досок 18+: N» с кнопкой «Показать»), категория «Взрослым» отсутствует
  в фильтре, треды по ним не открываются и композер выключен. После подтверждения чтение и постинг —
  как на любой другой доске; на главной есть кнопка «Скрыть», чтобы вернуть запрет.
- Состояние — `AdultAccess` в [HomeViewModel](shared/src/commonMain/kotlin/com/github/tor1ant/govnach/ui/model/HomeViewModel.kt)
  (singleton в Koin), поэтому разрешения одного экрана хватает всем остальным. Живёт до перезапуска
  приложения: тему настроек не трогаем, второй шаг «Мне есть 18» делается один раз.
- UI гейта: [AdultGate.kt](shared/src/commonMain/kotlin/com/github/tor1ant/govnach/ui/AdultGate.kt)
  (`AdultGateDialog`, `AdultBoardsNotice`, `AdultLockedState`). Экраны доски, создания треда и треда
  получают `adultAllowed` и `onAdultConfirmed` из [App](shared/src/commonMain/kotlin/com/github/tor1ant/govnach/App.kt).
  Минус подхода: категорию в апи могут переименовать — тогда `/sex/` перестанет считаться
  взрослой, и гейт надо обновить вместе с [ADULT_CATEGORY](shared/src/commonMain/kotlin/com/github/tor1ant/govnach/client/AdultContent.kt).
- Размытие медиа не зависит от гейта: `FileModel.nsfw` двач заполняет не везде — на `/fag/`
  в живом прогоне его не было ни у одного файла, — поэтому в треде взрослой доски превью
  стартуют размытыми и открываются тапом (`isAdultContent(nsfw, adultBoard)`).

### Логотип и иконки

Логотип — оранжевая стрелка двача, вектор без фона. Геометрия знака описана один раз
(вершины стрелки в координатах официального логотипа из шапки [2ch.hk](https://2ch.hk)) —
в [tools/gen_logo.py](tools/gen_logo.py); из неё собираются и иконки лаунчера/ярлыка, и знак
на экране запуска, чтобы проекции не разошлись.

- Векторы приложения — в `shared/src/commonMain/composeResources/drawable`:
  - [twoch_logo.xml](shared/src/commonMain/composeResources/drawable/twoch_logo.xml) — знак «2CH» целиком
    (градиентная стрелка и буквы), используется на экране запуска и на главной;
  - [twoch_emblem.xml](shared/src/commonMain/composeResources/drawable/twoch_emblem.xml) — только стрелка,
    отцентрована в безопасной зоне 108dp: из неё сделана иконка лаунчера Android.
  Рисует их [AppLogo.kt](shared/src/commonMain/kotlin/com/github/tor1ant/govnach/ui/AppLogo.kt).
- Генераторы (детерминированные, повторный запуск не меняет файлы):
  `python tools/gen_logo.py` — векторы приложения и `ic_launcher_foreground.xml`;
  `python tools/gen_android_icons.py` — mipmap-PNG для API < 26;
  `python tools/gen_desktop_icons.py` — иконки окна и ярлыка. Всем трём для растеризации
  нужен `resvg-py` (путь передаётся в `RESVG_PATH`).
- Android: адаптивная иконка в [mipmap-anydpi-v26](androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml) —
  только foreground без подложки плюс `monochrome` для themed icons 13+. На API < 26 лаунчер берёт
  `mipmap-*/ic_launcher.png` — их рисует `gen_android_icons.py` в тех же пропорциях.
- Desktop, две разные иконки, и это осознанно:
  - **окно, заголовок и панель задач** — [desktopApp/icons/govnach-window.ico](desktopApp/icons/govnach-window.ico)
    и кадры `window-<N>.png` (16–64px): стрелка без подложки, каждый размер нарисован отдельно.
    [main.kt](desktopApp/src/main/kotlin/com/github/tor1ant/govnach/main.kt) отдаёт их AWT как
    `MultiResolutionImage`. Иначе быть не может: Windows просит иконку заголовка размером
    `SM_CXSMICON` (16px, 24/32 при масштабе 150–200%), а `sun.awt.SunToolkit.getScaledIconImage`
    при выборе кадра округляет масштаб до 1/N и рисует билинейно без сохранения пропорций — одна
    крупная картинка доезжает до 16px мутным пятном. С кадрами «один-к-одному» масштабирования нет.
  - **ярлык, «Пуск», MSI, док macOS** — [govnach.ico](desktopApp/icons/govnach.ico),
    [govnach-setup.ico](desktopApp/icons/govnach-setup.ico), [govnach.icns](desktopApp/icons/govnach.icns):
    тот же знак на тёмной скруглённой плашке. Это про читаемость, а не декор: у стрелки перед
    подложкой серебристый блик, и на светлом фоне ярлыка она почти не видна (контраст по формуле
    WCAG был 1.5:1). На `#1A1A1A` он 6:1, и та же плашка совпадает с тёмной палитрой приложения.
- Заголовок окна — `Govnach`; имя задаётся константой `APP_NAME` в `main.kt`.
- Экран запуска: пока список досок грузится впервые, вместо пустого экрана показывается
  логотип со спиннером ([StartupScreen.kt](shared/src/commonMain/kotlin/com/github/tor1ant/govnach/ui/StartupScreen.kt)).
  Повторные загрузки (кнопка «повторить», перезаход на главную) заставку не открывают.
- `2ch.png` в корне — исходная растровая картинка (400x300 с белым фоном), в приложение не попадает:
  для вектора используется SVG.

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
- Живая проверка взрослых досок — список, треды, посты и выдача emoji капчи, без постинга:
  `GOVNACH_API_TEST=1 ./gradlew :shared:desktopAppTest --tests "*AdultBoardsApiTest*"`
  ([AdultBoardsApiTest](shared/src/desktopAppTest/kotlin/com/github/tor1ant/govnach/AdultBoardsApiTest.kt)).
  Офлайн-проверка признака доски и размытия — [AdultBoardsTest](shared/src/desktopAppTest/kotlin/com/github/tor1ant/govnach/AdultBoardsTest.kt).
- Живая проверка эмодзи-капчи (id → картинка → клики, без отправки поста):
  `GOVNACH_CAPTCHA_TEST=1 ./gradlew :shared:desktopAppTest --tests "*EmojiCaptchaSmokeTest*"`
  ([EmojiCaptchaSmokeTest](shared/src/desktopAppTest/kotlin/com/github/tor1ant/govnach/EmojiCaptchaSmokeTest.kt)).

Если в терминале нет `JAVA_HOME`, укажите его в JDK 21, например
`JAVA_HOME=C:\Users\<user>\.jdks\corretto-21` перед вызовом `gradlew`.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…