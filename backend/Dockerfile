# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-jammy as builder
WORKDIR /app

# Copy maven wrapper and pom first to leverage layer caching
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the built jar from the builder stage
# Matches backend-0.0.1-SNAPSHOT.jar but avoids original-*.jar
COPY --from=builder /app/target/backend-*.jar app.jar

# Expose the port the app runs on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
