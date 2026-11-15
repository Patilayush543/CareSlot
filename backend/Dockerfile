FROM eclipse-temurin:8-jdk

WORKDIR /app

COPY src/ ./src/

RUN mkdir out && javac -d out $(find src -name "*.java")

EXPOSE 8080

CMD ["java", "-cp", "out", "app.scheduler.Main"]
