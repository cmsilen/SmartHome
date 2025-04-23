FROM maven:3.9.4-eclipse-temurin-17

# Set working directory
WORKDIR /app

# Copy the whole project
COPY . .

# Expose default Spring Boot port
EXPOSE 8080

# Run the app using Maven
CMD ["mvn", "spring-boot:run"]