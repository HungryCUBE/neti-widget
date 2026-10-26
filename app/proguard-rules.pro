# Код приложения оставляем целиком: он небольшой, а так надёжнее
# (сериализация, ресиверы, воркеры). Ужимаются только библиотеки.
-keep class io.github.hungrycube.lessonwidget.** { *; }

# jsoup: необязательные зависимости, которых нет в приложении.
-dontwarn com.google.re2j.**
-dontwarn org.jspecify.annotations.**
-dontwarn javax.annotation.**
