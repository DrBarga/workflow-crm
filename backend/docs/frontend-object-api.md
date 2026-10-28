# API для формы и карточки объекта

Контракт дополнительных справочников и списков: [OpenAPI](openapi/frontend-object-api.json).
Создание, карточка и PATCH: [Objects OpenAPI](openapi/objects.yaml).
Все UUID берутся из ответов API текущего окружения.

## Сессия и ошибки

Запросы идут на тот же backend, что обслуживает /api/objects, с cookie действующей сессии.
Для fetch используется credentials: 'include'. Для POST/PATCH/DELETE сначала получить
GET /api/auth/csrf, сохранить cookie XSRF-TOKEN и передать token из JSON в X-XSRF-TOKEN.
Секреты геопровайдера браузеру не передаются.

401 означает отсутствие действующей сессии, 403 — отсутствие права, 404 — неизвестный
объект, 409 — удаление объекта уже началось. Некорректные значения поисковых
параметров/координат дают 422; неверный UUID или отсутствующий обязательный параметр — 400.
Все новые ответы запрещают HTTP-кеширование.

## Справочники

- GET /api/file-categories — массив {id, code, name, isRestricted}. Только активные
  категории; SUPERADMIN не получает закрытую категорию договоров. Право на загрузку
  и доступ к конкретному объекту всё равно проверяет файловый API.
- GET /api/regions — массив {id, code, name}. MANAGER и REGIONAL_MANAGER получают свои
  назначения, остальные роли — все настроенные регионы. У таблицы regions нет признака
  активности: это существующие регионы CRM, без придуманных записей и геометрии.
- GET /api/data-sources и GET /api/contract-types — активные {id, code, name}.
  Источник передаётся как dataSourceId, тип договора — как contractTypeId.

Для списка менеджеров предпочтителен GET /api/managers?regionId=UUID&page=0&size=20.
Возвращает {content, page, size, totalElements, totalPages}, элементы:
{id, fullName, role: "MANAGER", regionIds: [запрошенный регион]}.
Только активные менеджеры; REGIONAL_MANAGER выбирает в своих регионах,
DEVELOPMENT_DIRECTOR и SUPERADMIN — по сети. Email, состояние безопасности и другие
назначения пользователя в этой проекции отсутствуют.

Совместимость: GET /api/users?role=MANAGER&regionId=UUID&page=0&size=20 также доступен
REGIONAL_MANAGER и DEVELOPMENT_DIRECTOR с той же минимальной проекцией.
SUPERADMIN на /api/users сохраняет административный ответ UserView. Другие фильтры,
карточки, создание и редактирование пользователей остаются административными.
Чтобы получить одинаковый ответ для всех трёх ролей, использовать /api/managers.

## Адрес и регион до сохранения

GET /api/geo/search?q=Київ%20Хрещатик&limit=6 возвращает массив
{label, latitude, longitude}. q: 3–200 символов; limit: 1–6. Координаты числовые,
до семи знаков после запятой. Пустой массив означает отсутствие подходящих результатов.

Поиск использует настроенный Nominatim-совместимый сервис: украинский язык, countrycodes=ua,
не более шести подсказок. Положительные и пустые результаты кешируются в Redis на семь
дней; ключ включает провайдера и нормализованный запрос. Search и reverse разделяют
общий лимит в один внешний запрос в секунду. Кешированные запросы лимит не расходуют.
429 REQUEST_RATE_LIMITED — повторить позже; 503 REQUEST_REJECTED — провайдер не настроен/недоступен
или ответ некорректен. Ошибка не подменяется пустым успешным результатом.

