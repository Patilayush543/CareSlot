FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY src ./src
RUN mkdir -p out && javac -d out $(find src -name "*.java")

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/out ./out
CMD ["java", "-cp", "out", "app.scheduler.Main"]
