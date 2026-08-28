FROM eclipse-temurin:21 AS build

WORKDIR /app

COPY . .

RUN chmod +x kotlin

RUN ./kotlin package

FROM eclipse-temurin:21

WORKDIR /app

COPY --from=build /app/build/tasks/_app_executableJarJvm/app-jvm-executable.jar EasyPlug.jar

CMD ["java", "-jar", "EasyPlug.jar"]
