# Build context proje kök dizini olmalı: docker build -f bank-simulator/Dockerfile .
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
RUN ./mvnw -pl bank-simulator -am dependency:go-offline -B
COPY common/src common/src
COPY messaging/src messaging/src
COPY bank-simulator/src bank-simulator/src

RUN ./mvnw -pl bank-simulator -am clean package -DskipTests -B && \
    cd bank-simulator && java -Djarmode=layertools -jar target/bank-simulator-*.jar extract

FROM amazoncorretto:21-alpine

RUN addgroup -S psgroup && adduser -S psadmin -G psgroup
USER psadmin

WORKDIR /app
COPY --from=builder /app/bank-simulator/spring-boot-loader/ ./
COPY --from=builder /app/bank-simulator/dependencies/ ./
COPY --from=builder /app/bank-simulator/snapshot-dependencies/ ./
COPY --from=builder /app/bank-simulator/application/ ./

EXPOSE 8090
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