Для live нужны GEOCODING_SEARCH_URL (полный URL search разрешённого провайдера),
GEOCODING_URL (полный URL reverse) и GEOCODING_USER_AGENT.
Публичный nominatim.openstreetmap.org запрещён для подсказок ввода согласно
[политике провайдера](https://operations.osmfoundation.org/policies/nominatim/);
нужен собственный сервер либо провайдер, разрешающий такой сценарий.
В mock режиме поиск возвращает пустой массив, reverse — явно тестовый адрес.
Это режим разработки, он не подтверждает доступность реального геопоиска.

POST /api/geo/reverse:

    {"longitude":30.5234,"latitude":50.4501}

Ответ:

    {"address":"...","regionId":null,"requiresRegionSelection":true,
     "latitude":50.4501,"longitude":30.5234}

Объект не создаётся. regionId определяется только по загруженным границам CRM,
а не по тексту области из геокодера. При отсутствии/неоднозначности границ либо
регионе вне назначений MANAGER/REGIONAL_MANAGER возвращаются null и
requiresRegionSelection=true. Показать ручной выбор из /api/regions.
При однозначном доступном совпадении regionId заполнен, признак false.
Вне Украины и при неизвестных полях тела — 422.

Подсказка провайдера не меняет правила создания: POST /api/objects повторно проверяет
регион, координаты и право назначить менеджера.

## Контакты и упоминания

GET /api/contacts?q=Власник&page=0&size=20 — доступные контакты.
Поиск по имени и фрагменту телефона от трёх цифр. %, _ и ! ищутся как обычные символы.
GET /api/objects/UUID/contacts?q=...&page=0&size=20 — только связанные контакты
доступного объекта.

Ответ обоих: {items, page, size, totalElements, hasMore}. Элемент:
{id, name, role, phone, email, contactTypeId, version}.
phone: "***" либо null. Раскрытие телефона остаётся отдельным действием с аудитом.
Для региональных ролей список ограничен прежними правилами контактов; неподвязанный
контакт доступен автору с правом записи. CONTRACT_MANAGER видит контакты объектов
в CONTRACT_APPROVAL/CONTRACT_SIGNED. Director/CEO/Admin — по сети.

GET /api/objects/UUID/mentionable-users?q=...&page=0&size=20 возвращает такой же
конверт с элементами {id, fullName, role}. Список доступен тем, кто может написать
комментарий к объекту. Кандидаты активны и проходят ту же проверку чтения объекта,
что и recipients при отправке комментария. Список не раскрывает email и телефон.
Выбранный id передаётся в mentions[].userId существующего POST comments; offset/length
по-прежнему относятся к тексту комментария.

Для всех трёх списков page начинается с 0, size: 1–100. Фильтрация выполняется до
пагинации; totalElements относится только к доступным результатам.

## Сохранённые задачи

GET /api/objects/UUID/tasks?taskSetId=UUID&page=0&size=50

taskSetId необязателен. Ответ:
{items, taskSets, page, size, totalElements, hasMore}. taskSets содержит метаданные
наборов, представленных на текущей странице: {id, triggerStatus, state, createdAt}.
items содержит сохранённые задачи, включая ручные и завершённые:

    {"id":"UUID","name":"...","assignee":{"id":"UUID","fullName":"..."},
     "deadline":"2026-11-01","status":"NEW","statusName":"Не розпочата",
     "taskSetId":null,"needsReview":false,"manuallyPaused":false,"version":0,
     "predecessors":[{"id":"UUID","name":"...","status":"IN_PROGRESS"}]}

NEW — «Не розпочата» (в том числе ожидание зависимостей), PAUSED — пауза; вручную поставленную паузу отличает
manuallyPaused. Список не является превью и не создаёт задачи.
Менеджер видит свои объекты в назначенных регионах, Провідний — свои регионы,
Director/CEO/Admin и CONTRACT_MANAGER — по сети. Это право чтения не даёт права
изменить задачу. Чужой/неизвестный taskSetId не раскрывает другой объект, результат пустой.

## Поля объекта

shortComment уже поддерживается POST/PATCH /api/objects: строка до 200 символов либо null.
В полной карточке и полной строке реестра поле находится в корне; ограниченные
проекции его не отдают. Редактирование определяется editableFields.

openingPlannedDate вычисляется из constructionAcceptancePlannedDate +
commercialTerms.constructionTermDays в календарных днях. Если одного значения нет,
результат null. В полной карточке: workflow.milestones.openingPlannedDate;
в проекции CONTRACT_MANAGER: openingPlannedDate в корне.
Дата только для чтения: не передавать её в POST/PATCH. Изменение плановой даты БМР
или срока БМР пересчитывает результат; прямой PATCH защищённого поля даёт 422.
При обновлении commercialTerms учитывать существующий контракт замены коммерческого
блока, не отправлять сокращённый блок с потерей остальных значений.
