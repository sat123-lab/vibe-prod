FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN chmod +x mvnw

RUN ./mvnw clean package -DskipTests

# Persistent upload root — mount a Render disk at this path in production.
RUN mkdir -p /var/social/uploads
ENV UPLOAD_DIR=/var/social/uploads

EXPOSE 8080

CMD ["java","-jar","target/demo-0.0.1-SNAPSHOT.jar"]
