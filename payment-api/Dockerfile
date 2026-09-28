# Build context proje kök dizini olmalı: docker build -f payment-api/Dockerfile .
FROM amazoncorretto:21-alpine AS builder

# Apple M4'te JDK 21 SVE nedeniyle SIGILL veriyor, x86'da bu flag görmezden gelinir
ENV JAVA_TOOL_OPTIONS="-XX:+IgnoreUnrecognizedVMOptions -XX:UseSVE=0"
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
COPY common/pom.xml common/
COPY messaging/pom.xml messaging/
COPY payment-api/pom.xml payment-api/
COPY routing-service/pom.xml routing-service/
COPY bank-adapter/pom.xml bank-adapter/
COPY bank-simulator/pom.xml bank-simulator/
# Sadece bağımlılıkları indir (kaynak kodu henüz kopyalanmadığı için cache'lenecek)
RUN ./mvnw -pl payment-api -am dependency:go-offline -B
COPY common/src common/src
COPY messaging/src messaging/src
COPY payment-api/src payment-api/src

RUN ./mvnw -pl payment-api -am clean package -DskipTests -B && \
    cd payment-api && java -Djarmode=layertools -jar target/payment-api-*.jar extract

FROM amazoncorretto:21-alpine

RUN addgroup -S psgroup && adduser -S psadmin -G psgroup
USER psadmin

WORKDIR /app
COPY --from=builder /app/payment-api/spring-boot-loader/ ./
COPY --from=builder /app/payment-api/dependencies/ ./
COPY --from=builder /app/payment-api/snapshot-dependencies/ ./
COPY --from=builder /app/payment-api/application/ ./

EXPOSE 8081
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
