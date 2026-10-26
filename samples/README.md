# Образцы страниц nstu.ru

Сохранённые страницы публичного расписания, по ним пишется и проверяется парсер. Скачаны 26.10.2026 (9-я учебная неделя, нечётная).

| Файл | Откуда |
|---|---|
| `groups.html` | https://www.nstu.ru/studies/schedule/schedule_classes — список факультетов, курсов и групп |
| `groups.txt` | все группы с готовым расписанием из `groups.html`, по одной в строке |
| `schedule_EM-63_week.html` | `.../schedule_classes/schedule?group=ЭМ-63` — текущая неделя |
| `schedule_EM-63_semester.html` | `.../schedule_classes/schedule?group=ЭМ-63&print=true` — весь семестр с пометками недель |

## Устройство страниц

**Адрес.** `https://www.nstu.ru/studies/schedule/schedule_classes/schedule?group=<группа>`.
Имя группы передаётся в UTF-8 (`ЭМ-63` → `%D0%AD%D0%9C-63`); в другой кодировке сайт молча отдаёт пустую страницу. Есть группы с латиницей (`EM-41`).
Параметры: `&week=N` — конкретная неделя (1–18), `&print=true` — весь семестр одной таблицей.

**Неделя.** В шапке: `<span class="schedule__title-label">9 учебная неделя</span>` и сегодняшняя дата. В версии `week` у активной недели класс `schedule__weeks-item is-active`, номер в `data-week`. Чётность = чётность номера недели.

**Таблица.** `schedule__table-body` → строка дня (`schedule__table-day`: `пн`, дата `schedule__table-date` только в недельной версии, `data-today="true"` у сегодняшнего) → строки времени (`schedule__table-time`: `08:30-10:00`) → одна или несколько строк занятий (`data-type="item"`).

**Занятие** (`schedule__table-item`):
- текст до `&middot;` — название; подгруппа дописана как `; 1 п.гр.`;
- ссылка `<a href="https://ciu.nstu.ru/kaf/persons/...">` — преподаватель (может отсутствовать);
- `schedule__table-typework` — тип: `Лекция`, `Практика`, `Лабораторная` (может быть пустым);
- `schedule__table-class` — аудитория (`1-402`, `спорткомплекс`);
- пустой слот — текст `&nbsp`.

**Только в `print=true`:** `schedule__table-label` с текстом `по чётным`, `по нечётным` или `недели 1 3 5 7 9`; у подходящих текущей неделе — `data-week="current"`.

**Список групп.** `groups.html`: факультет (`schedule__faculty`, `data-id`) → форма обучения (`schedule__faculty-type__title`) → курс (`schedule__faculty-course__title`) → группы `<a class="schedule__faculty-groups__item">`. Группы без расписания — `<span>` с классом `schedule__faculty-groups__item-notready`.
